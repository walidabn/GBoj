package ch.epfl.gameboj.component.memory;

import static ch.epfl.gameboj.Preconditions.*;

public final class Ram {
    private final byte[] data;

    public Ram(int size) {
        checkArgument(0 <= size);
        this.data = new byte[size];
    }

    public int size() {
        return data.length;
    }

    public int read(int index) {
        return Byte.toUnsignedInt(data[index]);
    }

    public void write(int index, int value) {
        checkBits8(value);
        data[index] = (byte) value;
    }
}
