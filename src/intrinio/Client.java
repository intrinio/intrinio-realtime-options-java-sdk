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
import java.util.ArrayList;

import com.google.gson.Gson;

class WebSocketState {
	
	private WebSocket ws;
	private int index = WebSocketState.assignIndex();
	private boolean isReady = false;
	private boolean isReconnecting = false;
	private LocalDateTime lastReset = LocalDateTime.now();
	
	private static AtomicInteger nextIndex = new AtomicInteger(0);
	
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
	
	int getIndex() {
		return this.index;
	}
	
	static int assignIndex() {
		return WebSocketState.nextIndex.getAndAdd(1);
	}
	
}

record Token (String token, LocalDateTime date) {}

record Channel (String symbol, boolean tradesOnly) {}

public class Client implements WebSocket.Listener {
	private final String heartbeatMessage = "{\"topic\":\"phoenix\",\"event\":\"heartbeat\",\"payload\":{},\"ref\":null}";
	private final String heartbeatResponse = "{\"topic\":\"phoenix\",\"ref\":null,\"payload\":{\"status\":\"ok\",\"response\":{}},\"event\":\"phx_reply\"}";
	private final long[] selfHealBackoffs = {1000, 30000, 60000, 300000, 600000};
	private final ReentrantReadWriteLock tLock = new ReentrantReadWriteLock();
	private final ReentrantReadWriteLock wsLock = new ReentrantReadWriteLock();
	private final Config config = Config.load();
	private final int FirehoseSocketCount = 6;
	private final LinkedBlockingDeque<byte[]> data = new LinkedBlockingDeque<>();
	
