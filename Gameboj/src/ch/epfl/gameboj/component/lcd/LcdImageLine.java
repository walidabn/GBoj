package ch.epfl.gameboj.component.lcd;

import static ch.epfl.gameboj.Preconditions.checkArgument;

import java.util.Objects;

import ch.epfl.gameboj.bits.BitVector;
import ch.epfl.gameboj.bits.Bits;

public final class LcdImageLine {
    private final static int IDENTITY_MAP = 0b11_10_01_00;

    private final BitVector msb, lsb, opacity;

    public LcdImageLine(BitVector msb, BitVector lsb, BitVector opacity) {
        checkArgument(msb.size() == lsb.size() && lsb.size() == opacity.size());
        this.msb = msb;
        this.lsb = lsb;
        this.opacity = opacity;
    }

    public int size() { return msb.size(); }

    public BitVector msb() { return msb; }
    public BitVector lsb() { return lsb; }
    public BitVector opacity() { return opacity; }

    public LcdImageLine shift(int amount) {
        if (amount == 0)
            return this;
        else
            return new LcdImageLine(msb.shift(amount), lsb.shift(amount), opacity.shift(amount));
    }

    public LcdImageLine extractWrapped(int start, int size) {
        return new LcdImageLine(
                msb.extractWrapped(start, size),
                lsb.extractWrapped(start, size),
                opacity.extractWrapped(start, size));
    }

    public LcdImageLine mapColors(int map) {
        if (map == IDENTITY_MAP)
            return this;

        BitVector lsbInv = lsb.not();
        BitVector msbInv = msb.not();

        BitVector lsbM = new BitVector(size());
        BitVector msbM = new BitVector(size());

        for (int i = 0; i < 4; ++i) {
            boolean colorBit0 = Bits.test(map, 2 * i);
            boolean colorBit1 = Bits.test(map, 2 * i + 1);

            if (colorBit0 || colorBit1) {
                BitVector lsb1 = (Bits.test(i, 0) ? lsb : lsbInv);
                BitVector msb1 = (Bits.test(i, 1) ? msb : msbInv);
                BitVector mask = lsb1.and(msb1);

                if (colorBit0)
                    lsbM = lsbM.or(mask);
                if (colorBit1)
                    msbM = msbM.or(mask);
            }
        }

        return new LcdImageLine(msbM, lsbM, opacity());
    }

    public LcdImageLine below(LcdImageLine that) {
        return below(that, that.opacity);
    }

    public LcdImageLine below(LcdImageLine that, BitVector op) {
        checkArgument(that.size() == this.size());
        return new LcdImageLine(
                below(msb, that.msb, op),
                below(lsb, that.lsb, op),
                opacity.or(op));
    }

    private static BitVector below(BitVector bg, BitVector fg, BitVector o) {
        return bg.and(o.not()).or(fg.and(o));
    }

    public LcdImageLine join(LcdImageLine that, int firstBit) {
        checkArgument(that.size() == this.size());
        checkArgument(0 <= firstBit && firstBit <= size());

        BitVector thatMask = new BitVector(size(), true).shift(firstBit);
        BitVector thisMask = thatMask.not();
        return new LcdImageLine(
                msb.and(thisMask).or(that.msb.and(thatMask)),
                lsb.and(thisMask).or(that.lsb.and(thatMask)),
                opacity.and(thisMask).or(that.opacity.and(thatMask)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(msb, lsb, opacity);
    }

    @Override
    public boolean equals(Object thatO) {
        if (thatO instanceof LcdImageLine) {
            LcdImageLine that = (LcdImageLine)thatO;
            return msb.equals(that.msb) && lsb.equals(that.lsb) && opacity.equals(that.opacity);
        } else
            return false;
    }

    public static class Builder {
        private final BitVector.Builder lsbB, msbB;

        public Builder(int size) {
            checkArgument(0 < size);

            this.msbB = new BitVector.Builder(size);
            this.lsbB = new BitVector.Builder(size);
        }

        public Builder setBytes(int byteIndex, int msbValue, int lsbValue) {
            msbB.setByte(byteIndex, msbValue);
            lsbB.setByte(byteIndex, lsbValue);
            return this;
        }

        public LcdImageLine build() {
            BitVector msb = msbB.build(), lsb = lsbB.build();
            return new LcdImageLine(msb, lsb, msb.or(lsb));
        }
    }
}
