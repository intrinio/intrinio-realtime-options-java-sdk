package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Trade {
	private final String contract;
	private final double price;
	private final long size;
	private final double timestamp;
	private final long totalVolume;
	private final double askPriceAtExecution;
	private final double bidPriceAtExecution;
	private final double underlyingPriceAtExecution;

	public Trade(String contract, double price, long size, double timestamp, long totalVolume, double askPriceAtExecution, double bidPriceAtExecution, double underlyingPriceAtExecution){
		this.contract = contract;
		this.price = price;
		this.size = size;
		this.timestamp = timestamp;
		this.totalVolume = totalVolume;
		this.askPriceAtExecution = askPriceAtExecution;
		this.bidPriceAtExecution = bidPriceAtExecution;
		this.underlyingPriceAtExecution = underlyingPriceAtExecution;
	}

	public String getContract(){return this.contract;}
	public double getPrice(){return this.price;}
	public long getSize(){return this.size;}
	public double getTimestamp(){return this.timestamp;}
	public long getTotalVolume(){return this.totalVolume;}
	public double getAskPriceAtExecution(){return this.askPriceAtExecution;}
	public double getBidPriceAtExecution(){return this.bidPriceAtExecution;}
	public double getUnderlyingPriceAtExecution(){return this.underlyingPriceAtExecution;}

	public boolean equals(Object obj){
		if (this == obj)
			return true;

		if (obj == null)
			return false;

		if (getClass() != obj.getClass())
			return false;

		Trade other = (Trade)obj;

		return this.contract == other.contract
				&& this.price == other.price
				&& this.size == other.size
				&& this.timestamp == other.timestamp
				&& this.totalVolume == other.totalVolume
				&& this.askPriceAtExecution == other.askPriceAtExecution
				&& this.bidPriceAtExecution == other.bidPriceAtExecution
				&& this.underlyingPriceAtExecution == other.underlyingPriceAtExecution;
	}

	public int hashCode(){
		return contract.hashCode() ^ Double.hashCode(price) ^ Long.hashCode(size) ^ Double.hashCode(timestamp) ^ Long.hashCode(totalVolume) ^ Double.hashCode(askPriceAtExecution) ^ Double.hashCode(bidPriceAtExecution) ^ Double.hashCode(underlyingPriceAtExecution);
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
		return String.format("Quote (Contract: %s, Price: %s, Size: %s, Timestamp: %s, TotalVolume: %s, AskPriceAtExecution: %s, BidPriceAtExecution: %s, UnderlyingPriceAtExecution: %s)",
				this.contract,
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
		// contract length [0]
		// contract [1-21]
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

		String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

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
		
		return new Trade(Trade.formatContract(contract), price, size, timestamp, totalVolume, askPriceAtExecution, bidPriceAtExecution, underlyingPriceAtExecution);
	}
}