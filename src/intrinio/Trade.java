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
		// symbol length [0]
		// symbol [1-21]
		// event type [22]
		// price type [23]
		// underlying price type [24]
		// price [25-28]
		// size [29-32]
		// timestamp [33-40]
		// total volume [41-48]
		// ask price at execution [49-52]
		// bid price at execution [53-56]
		// underlying price at execution [57-60]

		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

		PriceType scaler = PriceType.fromInt(bytes[23]);
		PriceType underlyingScaler = PriceType.fromInt(bytes[24]);

		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 25, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();
		double price = scaler.getScaledValue(unscaledPrice);

		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 29, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 33, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 41, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 49, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 53, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underlyingPriceAtExecutionBuffer = ByteBuffer.wrap(bytes, 57, 4);
		underlyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underlyingPriceAtExecutionBuffer.getInt());
		
		return new Trade(symbol, price, size, timestamp, totalVolume, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
	public static Trade parse(ByteBuffer bytes) {
		//byte structure:
		// symbol length [0]
		// symbol [1-21]
		// event type [22]
		// price type [23]
		// underlying price type [24]
		// price [25-28]
		// size [29-32]
		// timestamp [33-40]
		// total volume [41-48]
		// ask price at execution [49-52]
		// bid price at execution [53-56]
		// underlying price at execution [57-60]

		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();

		PriceType scaler = PriceType.fromInt(bytes.get(23));
		PriceType underlyingScaler = PriceType.fromInt(bytes.get(24));

		ByteBuffer priceBuffer = bytes.slice(25, 4);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int unscaledPrice = priceBuffer.getInt();
		double price = scaler.getScaledValue(unscaledPrice);
		
		ByteBuffer sizeBuffer = bytes.slice(29, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(33, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;
		
		ByteBuffer volumeBuffer = bytes.slice(41, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();

		ByteBuffer askPriceAtExecutionBuffer = bytes.slice(49, 4);
		askPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPriceAtExecution = scaler.getScaledValue(askPriceAtExecutionBuffer.getInt());

		ByteBuffer bidPriceAtExecutionBuffer = bytes.slice(53, 4);
		bidPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPriceAtExecution = scaler.getScaledValue(bidPriceAtExecutionBuffer.getInt());

		ByteBuffer underyingPriceAtExecutionBuffer = bytes.slice(57, 4);
		underyingPriceAtExecutionBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double underlyingPriceAtExecution = underlyingScaler.getScaledValue(underyingPriceAtExecutionBuffer.getInt());

		return new Trade(symbol, price, size, timestamp, totalVolume, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
	
}