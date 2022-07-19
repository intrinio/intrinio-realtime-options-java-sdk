package SampleApp;

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
	public AtomicInteger askCount = new AtomicInteger(0);
	public AtomicInteger bidCount = new AtomicInteger(0);
	
	public void onQuote(Quote quote) {
		if (quote.type() == QuoteType.ASK) {
			askCount.incrementAndGet();
		} else if (quote.type() == QuoteType.BID) {
			bidCount.incrementAndGet();
		} else {
			Client.Log("Sample App - Invalid quote type detected: %s", quote.type().toString());
		}
	}
}

class OpenInterestHandler implements OnOpenInterest {
	public AtomicInteger oiCount = new AtomicInteger(0);
	
	public void onOpenInterest(OpenInterest oi) {
		oiCount.incrementAndGet();
	}
}

class UnusualActivityHandler implements OnUnusualActivity {
	public AtomicInteger blockCount = new AtomicInteger(0);
	public AtomicInteger sweepCount = new AtomicInteger(0);
	public AtomicInteger largeTradeCount = new AtomicInteger(0);
	
	public void onUnusualActivity(UnusualActivity ua) {
		if (ua.type() == UnusualActivityType.BLOCK) {
			blockCount.incrementAndGet();
		} else if (ua.type() == UnusualActivityType.SWEEP) {
			sweepCount.incrementAndGet();
		} else if (ua.type() == UnusualActivityType.LARGE) {
			largeTradeCount.incrementAndGet();
		} else {
			Client.Log("Sample App - Invalid UA type detected: %s", ua.type().toString());
		}
	}
}

public class SampleApp {
	
	public static void main(String[] args) {
		Client.Log("Starting sample app");
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		OpenInterestHandler openInterestHandler = new OpenInterestHandler();
		UnusualActivityHandler unusualActivityHandler = new UnusualActivityHandler();
		//Config config = null; //You can either create a config class or default to using the intrinio/config.json file
		//try {config = new Config("apiKeyHere", Provider.OPRA, null, null, false, 8);} catch (Exception e) {e.printStackTrace();}
		//Client client = new Client(config);
		Client client = new Client();
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				Client.Log(client.getStats());
				String appStats = String.format(
						"Messages (Trade = %d, Ask = %d, Bid = %d, Open Interest = %d, Block = %d, Sweep = %d, Large = %d)",
						tradeHandler.tradeCount.get(),
						quoteHandler.askCount.get(),
						quoteHandler.bidCount.get(),
						openInterestHandler.oiCount.get(),
						unusualActivityHandler.blockCount.get(),
						unusualActivityHandler.sweepCount.get(),
						unusualActivityHandler.largeTradeCount.get());
				Client.Log(appStats);
			}
		};
		timer.schedule(task, 10000, 10000);
		client.join(); //use channels configured in config
		//client.join(new String[] {"GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000"}); //manually specify channels
	}
}
