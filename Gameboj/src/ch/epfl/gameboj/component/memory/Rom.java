package ch.epfl.gameboj.component.memory;

import java.util.Arrays;

public final class Rom {
    private final byte[] data;

    public Rom(byte[] data) {
        this.data = Arrays.copyOf(data, data.length);
    }

    public int size() {
        return data.length;
    }

    public int read(int index) {
        return Byte.toUnsignedInt(data[index]);
    }
}
