package ch.epfl.gameboj;

public interface Register {
    public int ordinal();

    default public int index() {
        return ordinal();
    }
}
