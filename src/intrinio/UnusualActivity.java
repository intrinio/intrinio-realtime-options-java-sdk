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
		return Float.parseFloat(this.symbol.substring(this.symbol.indexOf('_') + 8));
	}

	public boolean isPut() { return this.symbol.charAt(this.symbol.indexOf('_') + 7) == 'P'; }

	public boolean isCall() {
		return this.symbol.charAt(this.symbol.indexOf('_') + 7) == 'C';
	}

	public ZonedDateTime getExpirationDate() {
		int dateStartIndex = this.symbol.indexOf('_') + 1;
		int year = 2000 + (this.symbol.charAt(dateStartIndex) - '0') * 10 + (this.symbol.charAt(dateStartIndex + 1) - '0');
		int month = (this.symbol.charAt(dateStartIndex + 2) - '0') * 10 + (this.symbol.charAt(dateStartIndex + 3) - '0');
		int day = (this.symbol.charAt(dateStartIndex + 4) - '0') * 10 + (this.symbol.charAt(dateStartIndex + 5) - '0');
		ZoneId tz = ZoneId.of("America/New_York");
		return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, tz);
	}

	public String getUnderlyingSymbol() { return this.symbol.substring(0, this.symbol.indexOf('_')).trim(); }
	
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

	static int getMessageSize(){
		return 60;
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
