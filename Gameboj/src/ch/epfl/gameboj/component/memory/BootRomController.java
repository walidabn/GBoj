package ch.epfl.gameboj.component.memory;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;
import static java.util.Objects.requireNonNull;

import ch.epfl.gameboj.AddressMap;
import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.cartridge.Cartridge;

public final class BootRomController implements Component {
    private final Cartridge cartridge;
    private final Rom bootRom;
    private boolean bootRomDisabled;

    public BootRomController(Cartridge cartridge) {
        this.cartridge = requireNonNull(cartridge);
        this.bootRom = new Rom(BootRom.DATA);
        this.bootRomDisabled = false;
    }

    @Override
    public int read(int address) {
        checkBits16(address);
        if (! bootRomDisabled && AddressMap.BOOT_ROM_START <= address && address < AddressMap.BOOT_ROM_END)
            return bootRom.read(address - AddressMap.BOOT_ROM_START);
        else
            return cartridge.read(address);
    }

    @Override
    public void write(int address, int data) {
        if (! bootRomDisabled && address == AddressMap.REG_BOOT_ROM_DISABLE)
            bootRomDisabled = true;
        cartridge.write(checkBits16(address), checkBits8(data));
    }
}
