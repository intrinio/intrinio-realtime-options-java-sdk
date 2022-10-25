package intrinio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.Gson;

class WebSocketState {
	
	private WebSocket ws;
	private boolean isReady = false;
	private boolean isReconnecting = false;
	private LocalDateTime lastReset = LocalDateTime.now();
	
	WebSocketState() {}

	WebSocket getWebSocket() {
		return ws;
	}

	void setWebSocket(WebSocket ws) {
		this.ws = ws;
	}

	boolean isReady() {
		return isReady;
	}

	void setReady(boolean isReady) {
		this.isReady = isReady;
	}

	boolean isReconnecting() {
		return isReconnecting;
	}

	void setReconnecting(boolean isReconnecting) {
		this.isReconnecting = isReconnecting;
	}

	LocalDateTime getLastReset() {
		return lastReset;
	}

	void reset() {
		this.lastReset = LocalDateTime.now();
	}
}

record Token (String token, LocalDateTime date) {}

public class Client implements WebSocket.Listener {
	private final String heartbeatMessage = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":null}";
	private final String heartbeatResponse = "{\"topic\":\"phoenix\",\"ref\":null,\"payload\":{\"status\":\"ok\",\"response\":{}},\"event\":\"phx_reply\"}";
	private final long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private final ReentrantReadWriteLock tLock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private Config config;
	private final int tradeMessageSize = 59;
	private final int quoteMessageSize = 46;
	private final int refreshMessageSize = 42;
	private final int unusualActivityMessageSize = 60;
	private final LinkedBlockingDeque<byte[]> data = new LinkedBlockingDeque<>();
	private AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private WebSocketState wsState = null;
	private AtomicLong dataMsgCount = new AtomicLong(0l);
	private AtomicLong textMsgCount = new AtomicLong(0l);
	private HashSet<String> channels = new HashSet<String>();
	private LinkedBlockingDeque<Tuple<byte[], Boolean>> dataBucket;
	private OnTrade onTrade = (Trade trade) -> {};
	private boolean useOnTrade = false;
	private OnQuote onQuote = (Quote quote) -> {};
	private boolean useOnQuote = false;
	private OnRefresh onRefresh = (Refresh r) -> {};
	private boolean useOnRefresh = false;
	private OnUnusualActivity onUnusualActivity = (UnusualActivity ua) -> {};
	private boolean useOnUnusualActivity = false;
	private Thread[] threads;
	private Lock dataBucketLock;
	private boolean isCancellationRequested = false;
	private boolean isStarted = false;
	
	private class Tuple<X, Y> { 
		  public final X x; 
		  public final Y y; 
		  public Tuple(X x, Y y) { 
		    this.x = x; 
		    this.y = y; 
		  } 
	}
	
	private Thread heartbeatThread = new Thread(() -> {
		while (!this.isCancellationRequested) {
			try {
				Thread.sleep(20000);
				wsLock.readLock().lock();
				try {
					if (wsState.isReady()) {
						wsState.getWebSocket().sendText(heartbeatMessage, true);
					}
				} finally {
					wsLock.readLock().unlock();
				}
				
			} catch (InterruptedException e) {}
		}
	});
	
	private byte[] getCompleteData() {
		Queue<byte[]> parts = new LinkedList<>();
		int length = 0;
		boolean done = false;
		dataBucketLock.lock();
		try {			
			while (!done) {
				try {				
					Tuple<byte[], Boolean> datum = dataBucket.poll(1, TimeUnit.SECONDS);
					if (datum != null) {
						parts.add(datum.x);
						done = datum.y;
						length += datum.x.length;
					}
				} catch(InterruptedException e) {
					Client.Log("process data interrupted");
				}
			}			
		} finally {dataBucketLock.unlock();}
		
		//reassemble into one byte array
		byte[] bytes = new byte[length];
		int index = 0;
		while (!parts.isEmpty()) {
			byte[] part = parts.remove();
			java.lang.System.arraycopy(part, 0, bytes, index, part.length);
			index += part.length;
		}
		return bytes;
	}
	
