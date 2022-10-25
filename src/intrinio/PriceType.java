package intrinio;

enum PriceType {
    One                 (0x00),
    Ten                 (0x01),
    Hundred             (0x02),
    Thousand            (0x03),
    TenThousand         (0x04),
    HundredThousand     (0x05),
    Million             (0x06),
    TenMillion          (0x07),
    HundredMillion      (0x08),
    Billion             (0x09),
    FiveHundredTwelve   (0x0A),
    Zero                (0x0F);

    private final int typeIndex;
    PriceType(int typeIndex) {
        this.typeIndex = typeIndex;
    }

    static PriceType fromInt(int b){
        switch (b){
            case 0x00:
                return One;
            case 0x01:
                return Ten;
            case 0x02:
                return Hundred;
            case 0x03:
                return Thousand;
            case 0x04:
                return TenThousand;
            case 0x05:
                return HundredThousand;
            case 0x06:
                return Million;
            case 0x07:
                return TenMillion;
            case 0x08:
                return HundredMillion;
            case 0x09:
                return Billion;
            case 0x0A:
                return FiveHundredTwelve;
            case 0x0F:
                return Zero;
            default:
                return One;
        }
    }

    long getScale() {
        switch (this){
            case One:
                return 1L;
            case Ten:
                return 10L;
            case Hundred:
                return 100L;
            case Thousand:
                return 1_000L;
            case TenThousand:
                return 10_000L;
            case HundredThousand:
                return 100_000L;
            case Million:
                return 1_000_000L;
            case TenMillion:
                return 10_000_000L;
            case HundredMillion:
                return 100_000_000L;
            case Billion:
                return 1_000_000_000L;
            case FiveHundredTwelve:
                return 512L;
            case Zero:
                return 0L;
            default:
                return 1L;
        }
    }

    double getScaledValue(int value){
        return ((double) value) / ((double) getScale());
    }

    double getScaledValue(long value){
        return ((double) value) / ((double) getScale());
    }
}
