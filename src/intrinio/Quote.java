package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Quote{
	public final String contract;
	public final double askPrice;
	public final long askSize;
	public final double bidPrice;
	public final long bidSize;
	public final double timestamp;
	public Quote(String contract, double askPrice, long askSize, double bidPrice, long bidSize, double timestamp){
		this.contract = contract;
		this.askPrice = askPrice;
		this.askSize = askSize;
		this.bidPrice = bidPrice;
		this.bidSize = bidSize;
		this.timestamp = timestamp;
	}

	public String getContract(){return this.contract;}
	public double getAskPrice(){return this.askPrice;}
	public long getAskSize(){return this.askSize;}
	public double getBidPrice(){return this.bidPrice;}
	public long getBidSize(){return this.bidSize;}
	public double getTimestamp(){return this.timestamp;}

	public boolean equals(Object obj){
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (getClass() != obj.getClass())
			return false;

		Quote other = (Quote) obj;

		return this.contract == other.contract
				&& this.askPrice == other.askPrice
				&& this.askSize == other.askSize
				&& this.bidPrice == other.bidPrice
				&& this.bidSize == other.bidSize
				&& this.timestamp == other.timestamp;
	}

	public int hashCode(){
		return contract.hashCode() ^ Double.hashCode(askPrice) ^ Long.hashCode(askSize) ^ Double.hashCode(bidPrice) ^ Long.hashCode(bidSize) ^ Double.hashCode(timestamp);
	}

	private static String formatContract(String functionalContract){
		//Transform from server format to normal format
		//From this: AAPL_201016C100.00 or ABC_201016C100.003
		//To this:   AAPL__201016C00100000 or ABC___201016C00100003
		char[] contractChars = new char[]{'_','_','_','_','_','_','2','2','0','1','0','1','C','0','0','0','0','0','0','0','0'};
		int underscoreIndex = functionalContract.indexOf('_');

		//copy symbol
		functionalContract.getChars(0, underscoreIndex, contractChars, 0);

		//copy date
		functionalContract.getChars(underscoreIndex + 1, underscoreIndex + 7, contractChars, 6);

		//copy put/call
		functionalContract.getChars(underscoreIndex + 7, underscoreIndex + 8, contractChars, 12);

		int decimalIndex = functionalContract.indexOf('.', 9);

		//whole number copy
		functionalContract.getChars(underscoreIndex + 8, decimalIndex, contractChars, 18 - (decimalIndex - underscoreIndex - 8));

		//decimal number copy
		functionalContract.getChars(decimalIndex + 1, functionalContract.length(), contractChars, 18);

		return new String(contractChars);
	}

	public float getStrikePrice() {
		int whole = (this.contract.charAt(13) - '0') * 10000 + (this.contract.charAt(14) - '0') * 1000 + (this.contract.charAt(15) - '0') * 100 + (this.contract.charAt(16) - '0') * 10 + (this.contract.charAt(17) - '0');
		float part = (this.contract.charAt(18) - '0') * 0.1f + (this.contract.charAt(19) - '0') * 0.01f + (this.contract.charAt(20) - '0') * 0.001f;
		return (whole + part);
	}

	public boolean isPut() {
		return this.contract.charAt(12) == 'P';
	}

	public boolean isCall() {
		return this.contract.charAt(12) == 'C';
	}

	public ZonedDateTime getExpirationDate() {
		int year = 2000 + (this.contract.charAt(6) - '0') * 10 + (this.contract.charAt(7) - '0');
		int month = (this.contract.charAt(8) - '0') * 10 + (this.contract.charAt(9) - '0');
		int day = (this.contract.charAt(10) - '0') * 10 + (this.contract.charAt(11) - '0');
		ZoneId tz = ZoneId.of("America/New_York");
		return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, tz);
	}

	public String getUnderlyingSymbol() {
		int i;
		for (i = 5; i >= 0 && this.contract.charAt(i) == '_'; i--);
		return this.contract.substring(0,i+1);
	}
	
	public String toString() {
		return String.format("Quote (Contract: %s, AskPrice: %s, AskSize: %s, BidPrice: %s, BidSize: %s, Timestamp: %s)",
				this.contract,
				this.askPrice,
				this.askSize,
				this.bidPrice,
				this.bidSize,
				this.timestamp);
	}

	public static Quote parse(byte[] bytes) {
		//byte structure:
		// contract length [0]
		// contract [1-21]
		// event type [22]
		// price type [23]
		// ask price [24-27]
		// ask size [28-31]
		// bid price [32-35]
		// bid size [36-39]
		// timestamp [40-47]

		String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

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

		return new Quote(Quote.formatContract(contract), askPrice, askSize, bidPrice, bidSize, timestamp);
	}
}
