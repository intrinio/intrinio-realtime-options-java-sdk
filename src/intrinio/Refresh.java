package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Refresh (String contract, long openInterest, double openPrice, double closePrice, double highPrice, double lowPrice){
    public float getStrikePrice() {
        return Float.parseFloat(this.contract.substring(this.contract.indexOf('_') + 8));
    }

    public boolean isPut() { return this.contract.charAt(this.contract.indexOf('_') + 7) == 'P'; }

    public boolean isCall() {
        return this.contract.charAt(this.contract.indexOf('_') + 7) == 'C';
    }

    public ZonedDateTime getExpirationDate() {
        int dateStartIndex = this.contract.indexOf('_') + 1;
        int year = 2000 + (this.contract.charAt(dateStartIndex) - '0') * 10 + (this.contract.charAt(dateStartIndex + 1) - '0');
        int month = (this.contract.charAt(dateStartIndex + 2) - '0') * 10 + (this.contract.charAt(dateStartIndex + 3) - '0');
        int day = (this.contract.charAt(dateStartIndex + 4) - '0') * 10 + (this.contract.charAt(dateStartIndex + 5) - '0');
        ZoneId tz = ZoneId.of("America/New_York");
        return ZonedDateTime.of(year, month, day, 12, 0, 0, 0, tz);
    }

    public String getUnderlyingSymbol() { return this.contract.substring(0, this.contract.indexOf('_')).trim(); }

    public String toString() {
        return String.format("Quote (Contract: %s, OpenInterest: %s, OpenPrice: %s, ClosePrice: %s, HighPrice: %s, LowPrice: %s)",
                this.contract,
                this.openInterest,
                this.openPrice,
                this.closePrice,
                this.highPrice,
                this.lowPrice);
    }

    public static Refresh parse(byte[] bytes) {
        //byte structure:
        // contract length [0]
        // contract [1-21]
        // event type [22]
        // price type [23]
        // open interest [24-27]
        // open price [28-31]
        // close price [32-35]
        // high price [36-39]
        // low price [40-43]

        String contract = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 1, bytes[0])).toString();

        PriceType scaler = PriceType.fromInt(bytes[23]);

        ByteBuffer openInterestBuffer = ByteBuffer.wrap(bytes, 24, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        long openInterest = Integer.toUnsignedLong(openInterestBuffer.getInt());

        ByteBuffer openPriceBuffer = ByteBuffer.wrap(bytes, 28, 4);
        openPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double openPrice = scaler.getScaledValue(openPriceBuffer.getInt());

        ByteBuffer closePriceBuffer = ByteBuffer.wrap(bytes, 32, 4);
        closePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double closePrice = scaler.getScaledValue(closePriceBuffer.getInt());

        ByteBuffer highPriceBuffer = ByteBuffer.wrap(bytes, 36, 4);
        highPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double highPrice = scaler.getScaledValue(highPriceBuffer.getInt());

        ByteBuffer lowPriceBuffer = ByteBuffer.wrap(bytes, 40, 4);
        lowPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double lowPrice = scaler.getScaledValue(lowPriceBuffer.getInt());

        return new Refresh(contract, openInterest, openPrice, closePrice, highPrice, lowPrice);
    }

    public static Refresh parse(ByteBuffer bytes) {
        //byte structure:
        // contract length [0]
        // contract [1-21]
        // event type [22]
        // price type [23]
        // open interest [24-27]
        // open price [28-31]
        // close price [32-35]
        // high price [36-39]
        // low price [40-43]

        String contract = StandardCharsets.US_ASCII.decode(bytes.slice(1, bytes.get(0))).toString();

        PriceType scaler = PriceType.fromInt(bytes.get(23));

        ByteBuffer openInterestBuffer = bytes.slice(24, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        long openInterest = Integer.toUnsignedLong(openInterestBuffer.getInt());

        ByteBuffer openPriceBuffer = bytes.slice(28, 4);
        openPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double openPrice = scaler.getScaledValue(openPriceBuffer.getInt());

        ByteBuffer closePriceBuffer = bytes.slice(32, 4);
        closePriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double closePrice = scaler.getScaledValue(closePriceBuffer.getInt());

        ByteBuffer highPriceBuffer = bytes.slice(36, 4);
        highPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double highPrice = scaler.getScaledValue(highPriceBuffer.getInt());

        ByteBuffer lowPriceBuffer = bytes.slice(40, 4);
        lowPriceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double lowPrice = scaler.getScaledValue(lowPriceBuffer.getInt());

        return new Refresh(contract, openInterest, openPrice, closePrice, highPrice, lowPrice);
    }
}
