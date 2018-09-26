package ch.epfl.gameboj.component.cartridge;

import static ch.epfl.gameboj.Preconditions.checkArgument;
import static ch.epfl.gameboj.Preconditions.checkBits16;

import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.memory.Rom;

public final class MBC0 implements Component {
    private final static int ROM_SIZE = 0x8000;
    
    private final Rom rom;

    public MBC0(Rom rom) {
        checkArgument(rom.size() == ROM_SIZE);
        this.rom = rom;
    }

    @Override
    public int read(int address) {
        return checkBits16(address) < ROM_SIZE ? rom.read(address) : NO_DATA;
    }

    @Override
    public void write(int address, int data) { }
}
