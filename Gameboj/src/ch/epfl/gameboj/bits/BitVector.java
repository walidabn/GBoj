package ch.epfl.gameboj.bits;

import static ch.epfl.gameboj.Preconditions.checkArgument;
import static java.lang.Math.floorDiv;
import static java.lang.Math.floorMod;
import static java.util.Objects.checkIndex;

import java.util.Arrays;

public final class BitVector {
    private static final int CHUNK_SIZE = Integer.SIZE;

    private final int[] chunks;

    private BitVector(int[] chunks) {
        this.chunks = chunks;
    }

    public BitVector(int size, boolean initialBits) {
        this(newArray(size, initialBits));
    }

    public BitVector(int size) {
        this(newArray(size, false));
    }

    private static int[] newArray(int size, boolean initialValue) {
        checkArgument(size > 0 && size % CHUNK_SIZE == 0);

        int[] a = new int[size / CHUNK_SIZE];
        Arrays.fill(a, initialValue ? ~0 : 0);
        return a;
    }

    public int size() {
        return chunks.length * CHUNK_SIZE;
    }

    public boolean testBit(int b) {
        checkIndex(b, size());
        return Bits.test(chunks[b / CHUNK_SIZE], b % CHUNK_SIZE);
    }

    public BitVector not() {
        int[] chunks1 = new int[chunks.length];
        for (int i = 0; i < chunks1.length; ++i)
            chunks1[i] = ~chunks[i];
        return new BitVector(chunks1);
    }

    public BitVector and(BitVector that) {
        checkArgument(that.size() == this.size());

        int[] chunks1 = new int[chunks.length];
        for (int i = 0; i < chunks1.length; ++i)
            chunks1[i] = this.chunks[i] & that.chunks[i];
        return new BitVector(chunks1);
    }

    public BitVector or(BitVector that) {
        checkArgument(that.size() == this.size());

        int[] chunks1 = new int[chunks.length];
        for (int i = 0; i < chunks1.length; ++i)
            chunks1[i] = this.chunks[i] | that.chunks[i];
        return new BitVector(chunks1);
    }

    public BitVector extractZeroExtended(int start, int size) {
        checkArgument(size > 0 && size % CHUNK_SIZE == 0);
        return extract(start, size, ExtensionMode.Zero);
    }

    public BitVector extractWrapped(int start, int size) {
        checkArgument(size > 0 && size % CHUNK_SIZE == 0);
        return extract(start, size, ExtensionMode.Wrap);
    }

    private BitVector extract(int start, int size, ExtensionMode mode) {
        int chkO = floorDiv(start, CHUNK_SIZE);
        int bitO = floorMod(start, CHUNK_SIZE);
        int[] chunks1 = new int[size / CHUNK_SIZE];
        if (bitO == 0) {
            for (int i = 0; i < chunks1.length; ++i)
                chunks1[i] = getChunk(mode, chkO + i);
        } else {
            for (int i = 0; i < chunks1.length; ++i) {
                int h = getChunk(mode, chkO + i + 1);
                int l = getChunk(mode, chkO + i);
                chunks1[i] = (h << (CHUNK_SIZE - bitO)) | (l >>> bitO);
            }
        }
        return new BitVector(chunks1);
    }

    private enum ExtensionMode { Zero, Wrap }

    private int getChunk(ExtensionMode m, int i) {
        switch (m) {
        case Zero:
            return (0 <= i && i < chunks.length) ? chunks[i] : 0;
        case Wrap:
            return chunks[floorMod(i, chunks.length)];
        default:
            throw new Error();
        }
    }

    public BitVector shift(int distance) {
        return distance == 0 ? this : extractZeroExtended(-distance, size());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(chunks);
    }

    @Override
    public boolean equals(Object thatO) {
        return (thatO instanceof BitVector)
                && Arrays.equals(chunks, ((BitVector)thatO).chunks);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(size());
        for (int i = 0; i < size(); ++i)
            b.append(testBit(i) ? '1' : '0');
        return b.reverse().toString();
    }

    public static final class Builder {
        private static final int BYTES_PER_CHUNK = CHUNK_SIZE / Byte.SIZE;

        private int[] chunks;

        public Builder(int size) {
            checkArgument(size > 0 && size % CHUNK_SIZE == 0);
            this.chunks = new int[size / CHUNK_SIZE];
        }

        public Builder setByte(int index, int value) {
            if (chunks == null)
                throw new IllegalStateException();
            checkIndex(index, chunks.length * BYTES_PER_CHUNK);

            int chunkI = index / BYTES_PER_CHUNK;
            int byteI = index % BYTES_PER_CHUNK;
            int shift = byteI * Byte.SIZE;
            int mask = 0xFF << shift;
            chunks[chunkI] = (chunks[chunkI] & ~mask) | (value << shift);
            return this;
        }

        public BitVector build() {
            if (chunks == null)
                throw new IllegalStateException();

            BitVector bitVector = new BitVector(chunks);
            chunks = null;
            return bitVector;
        }
    }
}
