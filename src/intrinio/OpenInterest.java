package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public record OpenInterest (String symbol, int openInterest, double timestamp){
	
	public String toString() {
		return "Open Interest (" + this.symbol + "): " + this.openInterest;
	}
	
	public static OpenInterest parse(byte[] bytes) {
		String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 21)).toString();
		
		ByteBuffer openInterestBuffer = ByteBuffer.wrap(bytes, 22, 4);
		openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int openInterest = openInterestBuffer.getInt();
		
		ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 26, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		return new OpenInterest(symbol, openInterest, timestamp);
	}
	
	public static OpenInterest parse(ByteBuffer bytes) {
		
		String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 21)).toString();
				
		ByteBuffer openInterestBuffer = bytes.slice(22, 4);
		openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
		int openInterest = openInterestBuffer.getInt();
		
		ByteBuffer timeStampBuffer = bytes.slice(26, 8);
		timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
		double timestamp = timeStampBuffer.getDouble();
		
		return new OpenInterest(symbol, openInterest, timestamp);
	}
}
