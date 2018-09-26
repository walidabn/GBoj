package ch.epfl.gameboj.component.memory;

import static ch.epfl.gameboj.Preconditions.checkArgument;
import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;
import static java.util.Objects.requireNonNull;

import ch.epfl.gameboj.component.Component;

public final class RamController implements Component {
    private final int startAddress, endAddress;
    private final Ram ram;

    public RamController(Ram ram, int startAddress, int endAddress) {
        int size = checkBits16(endAddress) - checkBits16(startAddress);
        checkArgument(0 <= size && size <= ram.size());
        this.ram = requireNonNull(ram);
        this.startAddress = startAddress;
        this.endAddress = endAddress;
    }

    public RamController(Ram ram, int startAddress) {
        this(ram, startAddress, startAddress + ram.size());
    }

    @Override
    public int read(int address) {
        checkBits16(address);
        if (startAddress <= address && address < endAddress)
            return ram.read(address - startAddress);
        else
            return NO_DATA;
    }

    @Override
    public void write(int address, int b) {
        checkBits16(address);
        checkBits8(b);
        if (startAddress <= address && address < endAddress)
            ram.write(address - startAddress, b);
    }
}