	private AtomicReference<Token> token = new AtomicReference<Token>(new Token(null, LocalDateTime.now()));
	private Hashtable<Integer,WebSocketState> wsStates = new Hashtable<Integer,WebSocketState>();
	private Hashtable<Integer, Integer> wsIndices = new Hashtable<Integer, Integer>();
	private AtomicLong dataMsgCount = new AtomicLong(0l);
	private AtomicLong textMsgCount = new AtomicLong(0l);
	private HashSet<Channel> channels = new HashSet<Channel>();
	private ArrayList<LinkedBlockingDeque<Tuple<byte[], Boolean>>> dataBuckets;
	private OnTrade onTrade = (Trade trade) -> {};
	private OnQuote onQuote = (Quote quote) -> {};
	private OnOpenInterest onOpenInterest = (OpenInterest oi) -> {};
	private Thread[] threads = new Thread[config.getNumThreads()];
	private Lock[] dataBucketLocks;
	private boolean isCancellationRequested = false;
	private int socketCount;
	
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
				Client.Log("Sending heartbeat");
				wsLock.readLock().lock();
				try {
					for (WebSocketState wss : wsStates.values()) {
						if (wss.isReady()) {
							wss.getWebSocket().sendText(heartbeatMessage, true);
						}
					}
				} finally {
					wsLock.readLock().unlock();
				}
				
			} catch (InterruptedException e) {}
		}
	});
	
	private byte[] getCompleteData(int dataIndex) {
		Queue<byte[]> parts = new LinkedList<>();
		int length = 0;
		boolean done = false;
		dataBucketLocks[dataIndex].lock();
		try {			
			while (!done) {
				try {				
					Tuple<byte[], Boolean> datum = dataBuckets.get(dataIndex).poll(1, TimeUnit.SECONDS);
					if (datum != null) {
						parts.add(datum.x);
						done = datum.y;
						length += datum.x.length;
					}
				} catch(InterruptedException e) {
					Client.Log("process data interrupted");
				}
			}			
		} finally {dataBucketLocks[dataIndex].unlock();}		
		
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
						byte type = datum[offset + 21];						
						ByteBuffer offsetBuffer;
						switch (type) {
						case 0:
							offsetBuffer = buffer.slice(offset, 50);
							Trade trade = Trade.parse(offsetBuffer);
							onTrade.onTrade(trade);
							offset += 50;
							break;
						case 1:
						case 2:
							offsetBuffer = buffer.slice(offset, 42);
							Quote quote = Quote.parse(offsetBuffer);
							onQuote.onQuote(quote);
							offset += 42;
							break;
						case 3:
							offsetBuffer = buffer.slice(offset, 34);
							OpenInterest oi = OpenInterest.parse(offsetBuffer);
							onOpenInterest.onOpenInterest(oi);
							offset += 34;
							break;
						default:
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
		switch (config.getProvider()) {
		case OPRA:
		case MANUAL:
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new Thread(processData);
			}
			break;
		case OPRA_FIREHOSE:
		case MANUAL_FIREHOSE:
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new Thread(processData);
			}
			break;
		default: throw new Exception("Provider not specified!");
		}
	}
	
	private boolean allReady() {
		wsLock.readLock().lock();
		try {
			if (wsStates.isEmpty()) return false;
			boolean allReady = true;
			for (WebSocketState wsState : wsStates.values()) {
				allReady &= wsState.isReady();
			}
			return allReady;
		} finally {
			wsLock.readLock().unlock();
		}
	}
	
	private String getAuthUrl() throws Exception {
		String authUrl;
		switch (config.getProvider()) {
		case OPRA: authUrl = "https://realtime-options.intrinio.com/auth?api_key=" + config.getApiKey();
			break;
		case OPRA_FIREHOSE: authUrl = "https://realtime-options-firehose.intrinio.com:8000/auth?api_key=" + config.getApiKey();
			break;
		case MANUAL: authUrl = "http://" + config.getIpAddress() + "/auth?api_key=" + config.getApiKey();
			break;
		case MANUAL_FIREHOSE: authUrl = "http://" + config.getIpAddress() + ":8000/auth?api_key=" + config.getApiKey();
			break;
		default: throw new Exception("Provider not specified!");
		}
		return authUrl;
	}
	
	private String getWebSocketUrl (String token, int index) throws Exception {
		String wsUrl;
		switch (config.getProvider()) {
		case OPRA: wsUrl = "wss://realtime-options.intrinio.com/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case OPRA_FIREHOSE: wsUrl = "wss://realtime-options-firehose.intrinio.com:800" + index + "/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case MANUAL: wsUrl = "ws://" + config.getIpAddress() + "/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		case MANUAL_FIREHOSE: wsUrl = "ws://" + config.getIpAddress() + ":800" + index + "/socket/websocket?vsn=1.0.0&token=" + token;
			break;
		default: throw new Exception("Provider not specified!");
		}
		return wsUrl;
	}
	
	private int getWebSocketCount() throws Exception {
		int count;
		switch (config.getProvider()) {
		case OPRA:
		case MANUAL:
			count = 1;
			break;
		case OPRA_FIREHOSE:
		case MANUAL_FIREHOSE:
			count = FirehoseSocketCount;
			break;
		default: throw new Exception("Provider not specified!");
		}
		return count;
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
		
	private void tryReconnect(int wsId) {
		BooleanSupplier reconnectFn = () -> { 
			WebSocketState wsState = wsStates.get(wsId);
			Client.Log("Websocket %d - Reconnecting...", wsState.getIndex());
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
					resetWebSocket(wsId, token);
				} else {
					resetWebSocket(wsId, this.token.get().token());
				}
				return false;
			}
		};
		this.doBackoff(reconnectFn);
	}
		
	private void onWebSocketConnected (WebSocket ws, WebSocketState wsState) {
		if (!channels.isEmpty()) {
			String lastOnly;
			String message;
			for (Channel channel : channels) {
				if (channel.tradesOnly()) {
					lastOnly = "true";
				} else {
					lastOnly = "false";
				}
				message = "{\"topic\":\"options:" + channel.symbol() + "\",\"event\":\"phx_join\",\"last_only\":\"" + lastOnly+ "\",\"payload\":{},\"ref\":null}";
				Client.Log("Websocket %d - Joining channel: %s (trades only = %s)", wsState.getIndex(), channel.symbol(), lastOnly);
				wsState.getWebSocket().sendText(message, true);
			}
		}
	}
	
	public CompletionStage<Void> onClose(WebSocket ws, int status, String reason) {
		WebSocketState wsState = wsStates.get(ws.hashCode());
		wsLock.readLock().lock();
		try {
			if (!wsState.isReconnecting()) {
				Client.Log("Websocket %d - Closed", wsState.getIndex());
				wsLock.readLock().unlock();
				wsLock.writeLock().lock();
				try {
					wsState.setReady(false);
				} finally {
					wsLock.writeLock().unlock();
				}
				if (!this.isCancellationRequested) {
					new Thread(() -> {
						this.tryReconnect(ws.hashCode());
					}).start();
				}
			}
		} finally {
			wsLock.readLock().unlock();
		}
		return null;
	}
		
	public void onError(WebSocket ws, Throwable err) {
		WebSocketState wsState = wsStates.get(ws.hashCode());
		Client.Log("Websocket %d - Error - %s", wsState.getIndex(), err.getMessage());
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
		int dataIndex = wsIndices.get(ws.hashCode());
		this.dataBuckets.get(dataIndex).add(new Tuple<byte[], Boolean>(bytes, isComplete));
		if (isComplete) {
			this.data.add(getCompleteData(dataIndex));
		}
		ws.request(1);
		return null;
	}
		
	private void resetWebSocket(int wsId, String token) {
		WebSocketState wsState = wsStates.get(wsId);
		Client.Log("Websocket %d - Resetting", wsState.getIndex());
		String wsUrl;
		try {
			wsUrl = this.getWebSocketUrl(token, wsStates.get(wsId).getIndex());
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
			Client.Log("Websocket %d - Reset", wsState.getIndex());
			wsLock.writeLock().lock();
			try {
				wsState.setWebSocket(ws);
				wsState.reset();
				wsState.setReady(true);
				wsState.setReconnecting(false);
				this.wsStates.remove(wsId);
				this.wsStates.put(ws.hashCode(), wsState);
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
			int wsCount;
			try {
				wsCount = socketCount;
			} catch (Exception e) {
				Client.Log("Initialization Failure. " + e.getMessage());
				return;
			}
			for (int i = 0; i < wsCount; i++) {
				Client.Log("Websocket %d - Connecting...", i);
				WebSocketState wsState = new WebSocketState();
				String wsUrl;
				try {
					wsUrl = this.getWebSocketUrl(token, wsState.getIndex());
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
					Client.Log("Websocket %d - Connected", wsState.getIndex());
					wsState.setWebSocket(ws);
					this.wsStates.put(wsState.hashCode(), wsState);
					this.wsIndices.put(ws.hashCode(), wsState.getIndex());
					wsState.setReady(true);
					wsState.setReconnecting(false);
					if (!heartbeatThread.isAlive()) {
						heartbeatThread.start();
					}
					for (Thread thread : threads) {
						if (!thread.isAlive()) {
							thread.start();
						}
					}
					this.onWebSocketConnected(ws, wsState);
				} catch (ExecutionException e) {
					Client.Log("Initialization Failure. Could not establish connection. %s", e.getMessage());
					return;
				} catch (InterruptedException e) {
					Client.Log("Initialization Failure. Thread interrupted. %s", e.getMessage());
					return;
				}
			}
		} finally {
			wsLock.writeLock().unlock();
		}
	}
		
	private void _join(String symbol, boolean tradesOnly) {
		String lastOnly;
		if (tradesOnly) {
			lastOnly = "true";
		} else {
			lastOnly = "false";
		}
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.add(channel)) {
			String message = "{\"topic\":\"options:" + symbol + "\",\"event\":\"phx_join\",\"last_only\":\"" + lastOnly + "\",\"payload\":{},\"ref\":null}";
			for (WebSocketState wsState : this.wsStates.values()) {
				Client.Log("Websocket %d - Joining channel: %s (trades only = %s)", wsState.getIndex(), symbol, lastOnly);
				wsState.getWebSocket().sendText(message, true);
			}
		}
	}
		
	private void _leave(String symbol, boolean tradesOnly) {
		String lastOnly;
		if (tradesOnly) {
			lastOnly = "true";
		} else {
			lastOnly = "false";
		}
		Channel channel = new Channel(symbol, tradesOnly);
		if (channels.remove(channel)) {
			String message = "{\"topic\":\"options:" + symbol + "\",\"event\":\"phx_leave\",\"last_only\":\"" + lastOnly + "\",\"payload\":{},\"ref\":null}";
			for (WebSocketState wsState : this.wsStates.values()) {
				Client.Log("Websocket %d - Leaving channel: %s (trades only = %s)", wsState.getIndex(), symbol, lastOnly);
				wsState.getWebSocket().sendText(message, true);
			}
		}
	}
		
	protected void finalize() {
		if (!this.isCancellationRequested) {
			this.stop();
		}
	}
		
	public Client() {
		try {
			socketCount = getWebSocketCount();
			dataBucketLocks = new ReentrantLock[socketCount];
			dataBuckets = new ArrayList<LinkedBlockingDeque<Tuple<byte[], Boolean>>>(socketCount);
			for(int i = 0; i < socketCount; i++) {
				dataBucketLocks[i] = new ReentrantLock();
				dataBuckets.add(new LinkedBlockingDeque<Tuple<byte[], Boolean>>());
			}				
			this.initializeThreads();
		} catch (Exception e) {
			Client.Log("Initialization Failure. " + e.getMessage());
		}
		String token = this.getToken();
		this.initializeWebSockets(token);
	}
		
	public Client(OnTrade onTrade){
		this();
		this.onTrade = onTrade;
	}
		
	public Client(OnTrade onTrade, OnQuote onQuote){
		this();
		this.onTrade = onTrade;
		this.onQuote = onQuote;
	}
		
	public Client(OnTrade onTrade, OnQuote onQuote, OnOpenInterest onOpenInterest) {
		this();
		this.onTrade = onTrade;
		this.onQuote = onQuote;
		this.onOpenInterest = onOpenInterest;
	}
		
	public void join() {
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		String[] symbols = config.getSymbols();
		boolean tradesOnly = config.isTradesOnly();
		for (String symbol : symbols) {
			if (!this.channels.contains(new Channel(symbol, tradesOnly))) {
				this._join(symbol, tradesOnly);
			}
		}
	}
		
	public void join(String symbol, boolean tradesOnly) {
		boolean t = tradesOnly || config.isTradesOnly();
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		if (!this.channels.contains(new Channel(symbol, tradesOnly))) {
			this._join(symbol, t);
		}
	}
		
	public void join(String symbol) {
		this.join(symbol, false);
	}
		
	public void join(String[] symbols, boolean tradesOnly) {
		boolean t = tradesOnly || config.isTradesOnly();
		while (!this.allReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {}
		}
		for (String symbol : symbols) {
			if (!this.channels.contains(new Channel(symbol, t))) {
				this._join(symbol, t);
			}
		}
	}
		
	public void join(String[] symbols) {
		this.join(symbols, false);
	}
		
	public void leave() {
		for (Channel channel : this.channels) {
			this._leave(channel.symbol(), channel.tradesOnly());
		}
	}
		
	public void leave(String symbol) {
		for (Channel channel : this.channels) {
			if (channel.symbol() == symbol) {
				this._leave(symbol, channel.tradesOnly());
			}
		}
	}
		
	public void leave(String[] symbols) {
		for (String symbol : symbols) {
			for (Channel channel : this.channels) {
				if (channel.symbol() == symbol) {
					this._leave(symbol, channel.tradesOnly());
				}
			}
		}
	}
		
	public void stop() {
		this.leave();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}
		wsLock.writeLock().lock();
		try {
			for (WebSocketState wsState : this.wsStates.values()) {
				wsState.setReady(false);
			}
		} finally {
			wsLock.writeLock().unlock();
		}
		this.isCancellationRequested = true;
		for (WebSocketState wsState : this.wsStates.values()) {
			Client.Log("Websocket %d - Closing", wsState.getIndex());
			wsState.getWebSocket().sendClose(1000, "Client closed");
		}
		try {
			this.heartbeatThread.join();
			for (Thread thread : threads) {
				thread.join();
			}
		} catch (InterruptedException e) {}
		Client.Log("Stopped");
	}
	
	private int getDataSize() {
		int j = 0;
		for(int i = 0; i < dataBuckets.size(); i++)
			j += dataBuckets.get(i).size();
		return j;
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
