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
		double totalValue,
		long totalSize,
		double averagePrice,
		double askPriceAtExecution,
		double bidPriceAtExecution,
		double underlyingPriceAtExecution,
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
		return String.format("Quote (Symbol: %s, Type: %s, Sentiment: %s, TotalValue: %s, TotalSize: %s, AveragePrice: %s, AskPriceAtExecution: %s, BidPriceAtExecution: %s, UnderlyingPriceAtExecution: %s, Timestamp: %s)",
				this.symbol,
				this.type,
				this.sentiment,
				this.totalValue,
				this.totalSize,
				this.averagePrice,
				this.askPriceAtExecution,
				this.bidPriceAtExecution,
				this.underlyingPriceAtExecution,
				this.timestamp);
	}

	public static UnusualActivity parse(byte[] bytes) {
		//byte structure:
		// symbol [0-19]
		// event type [20]
		// sentiment [21]
		// price type [22]
		// underlying price type [23]
		// total value [24-31]
		// total size [32-35]
		// average price [36-39]
		// ask price at execution [40-43]
		// bid price at execution [44-47]
		// underlying price at execution [48-51]
		// timestamp [52-59]

		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 20)).toString();
		
		UnusualActivityType type;
		switch (bytes[20]) {
			case 3: type = UnusualActivityType.BLOCK;
				break;
			case 4: type = UnusualActivityType.SWEEP;
				break;
			case 5: type = UnusualActivityType.LARGE;
				break;
			case 6: type = UnusualActivityType.GOLDEN;
				break;
			default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes[21]) {
			case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
				break;
			case 1: sentiment = UnusualActivitySentiment.BULLISH;
				break;
			case 2: sentiment = UnusualActivitySentiment.BEARISH;
				break;
			default: sentiment = UnusualActivitySentiment.INVALID;
		}

		PriceType scaler = PriceType.fromInt(bytes[22]);
		PriceType underlyingScaler = PriceType.fromInt(bytes[23]);
		
		ByteBuffer totalValueBuffer = ByteBuffer.wrap(bytes, 24, 8);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double totalValue = scaler.getScaledValue(totalValueBuffer.getLong());
		
		ByteBuffer totalSizeBuffer = ByteBuffer.wrap(bytes, 32, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = ByteBuffer.wrap(bytes, 36, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double averagePrice = scaler.getScaledValue(averagePriceBuffer.getInt());
		
		ByteBuffer askAtExecutionBuffer = ByteBuffer.wrap(bytes, 40, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askAtExecution = scaler.getScaledValue(askAtExecutionBuffer.getInt());
		
		ByteBuffer bidAtExecutionBuffer = ByteBuffer.wrap(bytes, 44, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidAtExecution = scaler.getScaledValue(bidAtExecutionBuffer.getInt());
		
		ByteBuffer underlyingPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 48, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 52, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		return new UnusualActivity(symbol, type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, underlyingPriceAtExecution, timestamp);
	}
	
	public static UnusualActivity parse(ByteBuffer bytes) {
		//byte structure:
		// symbol [0-19]
		// event type [20]
		// sentiment [21]
		// price type [22]
		// underlying price type [23]
		// total value [24-31]
		// total size [32-35]
		// average price [36-39]
		// ask price at execution [40-43]
		// bid price at execution [44-47]
		// underlying price at execution [48-51]
		// timestamp [52-59]

		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 20)).toString();
		
		UnusualActivityType type;
		switch (bytes.get(20)) {
			case 3: type = UnusualActivityType.BLOCK;
				break;
			case 4: type = UnusualActivityType.SWEEP;
				break;
			case 5: type = UnusualActivityType.LARGE;
				break;
			case 6: type = UnusualActivityType.GOLDEN;
				break;
			default: type = UnusualActivityType.INVALID;
		}
		
		UnusualActivitySentiment sentiment;
		switch (bytes.get(21)) {
		case 0: sentiment = UnusualActivitySentiment.NEUTRAL;
			break;
		case 1: sentiment = UnusualActivitySentiment.BULLISH;
			break;
		case 2: sentiment = UnusualActivitySentiment.BEARISH;
			break;
		default: sentiment = UnusualActivitySentiment.INVALID;
		}

		PriceType scaler = PriceType.fromInt(bytes.get(22));
		PriceType underlyingScaler = PriceType.fromInt(bytes.get(23));
		
		ByteBuffer totalValueBuffer = bytes.slice(24, 8);
		totalValueBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double totalValue = scaler.getScaledValue(totalValueBuffer.getLong());
		
		ByteBuffer totalSizeBuffer = bytes.slice(32, 4);
		totalSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalSize = Integer.toUnsignedLong(totalSizeBuffer.getInt());
		
		ByteBuffer averagePriceBuffer = bytes.slice(36, 4);
		averagePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double averagePrice = scaler.getScaledValue(averagePriceBuffer.getInt());
		
		ByteBuffer askAtExecutionBuffer = bytes.slice(40, 4);
		askAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askAtExecution = scaler.getScaledValue(askAtExecutionBuffer.getInt());
		
		ByteBuffer bidAtExecutionBuffer = bytes.slice(44, 4);
		bidAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidAtExecution = scaler.getScaledValue(bidAtExecutionBuffer.getInt());
		
		ByteBuffer underlyingPriceAtExecutionBuffer = bytes.slice(48, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(52, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		return new UnusualActivity(symbol, type, sentiment, totalValue, totalSize, averagePrice, askAtExecution, bidAtExecution, underlyingPriceAtExecution, timestamp);
	}
}
