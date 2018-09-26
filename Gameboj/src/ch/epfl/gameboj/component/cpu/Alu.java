package ch.epfl.gameboj.component.cpu;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;
import static java.util.Objects.checkIndex;

import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.Bits;

public final class Alu {
    private Alu() {}

    public enum Flag implements Bit {
        UNUSED_0, UNUSED_1, UNUSED_2, UNUSED_3, C, H, N, Z
    }

    public enum RotDir { LEFT, RIGHT }

    public static int maskZNHC(boolean z, boolean n, boolean h, boolean c) {
        int fZ = z ? Flag.Z.mask() : 0;
        int fN = n ? Flag.N.mask() : 0;
        int fH = h ? Flag.H.mask() : 0;
        int fC = c ? Flag.C.mask() : 0;
        return fZ | fN | fH | fC;
    }

    public static int unpackValue(int valueFlags) {
        return Bits.extract(valueFlags, 8, 24);
    }

    public static int unpackFlags(int valueFlags) {
        return Bits.clip(8, valueFlags);
    }

    public static int add(int l, int r, boolean c0) {
        int sum = checkBits8(l) + checkBits8(r) + bit(c0);
        int sum8 = Bits.clip(8, sum);
        int low4Sum = Bits.clip(4, l) + Bits.clip(4, r) + bit(c0);
        return packValueZNHC(sum8, sum8 == 0, false, low4Sum > 0xF, sum > 0xFF);
    }

    public static int add(int l, int r) {
        return add(l, r, false);
    }

    public static int add16L(int l, int r) {
        int sum16 = Bits.clip(16, checkBits16(l) + checkBits16(r));
        int low8Sum = Bits.clip(8, l) + Bits.clip(8, r);
        int low4Sum = Bits.clip(4, l) + Bits.clip(4, r);
        return packValueZNHC(sum16, false, false, low4Sum > 0xF, low8Sum > 0xFF);
    }

    public static int add16H(int l, int r) {
        int sum = checkBits16(l) + checkBits16(r);
        int sum16 = Bits.clip(16, sum);
        int low12Sum = Bits.clip(12, l) + Bits.clip(12, r);
        return packValueZNHC(sum16, false, false, low12Sum > 0xFFF, sum > 0xFFFF);
    }

    public static int sub(int l, int r, boolean b0) {
        int dif = checkBits8(l) - checkBits8(r) - bit(b0);
        int dif8 = Bits.clip(8, dif);
        int low4Dif = Bits.clip(4, l) - Bits.clip(4, r) - bit(b0);
        return packValueZNHC(dif8, dif8 == 0, true, low4Dif < 0, dif < 0);
    }

    public static int sub(int l, int r) {
        return sub(l, r, false);
    }

    public static int bcdAdjust(int v, boolean n, boolean h, boolean c) {
        checkBits8(v);
        boolean fixL = h | (! n & Bits.clip(4, v) > 9);
        boolean fixH = c | (! n & v > 0x99);
        int fix = ((fixH ? 6 : 0) << 4) | (fixL ? 6 : 0);
        int res = Bits.clip(8, v + (n ? -fix : fix));
        return packValueZNHC(res, res == 0, n, false, fixH);
    }

    public static int and(int l, int r) {
        int res = checkBits8(l) & checkBits8(r);
        return packValueZNHC(res, res == 0, false, true, false);
    }

    public static int or(int l, int r) {
        int res = checkBits8(l) | checkBits8(r);
        return packValueZNHC(res, res == 0, false, false, false);
    }

    public static int xor(int l, int r) {
        int res = checkBits8(l) ^ checkBits8(r);
        return packValueZNHC(res, res == 0, false, false, false);
    }

    public static int shiftLeft(int v) {
        int res = Bits.clip(8, checkBits8(v) << 1);
        return packValueZNHC(res, res == 0, false, false, Bits.test(v, 7));
    }

    public static int shiftRightA(int v) {
        int res = (checkBits8(v) >> 1) | (v & 0b1000_0000);
        return packValueZNHC(res, res == 0, false, false, Bits.test(v, 0));
    }

    public static int shiftRightL(int v) {
        int res = checkBits8(v) >> 1;
        return packValueZNHC(res, res == 0, false, false, Bits.test(v, 0));
    }

    public static int rotate(RotDir d, int v) {
        int res = Bits.rotate(8, checkBits8(v), distance(d));
        return packValueZNHC(res, res == 0, false, false, Bits.test(v, carryBit(d)));
    }

    public static int rotate(RotDir d, int v, boolean c) {
        checkBits8(v);
        int res = Bits.clip(8, Bits.rotate(9, c ? 0x100 | v : v, distance(d)));
        return packValueZNHC(res, res == 0, false, false, Bits.test(v, carryBit(d)));
    }

    private static int distance(RotDir d) {
        return d == RotDir.LEFT ? 1 : -1;
    }

    private static int carryBit(RotDir d) {
        return d == RotDir.LEFT ? 7 : 0;
    }

    public static int swap(int v) {
        return packValueZNHC(Bits.rotate(8, checkBits8(v), 4), v == 0, false, false, false);
    }

    public static int testBit(int v, int bitIndex) {
        return packValueZNHC(0, ! Bits.test(checkBits8(v), checkIndex(bitIndex, 8)), false, true, false);
    }

    private static int packValueZNHC(int v, boolean z, boolean n, boolean h, boolean c) {
        return (v << 8) | maskZNHC(z, n, h, c);
    }

    private static int bit(boolean b) {
        return b ? 1 : 0;
    }
}
