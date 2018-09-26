package ch.epfl.gameboj;

import static ch.epfl.gameboj.Preconditions.checkBits8;

import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.Bits;

public final class RegisterFile<E extends Register> {
    private final byte[] regs;

    public RegisterFile(E[] allRegs) {
        regs = new byte[allRegs.length];
    }

    public int get(E reg) {
        return Byte.toUnsignedInt(regs[reg.index()]);
    }

    public void set(E reg, int newValue) {
        regs[reg.index()] = (byte)checkBits8(newValue);
    }

    public boolean testBit(E reg, Bit b) {
        return Bits.test(get(reg), b.index());
    }

    public void setBit(E reg, Bit bit, boolean newValue) {
        set(reg, Bits.set(get(reg), bit.index(), newValue));
    }
}
