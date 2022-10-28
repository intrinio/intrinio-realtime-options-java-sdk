package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Quote(String symbol, double askPrice, long askSize, double bidPrice, long bidSize, double timestamp) {
	
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
		return String.format("Quote (Symbol: %s, AskPrice: %s, AskSize: %s, BidPrice: %s, BidSize: %s, Timestamp: %s)",
				this.symbol,
				this.askPrice,
				this.askSize,
				this.bidPrice,
				this.bidSize,
				this.timestamp);
	}

	public static Quote parse(byte[] bytes) {
		//byte structure:
		// symbol length [0]
		// symbol [1-21]
		// event type [22]
		// price type [23]
		// ask price [24-27]
		// ask size [28-31]
		// bid price [32-35]
		// bid size [36-39]
		// timestamp [40-47]

		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

		PriceType scaler = PriceType.fromInt(bytes[23]);
		
		ByteBuffer askPriceBuffer = ByteBuffer.wrap(bytes, 24, 4);
		askPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPrice = scaler.getScaledValue(askPriceBuffer.getInt());
		
		ByteBuffer askSizeBuffer = ByteBuffer.wrap(bytes, 28, 4);
		askSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long askSize = Integer.toUnsignedLong(askSizeBuffer.getInt());

		ByteBuffer bidPriceBuffer = ByteBuffer.wrap(bytes, 32, 4);
		bidPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPrice = scaler.getScaledValue(bidPriceBuffer.getInt());

		ByteBuffer bidSizeBuffer = ByteBuffer.wrap(bytes, 36, 4);
		bidSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long bidSize = Integer.toUnsignedLong(bidSizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 40, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;

		return new Quote(symbol, askPrice, askSize, bidPrice, bidSize, timestamp);
	}

	public static Quote parse(ByteBuffer bytes) {
		//byte structure:
		// symbol length [0]
		// symbol [1-21]
		// event type [22]
		// price type [23]
		// ask price [24-27]
		// ask size [28-31]
		// bid price [32-35]
		// bid size [36-39]
		// timestamp [40-47]

		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();

		PriceType scaler = PriceType.fromInt(bytes.get(23));
		
		ByteBuffer askPriceBuffer = bytes.slice(24, 4);
		askPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double askPrice = scaler.getScaledValue(askPriceBuffer.getInt());
		
		ByteBuffer askSizeBuffer = bytes.slice(28, 4);
		askSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long askSize = Integer.toUnsignedLong(askSizeBuffer.getInt());

		ByteBuffer bidPriceBuffer = bytes.slice(32, 4);
		bidPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double bidPrice = scaler.getScaledValue(bidPriceBuffer.getInt());

		ByteBuffer bidSizeBuffer = bytes.slice(36, 4);
		bidSizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long bidSize = Integer.toUnsignedLong(bidSizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(40, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = ((double) timeStampBuffer.getLong()) / 1_000_000_000.0D;

		return new Quote(symbol, askPrice, askSize, bidPrice, bidSize, timestamp);
	}
	
}
