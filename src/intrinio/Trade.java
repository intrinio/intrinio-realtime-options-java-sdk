package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Trade(String symbol, double price, long size, double timestamp, long totalVolume, double askPriceAtExecution, double bidPriceAtExecution, double underlyingPriceAtExecution) {

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
		return String.format("Quote (Symbol: %s, Price: %s, Size: %s, Timestamp: %s, TotalVolume: %s, AskPriceAtExecution: %s, BidPriceAtExecution: %s, UnderlyingPriceAtExecution: %s)",
				this.symbol,
				this.price,
				this.size,
				this.timestamp,
				this.totalVolume,
				this.askPriceAtExecution,
				this.bidPriceAtExecution,
				this.underlyingPriceAtExecution);
	}

	public static Trade parse(byte[] bytes) {
		//byte structure:
		// symbol [0-19]
		// event type [20]
		// price [21-24]
		// price type [25]
		// underlying price type [26]
		// size [27-30]
		// timestamp [31-38]
		// total volume [39-46]
		// ask price at execution [47-50]
		// bid price at execution [51-54]
		// underlying price at execution [55-58]

		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 20)).toString();
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 21, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();

		PriceType scaler = PriceType.fromInt(bytes[25]);
		PriceType underlyingScaler = PriceType.fromInt(bytes[26]);
		double price = scaler.getScaledValue(unscaledPrice);
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 27, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 31, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 39, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 47, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 51, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underlyingPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 55, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		return new Trade(symbol, price, size, timestamp, totalVolume, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
	public static Trade parse(ByteBuffer bytes) {
		//byte structure:
		// symbol [0-19]
		// event type [20]
		// price [21-24]
		// price type [25]
		// underlying price type [26]
		// size [27-30]
		// timestamp [31-38]
		// total volume [39-46]
		// ask price at execution [47-50]
		// bid price at execution [51-54]
		// underlying price at execution [55-58]
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 20)).toString();
		
		ByteBuffer priceBuffer = bytes.slice(21, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();

		PriceType scaler = PriceType.fromInt(bytes.get(25));
		PriceType underlyingScaler = PriceType.fromInt(bytes.get(26));
		double price = scaler.getScaledValue(unscaledPrice);
		
		ByteBuffer sizeBuffer = bytes.slice(27, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(31, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = bytes.slice(39, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = bytes.slice(47, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = bytes.slice(51, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underyingPriceAtExecutionBuffer = bytes.slice(55, 4);
		underyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underyingPriceAtExecutionBuffer.getInt());

		return new Trade(symbol, price, size, timestamp, totalVolume, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
}