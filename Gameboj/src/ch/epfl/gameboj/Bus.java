package ch.epfl.gameboj;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;

import ch.epfl.gameboj.component.Component;

public final class Bus {
    private final ArrayList<Component> components = new ArrayList<>();

    public void attach(Component component) {
        components.add(requireNonNull(component));
    }

    public int read(int address) {
        checkBits16(address);

        for (Component c: components) {
            int r = c.read(address);
            if (r != Component.NO_DATA)
                return r;
        }

        return 0xFF;
    }

    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        for (Component component: components)
            component.write(address, data);
    }
}
