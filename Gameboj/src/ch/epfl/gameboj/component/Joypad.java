package ch.epfl.gameboj.component;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;

import ch.epfl.gameboj.AddressMap;
import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.Bits;
import ch.epfl.gameboj.component.cpu.Cpu;
import ch.epfl.gameboj.component.cpu.Cpu.Interrupt;

public final class Joypad implements Component {
    public enum Key {
        RIGHT, LEFT, UP, DOWN,
        A, B, SELECT, START;

        public int row() { return Bits.extract(ordinal(), 2, 1); }
        public int col() { return Bits.extract(ordinal(), 0, 2); }
    }

    private enum P1Bit implements Bit {
        COL_0, COL_1, COL_2, COL_3, SELECT_ROW_0, SELECT_ROW_1
    }
    private static final int ROWS = 2;
    private static final P1Bit[] SELECTION_BITS =
            new P1Bit[] { P1Bit.SELECT_ROW_0, P1Bit.SELECT_ROW_1 };
    private static final int SELECTION_MASK =
            P1Bit.SELECT_ROW_0.mask() | P1Bit.SELECT_ROW_1.mask();

    private final Cpu cpu;

    private int[] pressedKeys;
    private int selectedRows;

    public Joypad(Cpu cpu) {
        this.cpu = cpu;
        this.pressedKeys = new int[ROWS];
    }

    @Override
    public int read(int address) {
        if (checkBits16(address) == AddressMap.REG_P1)
            return Bits.complement8(combinedPressedKeys() | selectedRows);
        else
            return NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits8(data);
        if (checkBits16(address) == AddressMap.REG_P1) {
            int combinedPressedKeys0 = combinedPressedKeys();
            selectedRows = Bits.complement8(data) & SELECTION_MASK;
            interruptIfChange(combinedPressedKeys0);
        }
    }

    public void keyPressed(Key k) {
        int combinedPressedKeys0 = combinedPressedKeys();
        pressedKeys[k.row()] = Bits.set(pressedKeys[k.row()], k.col(), true);
        interruptIfChange(combinedPressedKeys0);
    }

    public void keyReleased(Key k) {
        pressedKeys[k.row()] = Bits.set(pressedKeys[k.row()], k.col(), false);
    }

    private void interruptIfChange(int combinedPressedKeys0) {
        if ((~combinedPressedKeys0 & combinedPressedKeys()) != 0)
            cpu.requestInterrupt(Interrupt.JOYPAD);
    }
    
    private int combinedPressedKeys() {
        int res = 0;
        for (int r = 0; r < ROWS; ++r) {
            if (Bits.test(selectedRows, SELECTION_BITS[r]))
                res |= pressedKeys[r];
        }
        return res;
    }
}
