package ch.epfl.gameboj.component;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;
import static java.util.Objects.requireNonNull;

import ch.epfl.gameboj.AddressMap;
import ch.epfl.gameboj.Register;
import ch.epfl.gameboj.RegisterFile;
import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.Bits;
import ch.epfl.gameboj.component.cpu.Cpu;

public final class Timer implements Component, Clocked {
    private enum Reg implements Register { TIMA, TMA, TAC };
    private enum TacBits implements Bit { TAC_0, TAC_1, ENABLE };
    private final static int[] COUNTER_BIT = { 9, 3, 5, 7 };

    private final Cpu cpu;

    private int counter;
    private RegisterFile<Reg> regs;

    public Timer(Cpu cpu) {
        this.regs = new RegisterFile<>(Reg.values());
        this.cpu = requireNonNull(cpu);
    }

    @Override
    public int read(int address) {
        switch (checkBits16(address)) {
        case AddressMap.REG_DIV: return Bits.extract(counter, 8, 8);
        case AddressMap.REG_TIMA: return regs.get(Reg.TIMA);
        case AddressMap.REG_TMA: return regs.get(Reg.TMA);
        case AddressMap.REG_TAC: return regs.get(Reg.TAC);
        default: return Component.NO_DATA;
        }
    }

    @Override
    public void write(int address, int data) {
        checkBits8(data);
        switch (checkBits16(address)) {
        case AddressMap.REG_DIV: {
            boolean s0 = state();
            counter = 0;
            incIfChange(s0);
        } break;
        case AddressMap.REG_TIMA:
            regs.set(Reg.TIMA, data);
            break;
        case AddressMap.REG_TMA:
            regs.set(Reg.TMA, data);
            break;
        case AddressMap.REG_TAC: {
            boolean s0 = state();
            regs.set(Reg.TAC, data);
            incIfChange(s0);
        } break;
        }
    }

    @Override
    public void cycle(long cycle) {
        boolean s0 = state();
        counter += 4;
        incIfChange(s0);
    }

    private void incIfChange(boolean state0) {
        if (state0 && ! state()) {
            if (regs.get(Reg.TIMA) == 0xFF) {
                regs.set(Reg.TIMA, regs.get(Reg.TMA));
                cpu.requestInterrupt(Cpu.Interrupt.TIMER);
            } else
                regs.set(Reg.TIMA, regs.get(Reg.TIMA) + 1);
        }
    }

    private boolean state() {
        return regs.testBit(Reg.TAC, TacBits.ENABLE)
                & Bits.test(counter, COUNTER_BIT[Bits.extract(regs.get(Reg.TAC), 0, 2)]);
    }
}
