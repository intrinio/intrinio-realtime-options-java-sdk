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

	static int getMessageSize(){
		return 46;
	}

	public static Quote parse(byte[] bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 21)).toString();
		QuoteType type;
		switch (bytes[21]) {
		case 1: type = QuoteType.ASK;
			break;
		case 2: type = QuoteType.BID;
			break;
		default: type = QuoteType.INVALID;
		}
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 22, 8);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getDouble();
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 30, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 34, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		return new Quote(type, symbol, price, size, timestamp);
	}

	public static Quote parse(ByteBuffer bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 21)).toString();
		QuoteType type;
		switch (bytes.get(21)) {
		case 1: type = QuoteType.ASK;
			break;
		case 2: type = QuoteType.BID;
			break;
		default: type = QuoteType.INVALID;
		}
		
		ByteBuffer priceBuffer = bytes.slice(22, 8);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getDouble();
		
		ByteBuffer sizeBuffer = bytes.slice(30, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(34, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		return new Quote(type, symbol, price, size, timestamp);
	}
	
}
