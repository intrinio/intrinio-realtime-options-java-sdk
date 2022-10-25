package intrinio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record Refresh (String symbol, int openInterest, double timestamp){
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
        return "Refresh (" + this.symbol + "): " + this.openInterest;
    }

    static int getMessageSize(){
        return 42;
    }

    public static Refresh parse(byte[] bytes) {
        String symbol = StandardCharsets.US_ASCII.decode(ByteBuffer.wrap(bytes, 0, 21)).toString();

        ByteBuffer openInterestBuffer = ByteBuffer.wrap(bytes, 22, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int openInterest = openInterestBuffer.getInt();

        ByteBuffer timeStampBuffer = ByteBuffer.wrap(bytes, 26, 8);
        timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double timestamp = timeStampBuffer.getDouble();

        return new Refresh(symbol, openInterest, timestamp);
    }

    public static Refresh parse(ByteBuffer bytes) {

        String symbol = StandardCharsets.US_ASCII.decode(bytes.slice(0, 21)).toString();

        ByteBuffer openInterestBuffer = bytes.slice(22, 4);
        openInterestBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int openInterest = openInterestBuffer.getInt();

        ByteBuffer timeStampBuffer = bytes.slice(26, 8);
        timeStampBuffer.order(ByteOrder.LITTLE_ENDIAN);
        double timestamp = timeStampBuffer.getDouble();

        return new Refresh(symbol, openInterest, timestamp);
    }
}
