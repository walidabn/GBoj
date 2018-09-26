package ch.epfl.gameboj.component;

import ch.epfl.gameboj.Bus;

public interface Component {
    public static final int NO_DATA = 0x100;

    default public void attachTo(Bus bus) {
        bus.attach(this);
    }
    abstract public int read(int address);
    abstract public void write(int address, int data);
}
