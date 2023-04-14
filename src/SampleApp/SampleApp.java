package SampleApp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import intrinio.*;

class TradeHandler implements OnTrade {

	public AtomicInteger tradeCount = new AtomicInteger(0);
	
	public void onTrade(Trade trade) {
		tradeCount.incrementAndGet();
	}
}

class QuoteHandler implements OnQuote {
	public AtomicInteger quoteCount = new AtomicInteger(0);
	
	public void onQuote(Quote quote) {
		quoteCount.incrementAndGet();
	}
}

class RefreshHandler implements OnRefresh {
	public AtomicInteger rCount = new AtomicInteger(0);

	public void onRefresh(Refresh r) {
		rCount.incrementAndGet();
	}
}

class UnusualActivityHandler implements OnUnusualActivity {
	public AtomicInteger blockCount = new AtomicInteger(0);
	public AtomicInteger sweepCount = new AtomicInteger(0);
	public AtomicInteger largeTradeCount = new AtomicInteger(0);
	public AtomicInteger unusualSweepCount = new AtomicInteger(0);
	
	public void onUnusualActivity(UnusualActivity ua) {
		switch (ua.type()){
			case BLOCK:
				blockCount.incrementAndGet();
				break;
			case SWEEP:
				sweepCount.incrementAndGet();
				break;
			case LARGE:
				largeTradeCount.incrementAndGet();
				break;
			case UNUSUAL_SWEEP:
				unusualSweepCount.incrementAndGet();
				break;
			default:
				Client.Log("Sample App - Invalid UA type detected: %s", ua.type().toString());
				break;
		}
	}
}

public class SampleApp {
	
	public static void main(String[] args) {
		Client.Log("Starting sample app");
		
		// Create only the handlers/callbacks that you need
		// These will get registered below
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		RefreshHandler refreshHandler = new RefreshHandler();
		UnusualActivityHandler unusualActivityHandler = new UnusualActivityHandler();
		
		// You can either create a config class or default to using the intrinio/config.json file
		//Config config = null;
		//try {config = new Config("apiKeyHere", Provider.OPRA, null, null, 8);} catch (Exception e) {e.printStackTrace();}
		//Client client = new Client(config);
		Client client = new Client();
		
		// Register a callback for a graceful shutdown
		Runtime.getRuntime().addShutdownHook(new Thread( new Runnable() {
			public void run() {
				client.leave();
				Client.Log("Stopping sample app");
				client.stop();
			}
		}));
		
		try {
			// Register only the callbacks that you want.
			// Take special care when registering the 'OnQuote' handler as it will increase throughput by ~10x
			client.setOnTrade(tradeHandler);
			client.setOnQuote(quoteHandler);
			client.setOnRefresh(refreshHandler);
			client.setOnUnusualActivity(unusualActivityHandler);
			
			// Start the client
			client.start();
			
			// Use this to subscribe to a static list of symbols (option contracts) provided in config.json
			//client.join();
			
			// Use this to subscribe to the entire univers of symbols (option contracts). This requires special permission.
			client.joinLobby();

			// Use this to subscribe, dynamically, to an option chain (all option contracts for a given underlying symbol).
			//client.join("AAPL");

			// Use this to subscribe, dynamically, to a specific option contract.
			//client.join("AAP___230616P00250000");

			// Use this to subscribe, dynamically, a list of specific option contracts or option chains.
			//client.join(new String[] {"GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000", "TSLA"});
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
				LocalDateTime now = LocalDateTime.now();
				String date = dtf.format(now);
				Client.Log(date + " " + client.getStats());
				String appStats = String.format(
						"%s Messages (Trades = %d, Quotes = %d, Refreshes = %d, Blocks = %d, Sweeps = %d, Larges = %d, UnusualSweeps = %d)",
						date,
						tradeHandler.tradeCount.get(),
						quoteHandler.quoteCount.get(),
						refreshHandler.rCount.get(),
						unusualActivityHandler.blockCount.get(),
						unusualActivityHandler.sweepCount.get(),
						unusualActivityHandler.largeTradeCount.get(),
						unusualActivityHandler.unusualSweepCount.get());
				Client.Log(appStats);
			}
		};
		timer.schedule(task, 10000, 10000);
	}
}
