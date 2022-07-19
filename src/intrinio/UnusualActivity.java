package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record UnusualActivity(
		String symbol,
		UnusualActivityType type, 
		UnusualActivitySentiment sentiment,
		float totalValue,
		long totalSize,
		float averagePrice,
		float askAtExecution,
		float bidAtExecution,
		float priceAtExecution,
		double timestamp) {
	
	public float getStrikePrice() {
		int whole = (this.symbol.charAt(13) - '0') * 10000 + (this.symbol.charAt(14) - '0') * 1000 + (this.symbol.charAt(15) - '0') * 100 + (this.symbol.charAt(16) - '0') * 10 + (this.symbol.charAt(17) - '0');
		float part = (this.symbol.charAt(18) - '0') * 0.1f + (this.symbol.charAt(19) - '0') * 0.01f + (this.symbol.charAt(20) - '0') * 0.001f;
		return (whole + part);
	}
	
	public boolean isPut() {
		return this.symbol.charAt(12) == 'P';
	}
	
	public boolean isCall() {
		return this.symbol.charAt(12) == 'C';
	}
	
	public ZonedDateTime getExpirationDate() {
		int year = 2000 + (this.symbol.charAt(6) - '0') * 10 + (this.symbol.charAt(7) - '0');
		int month = (this.symbol.charAt(8) - '0') * 10 + (this.symbol.charAt(9) - '0');
		int day = (this.symbol.charAt(10) - '0') * 10 + (this.symbol.charAt(11) - '0');
		ZoneId tz = ZoneId.of("America/New_York");
		return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, tz);
	}
	
	public String getUnderlyingSymbol() {
		int i;
		for (i = 5; i >= 0 && this.symbol.charAt(i) == '_'; i--);
		return this.symbol.substring(0,i+1);
	}
	
	public String toString() {
		String s =
				"Unusual Activity (" +
				"Symbol: " + this.symbol +
				", Activity Type: " + this.type +
				", Sentiment: " + this.sentiment +
				", Total Value: " + this.totalValue +
				", Total Size: " + this.totalSize +
				", AveragePrice: " + this.averagePrice +
				", Ask at Execution: " + this.askAtExecution +
				", Bid at Execution: " + this.bidAtExecution +
				", Underlying Price at Execution: " + this.priceAtExecution +
				", Timestamp: " + this.timestamp +
				")";
		return s;
	}
	
	public static UnusualActivity parse(byte[] bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 21)).toString();
		
		UnusualActivityType type;
		switch (bytes[21]) {
		case 4: type = UnusualActivityType.BLOCK;
			break;
		case 5: type = UnusualActivityType.SWEEP;
			break;
		case 6: type = UnusualActivityType.LARGE;
			break;
		default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes[22]) {
		case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
			break;
		case 1: sentiment = UnusualActivitySentiment.BULLISH;
			break;
		case 2: sentiment = UnusualActivitySentiment.BEARISH;
			break;
		default: sentiment = UnusualActivitySentiment.INVALID;
		}
		
		ByteBuffer totalValueBuffer = ByteBuffer.wrap(bytes, 23, 4);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float totalValue = totalValueBuffer.getFloat();
		
		ByteBuffer totalSizeBuffer = ByteBuffer.wrap(bytes, 27, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = ByteBuffer.wrap(bytes, 31, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float averagePrice = averagePriceBuffer.getFloat();
		
		ByteBuffer askAtExecutionBuffer = ByteBuffer.wrap(bytes, 35, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float askAtExecution = askAtExecutionBuffer.getFloat();
		
		ByteBuffer bidAtExecutionBuffer = ByteBuffer.wrap(bytes, 39, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float bidAtExecution = bidAtExecutionBuffer.getFloat();
		
		ByteBuffer priceAtExecutionBuffer = ByteBuffer.wrap(bytes, 43, 4);
		priceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float priceAtExecution = priceAtExecutionBuffer.getFloat();
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 47, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		return new UnusualActivity(symbol, type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, priceAtExecution, timestamp);
	}
	
	public static UnusualActivity parse(ByteBuffer bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 21)).toString();
		
		UnusualActivityType type;
		switch (bytes.get(21)) {
		case 4: type = UnusualActivityType.BLOCK;
			break;
		case 5: type = UnusualActivityType.SWEEP;
			break;
		case 6: type = UnusualActivityType.LARGE;
			break;
		default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes.get(22)) {
		case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
			break;
		case 1: sentiment = UnusualActivitySentiment.BULLISH;
			break;
		case 2: sentiment = UnusualActivitySentiment.BEARISH;
			break;
		default: sentiment = UnusualActivitySentiment.INVALID;
		}
		
		ByteBuffer totalValueBuffer = bytes.slice(23, 4);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float totalValue = totalValueBuffer.getFloat();
		
		ByteBuffer totalSizeBuffer = bytes.slice(27, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = bytes.slice(31, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float averagePrice = averagePriceBuffer.getFloat();
		
		ByteBuffer askAtExecutionBuffer = bytes.slice(35, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float askAtExecution = askAtExecutionBuffer.getFloat();
		
		ByteBuffer bidAtExecutionBuffer = bytes.slice(39, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float bidAtExecution = bidAtExecutionBuffer.getFloat();
		
		ByteBuffer priceAtExecutionBuffer = bytes.slice(43, 4);
		priceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		float priceAtExecution = priceAtExecutionBuffer.getFloat();
		
		ByteBuffer timeStampBuffer = bytes.slice(47, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		return new UnusualActivity(symbol, type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, priceAtExecution, timestamp);
	}
}
