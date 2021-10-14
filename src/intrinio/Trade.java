package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Trade(String symbol, double price, long size, long totalVolume, double timestamp) {

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
				"Trade (" +
				"Symbol: " + this.symbol +
				", Price: " + this.price +
				", Size: " + this.size +
				", Total Volume: " + this.totalVolume +
				", Timestamp: " + this.timestamp +
				")";
		return s;
	}
	
	public static Trade parse(byte[] bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 21)).toString();
		
		ByteBuffer priceBuffer = ByteBuffer.wrap(bytes, 22, 8);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getDouble();
		
		ByteBuffer sizeBuffer = ByteBuffer.wrap(bytes, 30, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 34, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		ByteBuffer volumeBuffer = ByteBuffer.wrap(bytes, 42, 8);
		volumeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long totalVolume = volumeBuffer.getLong();
		
		return new Trade(symbol, price, size, totalVolume, timestamp);
	}
	
	public static Trade parse(ByteBuffer bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 21)).toString();
		
		ByteBuffer priceBuffer = bytes.slice(22, 8);
		priceBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double price = priceBuffer.getDouble();
		
		ByteBuffer sizeBuffer = bytes.slice(30, 4);
		sizeBuffer.order(ByteOrder.LITTLE_ENDIAN);
		long size = Integer.toUnsignedLong(sizeBuffer.getInt());
		
		ByteBuffer timeStampBuffer = bytes.slice(34, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		return new Trade(symbol, price, size, 0, timestamp);
	}
	
}