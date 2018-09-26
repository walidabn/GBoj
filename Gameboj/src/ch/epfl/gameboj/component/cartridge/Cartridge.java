package ch.epfl.gameboj.component.cartridge;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.memory.Rom;

public final class Cartridge implements Component {
    private final static int HEADER_MBC_OFFSET = 0x147;
    private final static int HEADER_RAM_SIZE_OFFSET = 0x149;

    private final static int[] RAM_SIZES = new int[] { 0, 0x800, 0x2000, 0x8000 };

    private final static int ROM_ONLY = 0;
    private final static int MBC_1 = 1;
    private final static int MBC_1_RAM = 2;
    private final static int MBC_1_RAM_BATTERY = 3;

    private final Component mbc;

    public static Cartridge ofFile(File romFile) throws IOException {
        try (InputStream c = new FileInputStream(romFile)) {
            byte[] romData = new byte[(int) romFile.length()];
            int read = c.read(romData);
            if (read != romData.length)
                throw new IOException("unable to read whole file");

            Rom rom = new Rom(romData);
            int mbcType = rom.read(HEADER_MBC_OFFSET);
            switch (mbcType) {
            case ROM_ONLY:
                return new Cartridge(new MBC0(rom));
            case MBC_1:
            case MBC_1_RAM:
            case MBC_1_RAM_BATTERY:
                return new Cartridge(new MBC1(rom, RAM_SIZES[rom.read(HEADER_RAM_SIZE_OFFSET)]));
            default:
                throw new IllegalArgumentException("unexpected MBC type: " + mbcType);
            }
        }
    }

    private Cartridge(Component mbc) {
        this.mbc = mbc;
    }

    @Override
    public int read(int address) {
        return mbc.read(checkBits16(address));
    }

    @Override
    public void write(int address, int data) {
        mbc.write(checkBits16(address), checkBits8(data));
    }
}