	private Runnable processData = () -> {
		while (!this.isCancellationRequested) {
			try {
				byte[] datum = data.poll(1, TimeUnit.SECONDS);
				if (datum != null) {
					int count = datum[0];
					int offset = 1;
					ByteBuffer buffer = ByteBuffer.wrap(datum);
					buffer.position(0);
					buffer.limit(datum.length);
					for (long i = 0L; i < count; i++) {
						buffer.position(0);
						byte type = datum[offset + 20];
						ByteBuffer offsetBuffer;
						if (type == 1) {
							offsetBuffer = buffer.slice(offset, quoteMessageSize);
							Quote quote = Quote.parse(offsetBuffer);
							offset += quoteMessageSize;
							if (useOnQuote) onQuote.onQuote(quote);
						}
						else if (type == 0) {
							offsetBuffer = buffer.slice(offset, tradeMessageSize);
							Trade trade = Trade.parse(offsetBuffer);
							offset += tradeMessageSize;
							if (useOnTrade) onTrade.onTrade(trade);
						}
						else if (type > 2) {
							offsetBuffer = buffer.slice(offset, unusualActivityMessageSize);
							UnusualActivity ua = UnusualActivity.parse(offsetBuffer);
							offset += unusualActivityMessageSize;
							if (useOnUnusualActivity) onUnusualActivity.onUnusualActivity(ua);
						}
						else if (type == 2) {
							offsetBuffer = buffer.slice(offset, refreshMessageSize);
							Refresh r = Refresh.parse(offsetBuffer);
							offset += refreshMessageSize;
							if (useOnRefresh) onRefresh.onRefresh(r);
						}
						else {
							Client.Log("Error parsing multi-part message. Type is %d", type);							
							i = count;
						}
					}
				} 
			} catch (Exception ex) 
			{
				Client.Log("General Exception");
			}			
		}
	};
	
