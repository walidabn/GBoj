package ch.epfl.gameboj;

public interface Preconditions {
    public static void checkArgument(boolean b) {
        if (! b)
            throw new IllegalArgumentException();
    }

    public static int checkBits8(int v) {
        checkArgument((v & 0xFF) == v);
        return v;
    }

    public static int checkBits16(int v) {
        checkArgument((v & 0xFFFF) == v);
        return v;
    }
}
