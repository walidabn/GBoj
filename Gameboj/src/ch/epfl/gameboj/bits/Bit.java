package ch.epfl.gameboj.bits;

public interface Bit {
    int ordinal();

    default int index() {
        return ordinal();
    }

    default int mask() {
        return Bits.mask(index());
    }
}
