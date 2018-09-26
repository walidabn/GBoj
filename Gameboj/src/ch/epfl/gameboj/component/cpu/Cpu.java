package ch.epfl.gameboj.component.cpu;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;

import ch.epfl.gameboj.AddressMap;
import ch.epfl.gameboj.Bus;
import ch.epfl.gameboj.Register;
import ch.epfl.gameboj.RegisterFile;
import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.Bits;
import ch.epfl.gameboj.component.Clocked;
import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.cpu.Alu.Flag;
import ch.epfl.gameboj.component.cpu.Alu.RotDir;
import ch.epfl.gameboj.component.memory.Ram;

public final class Cpu implements Component, Clocked {
    public enum Interrupt implements Bit {
        VBLANK, LCD_STAT, TIMER, SERIAL, JOYPAD;
    }

    private enum Reg implements Register {
        A, F, B, C, D, E, H, L
    }
    private enum Reg16 {
        AF(Reg.A, Reg.F), BC(Reg.B, Reg.C), DE(Reg.D, Reg.E), HL(Reg.H, Reg.L);

        private final Reg h, l;

        private Reg16(Reg h, Reg l) {
            this.h = h;
            this.l = l;
        }
    }

    private static final Opcode[] DIRECT_OPCODE_TABLE = buildOpcodeTable(Opcode.Kind.DIRECT);
    private static final Opcode[] PREFIXED_OPCODE_TABLE = buildOpcodeTable(Opcode.Kind.PREFIXED);
    private static final int OPCODE_PREFIX = 0xCB;

    private static Opcode[] buildOpcodeTable(Opcode.Kind k) {
        Opcode[] table = new Opcode[256];
        for (Opcode opcode: Opcode.values()) {
            if (opcode.kind == k)
                table[opcode.encoding] = opcode;
        }
        return table;
    }

    private Bus bus;

    private final Ram highRAM;

    private int regPC;
    private int regSP;
    private final RegisterFile<Reg> regs;

    private boolean flagIME;
    private int regIE, regIF;

    private long nextNonIdleCycle;

    public Cpu() {
        this.highRAM = new Ram(AddressMap.HIGH_RAM_SIZE);

        this.regs = new RegisterFile<>(Reg.values());
    }

    @Override
    public void attachTo(Bus bus) {
        this.bus = bus;
        Component.super.attachTo(bus);
    }

    public void requestInterrupt(Interrupt i) {
        regIF = Bits.set(regIF, i.index(), true);
    }

    @Override
    public int read(int address) {
        checkBits16(address);

        if (AddressMap.HIGH_RAM_START <= address && address < AddressMap.HIGH_RAM_END)
            return highRAM.read(address - AddressMap.HIGH_RAM_START);
        else if (address == AddressMap.REG_IE)
            return regIE;
        else if (address == AddressMap.REG_IF)
            return regIF;
        else
            return Component.NO_DATA;
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (AddressMap.HIGH_RAM_START <= address && address < AddressMap.HIGH_RAM_END)
            highRAM.write(address - AddressMap.HIGH_RAM_START, data);
        else if (address == AddressMap.REG_IE)
            regIE = data;
        else if (address == AddressMap.REG_IF)
            regIF = data;
    }

    @Override
    public void cycle(long cycle) {
        assert cycle <= nextNonIdleCycle;
        if (cycle == nextNonIdleCycle)
            reallyCycle();
        else if (nextNonIdleCycle == Long.MAX_VALUE && pendingInterrupt()) {
            nextNonIdleCycle = cycle;
            reallyCycle();
        }
    }

    private void reallyCycle() {
        if (flagIME && pendingInterrupt()) {
            int intIndex = 31 - Integer.numberOfLeadingZeros(Integer.lowestOneBit(regIE & regIF));
            regIF = Bits.set(regIF, intIndex, false);
            push16(regPC);
            flagIME = false;
            regPC = AddressMap.INTERRUPTS[intIndex];
            nextNonIdleCycle += 5;
        } else {
            int o0 = read8(regPC);
            dispatch(o0 != OPCODE_PREFIX ? DIRECT_OPCODE_TABLE[o0] : PREFIXED_OPCODE_TABLE[read8AfterOpcode()]);
        }
    }

    private boolean pendingInterrupt() {
        return (regIE & regIF) != 0;
    }

