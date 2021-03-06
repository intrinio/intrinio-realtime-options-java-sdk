package SampleApp;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import intrinio.*;

class TradeHandler implements OnTrade {
	private final ConcurrentHashMap<String,Integer> symbols = new ConcurrentHashMap<String,Integer>();
	private int maxTradeCount = 0;
	private Trade maxTrade;
	
	public int getMaxTradeCount() {
		return maxTradeCount;
	}

	public Trade getMaxTrade() {
		return maxTrade;
	}
	
	public void onTrade(Trade trade) {
		symbols.compute(trade.symbol(), (String key, Integer value) -> {
			if (value == null) {
				if (maxTradeCount == 0) {
					maxTradeCount = 1;
					maxTrade = trade;
				}
				return 1;
			} else {
				if (value + 1 > maxTradeCount) {
					maxTradeCount = value + 1;
					maxTrade = trade;
				}
				return value + 1;
			}
		});
	}
	
	public void tryLog() {
		if (maxTradeCount > 0) {
			Client.Log("Most active trade symbol: %s (%d updates)", maxTrade.symbol(), maxTradeCount);
			Client.Log("%s - Trade (price = %f, size = %d, isPut = %b, isCall = %b, exp = %s)",
					maxTrade.symbol(),
					maxTrade.price(),
					maxTrade.size(),
					maxTrade.isPut(),
					maxTrade.isCall(),
					maxTrade.getExpirationDate().toString());
		}
	}
}

class QuoteHandler implements OnQuote {
	private final ConcurrentHashMap<String,Integer> symbols = new ConcurrentHashMap<String,Integer>();
	private int maxQuoteCount = 0;
	private Quote maxQuote;
	
	public int getMaxQuoteCount() {
		return maxQuoteCount;
	}

	public Quote getMaxQuote() {
		return maxQuote;
	}
	
	public void onQuote(Quote quote) {
		symbols.compute(quote.symbol() + ":" + quote.type(), (String key, Integer value) -> {
			if (value == null) {
				if (maxQuoteCount == 0) {
					maxQuoteCount = 1;
					maxQuote = quote;
				}
				return 1;
			} else {
				if (value + 1 > maxQuoteCount) {
					maxQuoteCount = value + 1;
					maxQuote = quote;
				}
				return value + 1;
			}
		});
	}
	
	public void tryLog() {
		if (maxQuoteCount > 0) {
			Client.Log("Most active quote symbol: %s:%s (%d updates)", maxQuote.symbol(), maxQuote.type(), maxQuoteCount);
			Client.Log("%s - Quote (type = %s, price = %f, size = %d, isPut = %b, isCall = %b, exp = %s)",
					maxQuote.symbol(),
					maxQuote.type(),
					maxQuote.price(),
					maxQuote.size(),
					maxQuote.isPut(),
					maxQuote.isCall(),
					maxQuote.getExpirationDate().toString());
		}
	}
}

class OpenInterestHandler implements OnOpenInterest {
	public void onOpenInterest(OpenInterest oi) {
		Client.Log("Open Interest (%s) = %d", oi.symbol(), oi.openInterest());
	}
}

public class SampleApp {
	
	public static void main(String[] args) {
		Client.Log("Starting sample app");
		TradeHandler tradeHandler = new TradeHandler();
		QuoteHandler quoteHandler = new QuoteHandler();
		OpenInterestHandler openInterestHandler = new OpenInterestHandler();
		//Config config = null; //You can either create a config class or default to using the intrinio/config.json file
		//try {config = new Config("apiKeyHere", Provider.OPRA, null, null, false, 8);} catch (Exception e) {e.printStackTrace();}
		//Client client = new Client(tradeHandler, quoteHandler, openInterestHandler, config);
		Client client = new Client(tradeHandler, quoteHandler, openInterestHandler);
		Timer timer = new Timer();
		TimerTask task = new TimerTask() {
			public void run() {
				Client.Log(client.getStats());
				tradeHandler.tryLog();
				quoteHandler.tryLog();
			}
		};
		timer.schedule(task, 10000, 10000);
		client.join(); //use channels configured in config
		//client.join(new String[] {"GOOG__210917C01040000", "MSFT__210917C00180000", "AAPL__210917C00130000"}); //manually specify channels
	}
}