	private void initializeThreads() throws Exception {
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(processData);
		}
	}
	
	private boolean allReady() {
		wsLock.readLock().lock();
		try {
			return (wsState == null) ? false : wsState.isReady();
		} finally {
			wsLock.readLock().unlock();
		}
	}
	
	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getProvider()) {
		case OPRA:
		case OPRA_FIREHOSE: authUrl = "https://realtime-options.intrinio.com/auth?api_key=" + config.getApiKey();
			break;
		case MANUAL:
		case MANUAL_FIREHOSE: authUrl = "http://" + config.getIpAddress() + "/auth?api_key=" + config.getApiKey();
			break;
		default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}
	
	private String getWebSocketUrl (String token) throws Exception {
		String wsUrl;
		switch (config.getProvider()) {
		case OPRA:
		case OPRA_FIREHOSE: wsUrl = "wss://realtime-options.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case MANUAL:
		case MANUAL_FIREHOSE: wsUrl = "ws://" + config.getIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
	}

	private void doBackoff(BooleanSupplier callback) {
		int i = 0;
		long backoff = this.selfHealBackoffs[i];
		boolean success = callback.getAsBoolean();
		while (!success) {
			try {
				Thread.sleep(backoff);
				i = Math.min(i + 1, this.selfHealBackoffs.length - 1);
				backoff = this.selfHealBackoffs[i];
				success = callback.getAsBoolean();
			} catch (InterruptedException e) {}
		}
	}
	
	private BooleanSupplier trySetToken = () -> {
		Client.Log("Authorizing...");
		String authUrl = null;
		try {
			authUrl = this.getAuthUrl();
		} catch (Exception e) {
			Client.Log("Authorization Failure. " + e.getMessage());
			return false;
		}
		URL url = null;
		try {
			url = new URL(authUrl);
		} catch (MalformedURLException e) {
			Client.Log("Authorization Failure. Bad URL (%s). %s", authUrl, e.getMessage());
			return false;
		}
		HttpURLConnection con;
		try {
			con = (HttpURLConnection) url.openConnection();
			con.setRequestProperty("Client-Information", "IntrinioRealtimeOptionsJavaSDKv3.0");
		} catch (IOException e) {
			Client.Log("Authorization Failure. Please check your network connection. " + e.getMessage());
			return false;
		}
		try {
			con.setRequestMethod("GET");
			int status = con.getResponseCode();
			if (status == 200) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
				String token = reader.readLine();
				this.token.set(new Token(token, LocalDateTime.now()));
				Client.Log("Authorization successful");
				return true;
			}
			else
				Client.Log("Authorization Failure (%d). The authorization key you provided is likely incorrect.", status);
				return false;
		} catch (ProtocolException e) {
			Client.Log("Authorization Failure. Bad request type. " + e.getMessage());
			return false;
		} catch (IOException e) {
			Client.Log("Authorization Failure. The authorization server is likely offline. " + e.getMessage());
			return false;
		}
	};
		
	private String getToken() {
		tLock.readLock().lock();
		try {
			Token token = this.token.get();
			if (LocalDateTime.now().minusDays(1).compareTo(token.date()) > 0) {
				return token.token();
			} else {
				tLock.readLock().unlock();
				tLock.writeLock().lock();
				try {
					doBackoff(this.trySetToken);
					tLock.readLock().lock();
					return this.token.get().token();
				} finally {
					tLock.writeLock().unlock();
				}
			}
		} finally {
			tLock.readLock().unlock();
		}
	}
		
	private void tryReconnect() {
		BooleanSupplier reconnectFn = () -> {
			Client.Log("Websocket - Reconnecting...");
			if (wsState.isReady()) {
				return true;
			} else {
				this.wsLock.writeLock().lock();
				try {
					wsState.setReconnecting(true);
				} finally {
					this.wsLock.writeLock().unlock();
				}
				if (wsState.getLastReset().plusDays(5).compareTo(LocalDateTime.now()) >= 0) {
					String token = this.getToken();
					resetWebSocket(token);
				} else {
					resetWebSocket(this.token.get().token());
				}
				return false;
			}
		};
		this.doBackoff(reconnectFn);
	}
		
	private void onWebSocketConnected (WebSocket ws, WebSocketState wsState) {
		if (!channels.isEmpty()) {
			String message;
			for (String channel : channels) {
				StringBuilder sb = new StringBuilder();
				LinkedList<String> list = new LinkedList<String>();
				if (useOnTrade) {
					sb.append(",\"trade_data\":\"true\"");
					list.add("trade");
				}
                if (useOnQuote) {
                	sb.append(",\"quote_data\":\"true\"");
                	list.add("quote");
                }
                if (useOnRefresh) {
                	sb.append(",\"refresh_data\":\"true\"");
                	list.add("open interest");
                }
                if (useOnUnusualActivity) {
                	sb.append(",\"unusual_activity_data\":\"true\"");
                	list.add("unusual activity");
                }
                String subscriptionSelection = sb.toString();
                message = "{\"topic\":\"options:" + channel + "\",\"event\":\"phx_join\"" + subscriptionSelection + ",\"payload\":{},\"ref\":null}";
                subscriptionSelection = String.join(", ", list);
                Client.Log("Websocket - Joining channel: %s (subscriptions = %s)", channel, subscriptionSelection);
				wsState.getWebSocket().sendText(message, true);
			}
		}
	}
	
	public CompletionStage<Void> onClose(WebSocket ws, int status, String reason) {
		wsLock.readLock().lock();
		try {
			if (!wsState.isReconnecting()) {
				Client.Log("Websocket - Closed");
				wsLock.readLock().unlock();
				wsLock.writeLock().lock();
				try {
					wsState.setReady(false);
				} finally {
					wsLock.writeLock().unlock();
				}
				if (!this.isCancellationRequested) {
					new Thread(() -> {
						this.tryReconnect();
					}).start();
				}
			}
		} finally {
			wsLock.readLock().unlock();
		}
		return null;
	}
		
	public void onError(WebSocket ws, Throwable err) {
		Client.Log("Websocket - Error - %s", err.getMessage());
		ws.request(1);
	}
		
	public CompletionStage<Void> onText(WebSocket ws, CharSequence data, boolean isComplete) {
		textMsgCount.addAndGet(1l);
		if (this.heartbeatResponse.contentEquals(data)) {
			ws.request(1);
			return null;
		} else {
			Gson gson = new Gson();
			ErrorMessage errorMessage = gson.fromJson(data.toString(), ErrorMessage.class);
			Client.Log("Error received: %s", errorMessage.getPayload().getResponse());
			ws.request(1);
			return null;
		}
	}
		
	public CompletionStage<Void> onBinary(WebSocket ws, ByteBuffer data, boolean isComplete) {
		dataMsgCount.addAndGet(1);
		byte[] bytes = new byte[data.remaining()];
		data.get(bytes);
		this.dataBucket.add(new Tuple<byte[], Boolean>(bytes, isComplete));
		if (isComplete) {
			this.data.add(getCompleteData());
		}
		ws.request(1);
		return null;
	}
		
	private void resetWebSocket(String token) {
		Client.Log("Websocket - Resetting");
		String wsUrl;
		try {
			wsUrl = this.getWebSocketUrl(token);
		} catch (Exception e) {
			Client.Log("Reset Failure. " + e.getMessage());
			return;
		}
		URI uri = null;
		try {
			uri = new URI(wsUrl);
		} catch (URISyntaxException e) {
			Client.Log("Reset Failure. Bad URL (%s). %s", wsUrl, e.getMessage());
			return;
		}
		HttpClient client = HttpClient.newHttpClient();
		CompletableFuture<WebSocket> task = client.newWebSocketBuilder().buildAsync(uri, (WebSocket.Listener) this);
		try {
			WebSocket ws = task.get();
			Client.Log("Websocket - Reset");
			wsLock.writeLock().lock();
			try {
				wsState.setWebSocket(ws);
				wsState.reset();
				wsState.setReady(true);
				wsState.setReconnecting(false);
			} finally {
				wsLock.writeLock().unlock();
			}
			this.onWebSocketConnected(ws, wsState);
		} catch (ExecutionException e) {
			Client.Log("Reset Failure. Could not establish connection. %s", e.getMessage());
			return;
		} catch (InterruptedException e) {
			Client.Log("Reset Failure. Thread interrupted. %s", e.getMessage());
			return;
		}
	}
		
	private void initializeWebSockets(String token) {
		wsLock.writeLock().lock();
		try {
			Client.Log("Websocket - Connecting...");
			WebSocketState websocketState = new WebSocketState();
			String wsUrl;
			try {
				wsUrl = this.getWebSocketUrl(token);
			} catch (Exception e) {
				Client.Log("Initialization Failure. " + e.getMessage());
				return;
			}
			URI uri = null;
			try {
				uri = new URI(wsUrl);
			} catch (URISyntaxException e) {
				Client.Log("Initialization Failure. Bad URL (%s). %s", wsUrl, e.getMessage());
				return;
			}
			HttpClient client = HttpClient.newHttpClient();
			CompletableFuture<WebSocket> task = client.newWebSocketBuilder().buildAsync(uri, (WebSocket.Listener) this);
			try {
				WebSocket ws = task.get();
				Client.Log("Websocket - Connected");
				websocketState.setWebSocket(ws);
				this.wsState = websocketState;
				websocketState.setReady(true);
				websocketState.setReconnecting(false);
				if (!heartbeatThread.isAlive()) {
					heartbeatThread.start();
				}
				for (Thread thread : threads) {
					if (!thread.isAlive()) {
						thread.start();
					}
				}
				this.onWebSocketConnected(ws, websocketState);
			} catch (ExecutionException e) {
				Client.Log("Initialization Failure. Could not establish connection. %s", e.getMessage());
				return;
			} catch (InterruptedException e) {
				Client.Log("Initialization Failure. Thread interrupted. %s", e.getMessage());
				return;
			}
		} finally {
			wsLock.writeLock().unlock();
		}
	}
		
	private void _join(String symbol) {
		if (((symbol == "lobby") || (symbol == "lobby_trades_only")) &&
				((config.getProvider() != Provider.MANUAL_FIREHOSE) && (config.getProvider() != Provider.OPRA_FIREHOSE))) {
			Client.Log("Only 'FIREHOSE' providers may join the lobby channel");
		} else if (((symbol != "lobby") && (symbol != "lobby_trades_only")) &&
	            ((config.getProvider() == Provider.MANUAL_FIREHOSE) || (config.getProvider() == Provider.OPRA_FIREHOSE))) {
			Client.Log("'FIREHOSE' providers may only join the lobby channel");
		} else {
			if (channels.add(symbol)) {
				StringBuilder sb = new StringBuilder();
				LinkedList<String> list = new LinkedList<String>();
				if (useOnTrade) {
					sb.append(",\"trade_data\":\"true\"");
					list.add("trade");
				}
	            if (useOnQuote) {
	            	sb.append(",\"quote_data\":\"true\"");
	            	list.add("quote");
	            }
	            if (useOnRefresh) {
	            	sb.append(",\"refresh_data\":\"true\"");
	            	list.add("open interest");
	            }
	            if (useOnUnusualActivity) {
	            	sb.append(",\"unusual_activity_data\":\"true\"");
	            	list.add("unusual activity");
	            }
	            String subscriptionSelection = sb.toString();
	            String message = "{\"topic\":\"options:" + symbol + "\",\"event\":\"phx_join\"" + subscriptionSelection + ",\"payload\":{},\"ref\":null}";
				subscriptionSelection = String.join(", ", list);
				Client.Log("Websocket - Joining channel: %s (subscriptions = %s)", symbol, subscriptionSelection);
				wsState.getWebSocket().sendText(message, true);
			}
		}
	}
		
	private void _leave(String symbol) {
		if (channels.remove(symbol)) {
			String message = "{\"topic\":\"options:" + symbol + "\",\"event\":\"phx_leave\",\"payload\":{},\"ref\":null}";
			Client.Log("Websocket - Leaving channel: %s", symbol);
			wsState.getWebSocket().sendText(message, true);
		}
	}
		
	protected void finalize() {
		if (!this.isCancellationRequested) {
			this.stop();
		}
	}
		
	public Client() {
		try {
			this.config = Config.load();
			threads = new Thread[config.getNumThreads()];
			dataBucketLock = new ReentrantLock();
			dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
			this.initializeThreads();
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
	}
	
	public Client(Config config) {
		try {
			this.config = config;
			threads = new Thread[config.getNumThreads()];
			dataBucketLock = new ReentrantLock();
			dataBucket = new LinkedBlockingDeque<Tuple<byte[], Boolean>>();
			this.initializeThreads();
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
	}
	
	public void setOnTrade(OnTrade onTrade) throws Exception {
		if (this.isStarted) {
			throw new Exception("You must set all callbacks prior to calling 'start'");
		} else if (this.useOnTrade) {
			throw new Exception("'OnTrade' callback has already been set");
		} else {
			this.onTrade = onTrade;
			this.useOnTrade = true;
		}
	}
	
	public void setOnQuote(OnQuote onQuote) throws Exception {
		if (this.isStarted) {
			throw new Exception("You must set all callbacks prior to calling 'start'");
		} else if (this.useOnQuote) {
			throw new Exception("'OnQuote' callback has already been set");
		} else {
			this.onQuote = onQuote;
			this.useOnQuote = true;
		}
	}
	
	public void setOnRefresh(OnRefresh onRefresh) throws Exception {
		if (this.isStarted) {
			throw new Exception("You must set all callbacks prior to calling 'start'");
		} else if (this.useOnRefresh) {
			throw new Exception("'OnRefresh' callback has already been set");
		} else {
			this.onRefresh = onRefresh;
			this.useOnRefresh = true;
		}
	}
	
	public void setOnUnusualActivity(OnUnusualActivity onUnusualActivity) throws Exception {
		if (this.isStarted) {
			throw new Exception("You must set all callbacks prior to calling 'start'");
		} else if (this.useOnUnusualActivity) {
			throw new Exception("'OnUnusualActivity' callback has already been set");
		} else {
			this.onUnusualActivity = onUnusualActivity;
			this.useOnUnusualActivity = true;
		}
	}

	public void join() {
		if ((config.getProvider() == Provider.MANUAL_FIREHOSE) || (config.getProvider() == Provider.OPRA_FIREHOSE)) {
			Client.Log("'FIREHOSE' providers must join the lobby channel. Use the function 'joinLobby' instead.");
		} else {
			while (!this.allReady()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
			String[] symbols = config.getSymbols();
			for (String symbol : symbols) {
				if (!this.channels.contains(symbol)) {
					this._join(symbol);
				}
			}
		}
	}
		
	public void join(String symbol) {
		if ((config.getProvider() == Provider.MANUAL_FIREHOSE) || (config.getProvider() == Provider.OPRA_FIREHOSE)) {
			Client.Log("'FIREHOSE' providers must join the lobby channel. Use the function 'joinLobby' instead.");
		} else {
			if (!symbol.isBlank()) {
				while (!this.allReady()) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {}
				}
				if (!channels.contains(symbol)) this._join(symbol);
			}
		}
	}
		
	public void join(String[] symbols) {
		if ((config.getProvider() == Provider.MANUAL_FIREHOSE) || (config.getProvider() == Provider.OPRA_FIREHOSE)) {
			Client.Log("'FIREHOSE' providers must join the lobby channel. Use the function 'joinLobby' instead.");
		} else {
			while (!this.allReady()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
			for (String symbol : symbols) {
				if (!channels.contains(symbol)) this._join(symbol);
			}
		}
	}
	
	public void joinLobby() {
		if ((config.getProvider() != Provider.MANUAL_FIREHOSE) && (config.getProvider() != Provider.OPRA_FIREHOSE)) {
			Client.Log("Only 'FIREHOSE' providers may join the lobby channel");
		} else if (channels.contains("lobby")) {
			Client.Log("This client has already joined the lobby channel");
		} else {
			while (!this.allReady()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
			}
			this._join("lobby");
		}
	}
		
	public void leave() {
		for (String channel : this.channels) {
			this._leave(channel);
		}
	}
		
	public void leave(String symbol) {
		if (!symbol.isBlank()) {
			if (channels.contains(symbol)) this._leave(symbol);
		}
	}
		
	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			if (channels.contains(symbol)) this._leave(symbol);
		}
	}
	
	public void leaveLobby() {
		if (channels.contains("lobby")) this.leave("lobby");
	}
	
	public void start() throws Exception {
		if (!(useOnTrade || useOnQuote || useOnRefresh || useOnUnusualActivity)) {
			throw new Exception("You must set at least one callback method before starting.");
		} else {
			String token = this.getToken();
			this.initializeWebSockets(token);
			this.isStarted = true;
		}
	}
		
	public void stop() {
		this.leave();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}
		wsLock.writeLock().lock();
		try {
			wsState.setReady(false);
		} finally {
			wsLock.writeLock().unlock();
		}
		this.isCancellationRequested = true;
		Client.Log("Websocket - Closing");
		wsState.getWebSocket().sendClose(1000, "Client closed");
		try {
			this.heartbeatThread.join();
			for (Thread thread : threads) {
				thread.join();
			}
		} catch (InterruptedException e) {}
		Client.Log("Stopped");
	}
	
	private int getDataSize() {
		return dataBucket.size();
	}
	
	public String getStats() {
		return String.format("Data Messages = %d, Text Messages = %d, Queue Depth = %d", this.dataMsgCount.get(), this.textMsgCount.get(), getDataSize());
	}
		
	public static void Log(String message) {
		System.out.println(message);
	}
		
	public static void Log(String message, Object... args) {
		System.out.printf(message + "%n", args);
	}
}