    public int[] _testGetPcSpAFBCDEHL() {
        return new int[] {
                regPC, regSP,
                regs.get(Reg.A), regs.get(Reg.F),
                regs.get(Reg.B), regs.get(Reg.C),
                regs.get(Reg.D), regs.get(Reg.E),
                regs.get(Reg.H), regs.get(Reg.L),
        };
    }

    private void dispatch(Opcode op) {
        int nextPc = regPC + op.totalBytes;
        nextNonIdleCycle += op.cycles;

        switch (op.family) {
        case NOP: {
            // Nothing to do
        } break;

        // Load
        case LD_R8_HLR: {
            regs.set(extractReg(op, 3), read8AtHl());
        } break;
        case LD_A_HLRU: {
            regs.set(Reg.A, read8AtHl());
            setReg16(Reg16.HL, Bits.clip(16, reg16(Reg16.HL) + extractHlIncrement(op)));
        } break;
        case LD_A_N8R: {
            regs.set(Reg.A, read8(AddressMap.REGS_START + read8AfterOpcode()));
        } break;
        case LD_A_CR: {
            regs.set(Reg.A, read8(AddressMap.REGS_START + regs.get(Reg.C)));
        } break;
        case LD_A_N16R: {
            regs.set(Reg.A, read8(read16AfterOpcode()));
        } break;
        case LD_A_BCR: {
            regs.set(Reg.A, read8(reg16(Reg16.BC)));
        } break;
        case LD_A_DER: {
            regs.set(Reg.A, read8(reg16(Reg16.DE)));
        } break;
        case LD_R8_N8: {
            regs.set(extractReg(op, 3), read8AfterOpcode());
        } break;
        case LD_R16SP_N16: {
            setReg16SP(extractReg16(op), read16AfterOpcode());
        } break;
        case POP_R16: {
            setReg16(extractReg16(op), pop16());
        } break;

        // Store
        case LD_HLR_R8: {
            write8AtHl(regs.get(extractReg(op, 0)));
        } break;
        case LD_HLRU_A: {
            write8AtHl(regs.get(Reg.A));
            setReg16(Reg16.HL, Bits.clip(16, reg16(Reg16.HL) + extractHlIncrement(op)));
        } break;
        case LD_N8R_A: {
            write8(AddressMap.REGS_START + read8AfterOpcode(), regs.get(Reg.A));
        } break;
        case LD_CR_A: {
            write8(AddressMap.REGS_START + regs.get(Reg.C), regs.get(Reg.A));
        } break;
        case LD_N16R_A: {
            write8(read16AfterOpcode(), regs.get(Reg.A));
        } break;
        case LD_BCR_A: {
            write8(reg16(Reg16.BC), regs.get(Reg.A));
        } break;
        case LD_DER_A: {
            write8(reg16(Reg16.DE), regs.get(Reg.A));
        } break;
        case LD_HLR_N8: {
            write8AtHl(read8AfterOpcode());
        } break;
        case LD_N16R_SP: {
            write16(read16AfterOpcode(), regSP);
        } break;
        case PUSH_R16: {
            push16(reg16(extractReg16(op)));
        } break;

        // Move
        case LD_R8_R8: {
            regs.set(extractReg(op, 3), regs.get(extractReg(op, 0)));
        } break;
        case LD_SP_HL: {
            regSP = reg16(Reg16.HL);
        } break;

        // Add
        case ADD_A_R8: {
            setRegFlags(Reg.A, Alu.add(regs.get(Reg.A), regs.get(extractReg(op, 0)), extractC(op)));
        } break;
        case ADD_A_N8: {
            setRegFlags(Reg.A, Alu.add(regs.get(Reg.A), read8AfterOpcode(), extractC(op)));
        } break;
        case ADD_A_HLR: {
            setRegFlags(Reg.A, Alu.add(regs.get(Reg.A), read8AtHl(), extractC(op)));
        } break;
        case INC_R8: {
            Reg r = extractReg(op, 3);
            int vf = Alu.add(regs.get(r), 1);
            setRegFromAlu(r, vf);
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case INC_HLR: {
            int vf = Alu.add(read8AtHl(), 1);
            write8AtHl(Alu.unpackValue(vf));
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case INC_R16SP: {
            Reg16 r = extractReg16(op);
            setReg16SP(r, Bits.clip(16, reg16SP(r) + 1));
        } break;
        case ADD_HL_R16SP: {
            int vf = Alu.add16H(reg16(Reg16.HL), reg16SP(extractReg16(op)));
            setReg16(Reg16.HL, Alu.unpackValue(vf));
            combineAluFlags(vf, FlagSrc.CPU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU);
        } break;
        case LD_HLSP_S8: {
            int vf = Alu.add16L(regSP, Bits.clip(16, readSigned8AfterOpcode()));
            setReg16SP(Bits.test(op.encoding, 4) ? Reg16.HL : Reg16.AF, Alu.unpackValue(vf));
            setFlags(vf);
        } break;

        // Subtract
        case SUB_A_R8: {
            setRegFlags(Reg.A, Alu.sub(regs.get(Reg.A), regs.get(extractReg(op, 0)), extractC(op)));
        } break;
        case SUB_A_N8: {
            setRegFlags(Reg.A, Alu.sub(regs.get(Reg.A), read8AfterOpcode(), extractC(op)));
        } break;
        case SUB_A_HLR: {
            setRegFlags(Reg.A, Alu.sub(regs.get(Reg.A), read8AtHl(), extractC(op)));
        } break;
        case DEC_R8: {
            Reg r = extractReg(op, 3);
            int vf = Alu.sub(regs.get(r), 1);
            setRegFromAlu(r, vf);
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case DEC_HLR: {
            int vf = Alu.sub(read8AtHl(), 1);
            write8AtHl(Alu.unpackValue(vf));
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case CP_A_R8: {
            setFlags(Alu.sub(regs.get(Reg.A), regs.get(extractReg(op, 0))));
        } break;
        case CP_A_N8: {
            setFlags(Alu.sub(regs.get(Reg.A), read8AfterOpcode()));
        } break;
        case CP_A_HLR: {
            setFlags(Alu.sub(regs.get(Reg.A), read8AtHl()));
        } break;
        case DEC_R16SP: {
            Reg16 r = extractReg16(op);
            setReg16SP(r, Bits.clip(16, reg16SP(r) - 1));
        } break;

        // And, or, xor, complement
        case AND_A_N8: {
            setRegFlags(Reg.A, Alu.and(regs.get(Reg.A), read8AfterOpcode()));
        } break;
        case AND_A_R8: {
            setRegFlags(Reg.A, Alu.and(regs.get(Reg.A), regs.get(extractReg(op, 0))));
        } break;
        case AND_A_HLR: {
            setRegFlags(Reg.A, Alu.and(regs.get(Reg.A), read8AtHl()));
        } break;
        case OR_A_R8: {
            setRegFlags(Reg.A, Alu.or(regs.get(Reg.A), regs.get(extractReg(op, 0))));
        } break;
        case OR_A_N8: {
            setRegFlags(Reg.A, Alu.or(regs.get(Reg.A), read8AfterOpcode()));
        } break;
        case OR_A_HLR: {
            setRegFlags(Reg.A, Alu.or(regs.get(Reg.A), read8AtHl()));
        } break;
        case XOR_A_R8: {
            setRegFlags(Reg.A, Alu.xor(regs.get(Reg.A), regs.get(extractReg(op, 0))));
        } break;
        case XOR_A_N8: {
            setRegFlags(Reg.A, Alu.xor(regs.get(Reg.A), read8AfterOpcode()));
        } break;
        case XOR_A_HLR: {
            setRegFlags(Reg.A, Alu.xor(regs.get(Reg.A), read8AtHl()));
        } break;
        case CPL: {
            regs.set(Reg.A, Bits.complement8(regs.get(Reg.A)));
            combineAluFlags(0, FlagSrc.CPU, FlagSrc.V1, FlagSrc.V1, FlagSrc.CPU);
        } break;

        // Rotate, shift
        case ROTCA: {
            int vf = Alu.rotate(extractRotDir(op), regs.get(Reg.A));
            setRegFromAlu(Reg.A, vf);
            combineAluFlags(vf, FlagSrc.V0, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU);
        } break;
        case ROTA: {
            int vf = Alu.rotate(extractRotDir(op), regs.get(Reg.A), test(Flag.C));
            setRegFromAlu(Reg.A, vf);
            combineAluFlags(vf, FlagSrc.V0, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU);
        } break;
        case ROTC_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.rotate(extractRotDir(op), regs.get(reg)));
        } break;
        case ROT_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.rotate(extractRotDir(op), regs.get(reg), test(Flag.C)));
        } break;
        case ROTC_HLR: {
            write8AtHlAndSetFlags(Alu.rotate(extractRotDir(op), read8AtHl()));
        } break;
        case ROT_HLR: {
            write8AtHlAndSetFlags(Alu.rotate(extractRotDir(op), read8AtHl(), test(Flag.C)));
        } break;
        case SWAP_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.swap(regs.get(reg)));
        } break;
        case SWAP_HLR: {
            write8AtHlAndSetFlags(Alu.swap(read8AtHl()));
        } break;
        case SLA_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.shiftLeft(regs.get(reg)));
        } break;
        case SRA_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.shiftRightA(regs.get(reg)));
        } break;
        case SRL_R8: {
            Reg reg = extractReg(op, 0);
            setRegFlags(reg, Alu.shiftRightL(regs.get(reg)));
        } break;
        case SLA_HLR: {
            write8AtHlAndSetFlags(Alu.shiftLeft(read8AtHl()));
        } break;
        case SRA_HLR: {
            write8AtHlAndSetFlags(Alu.shiftRightA(read8AtHl()));
        } break;
        case SRL_HLR: {
            write8AtHlAndSetFlags(Alu.shiftRightL(read8AtHl()));
        } break;

        // Bit test and set
        case BIT_U3_R8: {
            int vf = Alu.testBit(regs.get(extractReg(op, 0)), extractBitIndex(op));
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case BIT_U3_HLR: {
            int vf = Alu.testBit(read8AtHl(), extractBitIndex(op));
            combineAluFlags(vf, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.ALU, FlagSrc.CPU);
        } break;
        case CHG_U3_R8: {
            Reg reg = extractReg(op, 0);
            regs.set(reg, Bits.set(regs.get(reg), extractBitIndex(op), extractBitAction(op)));
        } break;
        case CHG_U3_HLR: {
            write8AtHl(Bits.set(read8AtHl(), extractBitIndex(op), extractBitAction(op)));
        } break;

        // Misc. ALU
        case DAA: {
            setRegFlags(Reg.A, Alu.bcdAdjust(regs.get(Reg.A), test(Flag.N), test(Flag.H), test(Flag.C)));
        } break;
        case SCCF: {
            combineAluFlags(0, FlagSrc.CPU, FlagSrc.V0, FlagSrc.V0, extractC(op) ? FlagSrc.V0 : FlagSrc.V1);
        } break;

        // Jumps
        case JP_HL: {
            nextPc = reg16(Reg16.HL);
        } break;
        case JP_N16: {
            nextPc = read16AfterOpcode();
        } break;
        case JP_CC_N16: {
            if (extractAndTestCC(op)) {
                nextPc = read16AfterOpcode();
                nextNonIdleCycle += op.additionalCycles;
            }
        } break;
        case JR_E8: {
            nextPc += readSigned8AfterOpcode();
        } break;
        case JR_CC_E8: {
            if (extractAndTestCC(op)) {
                nextPc += readSigned8AfterOpcode();
                nextNonIdleCycle += op.additionalCycles;
            }
        } break;

        // Calls and returns
        case CALL_N16: {
            push16(nextPc);
            nextPc = read16AfterOpcode();
        } break;
        case CALL_CC_N16: {
            if (extractAndTestCC(op)) {
                push16(nextPc);
                nextPc = read16AfterOpcode();
                nextNonIdleCycle += op.additionalCycles;
            }
        } break;
        case RST_U3: {
            push16(nextPc);
            nextPc = AddressMap.RESETS[Bits.extract(op.encoding, 3, 3)];
        } break;
        case RET: {
            nextPc = pop16();
        } break;
        case RET_CC: {
            if (extractAndTestCC(op)) {
                nextPc = pop16();
                nextNonIdleCycle += op.additionalCycles;
            }
        } break;

        // Interrupts
        case EDI: {
            flagIME = Bits.test(op.encoding, 3);
        } break;
        case RETI: {
            nextPc = pop16();
            flagIME = true;
        } break;

        // Misc control
        case HALT: {
            nextNonIdleCycle = Long.MAX_VALUE;
        } break;
        case STOP: {
            throw new Error("STOP is not implemented");
        }
        }

        regPC = nextPc;
    }

    private int read8(int address) {
        return bus.read(address);
    }

    private int read8AtHl() {
        return read8(reg16(Reg16.HL));
    }

    private int read8AfterOpcode() {
        return read8(regPC + 1);
    }

    private int readSigned8AfterOpcode() {
        return Bits.signExtend8(read8AfterOpcode());
    }

    private int read16(int address) {
        int low8 = read8(address);
        int high8 = read8(address + 1);
        return Bits.make16(high8, low8);
    }

    private int read16AfterOpcode() {
        return read16(regPC + 1);
    }

    private void write8(int address, int v) {
        bus.write(address, v);
    }

    private void write8AtHl(int v) {
        write8(reg16(Reg16.HL), v);
    }

    private void write16(int address, int v) {
        write8(address + 0, Bits.extract(v, 0, 8));
        write8(address + 1, Bits.extract(v, 8, 8));
    }

    private void push16(int v) {
        regSP = Bits.clip(16, regSP - 2);
        write16(regSP, v);
    }

    private int pop16() {
        int v = read16(regSP);
        regSP = Bits.clip(16, regSP + 2);
        return v;
    }

    private int reg16(Reg16 r) {
        return Bits.make16(regs.get(r.h), regs.get(r.l));
    }

    private static final int LOW8_MASK = 0b1111_1111;
    private static final int ZNHC_MASK = Alu.maskZNHC(true, true, true, true);

    private void setReg16(Reg16 r, int newV) {
        regs.set(r.h, Bits.extract(newV, 8, 8));
        regs.set(r.l, newV & (r.l == Reg.F ? ZNHC_MASK : LOW8_MASK));
    }

    private int reg16SP(Reg16 r) {
        return r == Reg16.AF ? regSP : reg16(r);
    }

    private void setReg16SP(Reg16 r, int newV) {
        if (r == Reg16.AF)
            regSP = newV;
        else
            setReg16(r, newV);
    }

    private void setRegFromAlu(Reg r, int vf) {
        regs.set(r, Alu.unpackValue(vf));
    }

    private boolean test(Flag f) {
        return regs.testBit(Reg.F, f);
    }

    private void setFlags(int valueFlags) {
        regs.set(Reg.F, Alu.unpackFlags(valueFlags));
    }

    private void setRegFlags(Reg r, int vf) {
        setRegFromAlu(r, vf);
        setFlags(vf);
    }

    private void write8AtHlAndSetFlags(int vf) {
        write8AtHl(Alu.unpackValue(vf));
        setFlags(vf);
    }

    private enum FlagSrc { V0, V1, ALU, CPU; };

    private void combineAluFlags(int aluVF, FlagSrc z, FlagSrc n, FlagSrc h, FlagSrc c) {
        int aluF = Alu.unpackFlags(aluVF);
        int set = flagActMaskZNHC(FlagSrc.V1, z, n, h, c);
        int aluMask = flagActMaskZNHC(FlagSrc.ALU, z, n, h, c);
        int cpuMask = flagActMaskZNHC(FlagSrc.CPU, z, n, h, c);
        regs.set(Reg.F, set | (aluF & aluMask) | (regs.get(Reg.F) & cpuMask));
    }

    private static int flagActMaskZNHC(FlagSrc s, FlagSrc z, FlagSrc n, FlagSrc h, FlagSrc c) {
        return Alu.maskZNHC(z == s, n == s, h == s, c == s);
    }

    private static final Reg[] ENCODED_REG = {
            Reg.B, Reg.C, Reg.D, Reg.E, Reg.H, Reg.L, null, Reg.A
    };

    private static Reg extractReg(Opcode opcode, int startPos) {
        return ENCODED_REG[Bits.extract(opcode.encoding, startPos, 3)];
    }

    private static final Reg16[] ENCODED_REG16 = {
            Reg16.BC, Reg16.DE, Reg16.HL, Reg16.AF
    };

    private static Reg16 extractReg16(Opcode opcode) {
        return ENCODED_REG16[Bits.extract(opcode.encoding, 4, 2)];
    }

    private static int extractHlIncrement(Opcode opcode) {
        return Bits.test(opcode.encoding, 4) ? -1 : 1;
    }

    private boolean extractC(Opcode opcode) {
        return Bits.test(opcode.encoding, 3) & test(Flag.C);
    }

    private static Alu.RotDir extractRotDir(Opcode op) {
        return Bits.test(op.encoding, 3) ? RotDir.RIGHT : RotDir.LEFT;
    }

    private static int extractBitIndex(Opcode opcode) {
        return Bits.extract(opcode.encoding, 3, 3);
    }

    private static boolean extractBitAction(Opcode opcode) {
        return Bits.test(opcode.encoding, 6);
    }

    private boolean extractAndTestCC(Opcode opcode) {
        switch (Bits.extract(opcode.encoding, 3, 2)) {
        case 0b00: return ! test(Flag.Z);
        case 0b01: return test(Flag.Z);
        case 0b10: return ! test(Flag.C);
        case 0b11: return test(Flag.C);
        default: throw new Error();
        }
    }
}
