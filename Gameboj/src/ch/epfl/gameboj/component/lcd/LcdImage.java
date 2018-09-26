package ch.epfl.gameboj.component.lcd;

import static ch.epfl.gameboj.Preconditions.checkArgument;
import static java.util.Collections.nCopies;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.checkIndex;

import java.util.ArrayList;
import java.util.List;

import ch.epfl.gameboj.bits.BitVector;

public final class LcdImage {
    private final int width, height;
    private final List<LcdImageLine> lines;

    public LcdImage(int width, int height, List<LcdImageLine> lines) {
        checkArgument(0 < width);
        checkArgument(0 < height);
        checkArgument(lines.size() == height);

        this.width = width;
        this.height = height;
        this.lines = unmodifiableList(new ArrayList<>(lines));
    }

    public int width() { return width; }
    public int height() { return height; }

    public int get(int x, int y) {
        checkIndex(x, width);

        LcdImageLine l = lines.get(checkIndex(y, height));
        int msb = l.msb().testBit(x) ? 0b10 : 0;
        int lsb = l.lsb().testBit(x) ? 0b01 : 0;
        return msb | lsb;
    }

    @Override
    public int hashCode() {
        return lines.hashCode();
    }

    @Override
    public boolean equals(Object thatO) {
        if (thatO instanceof LcdImage) {
            LcdImage that = (LcdImage)thatO;
            return width == that.width
                    && height == that.height
                    && lines.equals(that.lines);
        } else
            return false;
    }

    public static final class Builder {
        private final int width, height;
        private final List<LcdImageLine> lines;

        public Builder(int width, int height) {
            checkArgument(0 < width);
            checkArgument(0 < height);

            BitVector emptyBits = new BitVector(width);
            LcdImageLine emptyLine = new LcdImageLine(emptyBits, emptyBits, emptyBits);

            this.width = width;
            this.height = height;
            this.lines = new ArrayList<>(nCopies(height, emptyLine));
        }

        public Builder setLine(int y, LcdImageLine l) {
            checkIndex(y, height);
            checkArgument(l.size() == width);

            lines.set(y, l);
            return this;
        }

        public LcdImage build() {
            return new LcdImage(width, height, lines);
        }
    }
}
