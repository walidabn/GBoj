package ch.epfl.gameboj.component.lcd;

import static ch.epfl.gameboj.Preconditions.checkBits16;
import static ch.epfl.gameboj.Preconditions.checkBits8;

import java.util.Arrays;

import ch.epfl.gameboj.AddressMap;
import ch.epfl.gameboj.Bus;
import ch.epfl.gameboj.Register;
import ch.epfl.gameboj.RegisterFile;
import ch.epfl.gameboj.bits.Bit;
import ch.epfl.gameboj.bits.BitVector;
import ch.epfl.gameboj.bits.Bits;
import ch.epfl.gameboj.component.Clocked;
import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.cpu.Cpu;
import ch.epfl.gameboj.component.cpu.Cpu.Interrupt;
import ch.epfl.gameboj.component.memory.Ram;

public final class LcdController implements Component, Clocked {
    public static final int LCD_WIDTH = 160, LCD_HEIGHT = 144;

    private static final int FULL_LINE_TILES = 32, VISIBLE_LINE_TILES = 20;
    private static final int TILE_BYTES = 16;

    private static final LcdImageLine EMPTY_LINE =
            new LcdImageLine(new BitVector(LCD_WIDTH), new BitVector(LCD_WIDTH), new BitVector(LCD_WIDTH));

    private static final int LINE_MODE_2_START_CYCLE = 0;
    private static final int LINE_MODE_2_CYCLES = 20;
    private static final int LINE_MODE_3_START_CYCLE = LINE_MODE_2_START_CYCLE + LINE_MODE_2_CYCLES;
    private static final int LINE_MODE_3_CYCLES = 43;
    private static final int LINE_MODE_0_START_CYCLE = LINE_MODE_3_START_CYCLE + LINE_MODE_3_CYCLES;
    private static final int LINE_MODE_0_CYCLES = 51;
    private static final int LINE_TOTAL_CYCLES =
            LINE_MODE_2_CYCLES + LINE_MODE_3_CYCLES + LINE_MODE_0_CYCLES;

    private static final int VBLANK_LINES = 10;
    private static final int FRAME_TOTAL_CYCLES = (LCD_HEIGHT + VBLANK_LINES) * LINE_TOTAL_CYCLES;

    private static final int WIN_OFFSET_X = 7;

    private enum SpriteField { Y, X, TILE, ATTRIBUTES };
    private enum SpriteAttrBit implements Bit {
        UNUSED_0, UNUSED_1, UNUSED_2, UNUSED_3, PALETTE, FLIP_H, FLIP_V, BEHIND_BG
    }

    private static final int SPRITE_OFFSET_X = 8, SPRITE_OFFSET_Y = 16;
    private static final int SPRITES_COUNT = 40;
    private static final int MAX_SPRITES_PER_LINE = 10;

    private enum Reg implements Register {
        LCDC, STAT, SCY, SCX, LY, LYC, DMA, BGP, OBP0, OBP1, WY, WX
    };

    private enum LcdcBit implements Bit {
        BG, OBJ, OBJ_SIZE, BG_AREA, TILE_SOURCE, WIN, WIN_AREA, LCD_STATUS
    };

    private enum StatBit implements Bit {
        MODE0, MODE1, LYC_EQ_LY, INT_MODE0, INT_MODE1, INT_MODE2, INT_LYC
    }
    private static final int STAT_NON_MODE_MASK =
            ~(StatBit.MODE0.mask() | StatBit.MODE1.mask());
    private static final int STAT_WRITABLE_MASK =
            ~(StatBit.MODE0.mask() | StatBit.MODE1.mask() | StatBit.LYC_EQ_LY.mask());

    private static final StatBit[] STAT_INT_BITS = new StatBit[] {
            StatBit.INT_MODE0, StatBit.INT_MODE1, StatBit.INT_MODE2
    };

    private final Cpu cpu;
    private Bus bus;

    private final Ram vRam;
    private final Ram oam;

    private final RegisterFile<Reg> regs;

    private int dmaCopySrc, dmaCopyDst;

    private long nextNonIdleCycle;
    private long lcdOnCycle;
    private LcdImage currentImage;
    private LcdImage.Builder nextImageBuilder;
    private int winY;

    public LcdController(Cpu cpu) {
        this.cpu = cpu;

        this.vRam = new Ram(AddressMap.VIDEO_RAM_SIZE);
        this.oam = new Ram(AddressMap.OAM_RAM_SIZE);
        this.regs = new RegisterFile<>(Reg.values());

        this.dmaCopyDst = AddressMap.OAM_END;
        this.nextNonIdleCycle = Long.MAX_VALUE;
        this.currentImage = new LcdImage.Builder(LCD_WIDTH, LCD_HEIGHT).build();
    }

    @Override
    public void attachTo(Bus bus) {
        this.bus = bus;
        Component.super.attachTo(bus);
    }

    @Override
    public int read(int address) {
        checkBits16(address);

        if (AddressMap.VIDEO_RAM_START <= address && address < AddressMap.VIDEO_RAM_END)
            return readVram(address);
        else if (AddressMap.OAM_START <= address && address < AddressMap.OAM_END)
            return readOam(address);
        else if (AddressMap.REGS_LCDC_START <= address && address < AddressMap.REGS_LCDC_END)
            return regs.get(registerAt(address));
        else
            return Component.NO_DATA;
    }

    private int readVram(int address) {
        return vRam.read(address - AddressMap.VIDEO_RAM_START);
    }

    private int readOam(int address) {
        return oam.read(address - AddressMap.OAM_START);
    }

    @Override
    public void write(int address, int data) {
        checkBits16(address);
        checkBits8(data);

        if (AddressMap.VIDEO_RAM_START <= address && address < AddressMap.VIDEO_RAM_END)
            writeVram(address, data);
        else if (AddressMap.OAM_START <= address && address < AddressMap.OAM_END)
            writeOam(address, data);
        else if (AddressMap.REGS_LCDC_START <= address && address < AddressMap.REGS_LCDC_END) {
            Reg reg = registerAt(address);
            switch (reg) {
            case LCDC: {
                boolean wasLcdOn = isLcdOn();
                regs.set(Reg.LCDC, data);
                if (wasLcdOn && ! isLcdOn()) {
                    setMode(0);
                    setLyOrLyc(Reg.LY, 0);
                    nextNonIdleCycle = Long.MAX_VALUE;
                }
            } break;
            case STAT:
                regs.set(reg, (data & STAT_WRITABLE_MASK) | (regs.get(reg) & ~STAT_WRITABLE_MASK));
                break;
            case LY:
                // do nothing (LY is read-only)
                break;
            case LYC:
                setLyOrLyc(Reg.LYC, data);
                break;
            case DMA:
                dmaCopySrc = data << 8;
                dmaCopyDst = AddressMap.OAM_START;
                break;
            default:
                regs.set(reg, data);
                break;
            }
        }
    }

    private void writeVram(int address, int data) {
        vRam.write(address - AddressMap.VIDEO_RAM_START, data);
    }

    private void writeOam(int address, int data) {
        oam.write(address - AddressMap.OAM_START, data);
    }

    private Reg registerAt(int address) {
        return Reg.values()[address - AddressMap.REGS_LCDC_START];
    }

    private boolean isLcdOn() {
        return Bits.test(regs.get(Reg.LCDC), LcdcBit.LCD_STATUS);
    }

    @Override
    public void cycle(long cycle) {
        assert cycle <= nextNonIdleCycle;

        if (dmaCopyDst < AddressMap.OAM_END)
            writeOam(dmaCopyDst++, bus.read(dmaCopySrc++));

        if (cycle == nextNonIdleCycle) {
            reallyCycle(cycle);
        } else if (nextNonIdleCycle == Long.MAX_VALUE && isLcdOn()) {
            lcdOnCycle = nextNonIdleCycle = cycle;
            reallyCycle(cycle);
        }
    }

    private void reallyCycle(long cycle) {
        int frameCycle = (int) ((cycle - lcdOnCycle) % FRAME_TOTAL_CYCLES);
        int line = frameCycle / LINE_TOTAL_CYCLES;
        int lineCycle = frameCycle % LINE_TOTAL_CYCLES;

        if (frameCycle == 0) {
            nextImageBuilder = new LcdImage.Builder(LCD_WIDTH, LCD_HEIGHT);
            winY = 0;
        }

        if (line < LCD_HEIGHT) {
            switch (lineCycle) {
            case LINE_MODE_2_START_CYCLE:
                setLyOrLyc(Reg.LY, line);
                setMode(2);
                nextNonIdleCycle += LINE_MODE_2_CYCLES;
                break;
            case LINE_MODE_3_START_CYCLE:
                setMode(3);
                nextImageBuilder.setLine(line, computeLine(line));
                nextNonIdleCycle += LINE_MODE_3_CYCLES;
                break;
            case LINE_MODE_0_START_CYCLE:
                setMode(0);
                nextNonIdleCycle += LINE_MODE_0_CYCLES;
                break;
            default:
                throw new Error();
            }
        } else {
            assert lineCycle == 0;
            if (line == LCD_HEIGHT) {
                // Start of vertical blank
                currentImage = nextImageBuilder.build();
                nextImageBuilder = null;
                setMode(1);
                cpu.requestInterrupt(Interrupt.VBLANK);
            }

            setLyOrLyc(Reg.LY, line);
            nextNonIdleCycle += LINE_TOTAL_CYCLES;
        }
    }

    private void setMode(int m) {
        regs.set(Reg.STAT, (regs.get(Reg.STAT) & STAT_NON_MODE_MASK) | m);
        if (m < STAT_INT_BITS.length && regs.testBit(Reg.STAT, STAT_INT_BITS[m]))
            cpu.requestInterrupt(Interrupt.LCD_STAT);
    }

    private void setLyOrLyc(Reg r, int y) {
        assert r == Reg.LY || r == Reg.LYC;

        regs.set(r, y);
        boolean lyEqLyc = regs.get(Reg.LY) == regs.get(Reg.LYC);
        regs.setBit(Reg.STAT, StatBit.LYC_EQ_LY, lyEqLyc);
        if (lyEqLyc && regs.testBit(Reg.STAT, StatBit.INT_LYC))
            cpu.requestInterrupt(Interrupt.LCD_STAT);
    }

    public LcdImage currentImage() {
        return currentImage;
    }

    private LcdImageLine computeLine(int y) {
        LcdImageLine line = EMPTY_LINE;
        if (regs.testBit(Reg.LCDC, LcdcBit.BG)) {
            int wrappedY = Bits.clip(8, regs.get(Reg.SCY) + y);
            line = bgOrWinLine(LcdcBit.BG_AREA, FULL_LINE_TILES, wrappedY)
                    .extractWrapped(regs.get(Reg.SCX), LCD_WIDTH)
                    .mapColors(regs.get(Reg.BGP));
        }
        int adjWinX = Math.max(0, regs.get(Reg.WX) - WIN_OFFSET_X);
        if (regs.testBit(Reg.LCDC, LcdcBit.WIN)
                && adjWinX < LCD_WIDTH
                && regs.get(Reg.WY) <= y) {
            LcdImageLine winLine = bgOrWinLine(LcdcBit.WIN_AREA, VISIBLE_LINE_TILES, winY)
                    .mapColors(regs.get(Reg.BGP));
            line = line.join(winLine.shift(adjWinX), adjWinX);
            winY += 1;
        }
        if (regs.testBit(Reg.LCDC, LcdcBit.OBJ)) {
            int[] spritesToDisplay = spritesIntersectingLine(y);
            LcdImageLine bgSpriteLine = spriteLine(spritesToDisplay, y, SpriteKind.BACKGROUND);
            LcdImageLine fgSpriteLine = spriteLine(spritesToDisplay, y, SpriteKind.FOREGROUND);
            BitVector lineOpacity = line.opacity().or(bgSpriteLine.opacity().not());
            line = bgSpriteLine.below(line, lineOpacity).below(fgSpriteLine);
        }
        return line;
    }

    private LcdImageLine bgOrWinLine(LcdcBit sourceBit, int tilesCount, int y) {
        LcdImageLine.Builder lineB = new LcdImageLine.Builder(tilesCount * Byte.SIZE);

        int baseAddress = AddressMap.BG_DISPLAY_DATA[regs.testBit(Reg.LCDC, sourceBit) ? 1 : 0];
        int bgTilesStart = AddressMap.TILE_SOURCE[regs.testBit(Reg.LCDC, LcdcBit.TILE_SOURCE) ? 1 : 0];
        int tileY = Bits.extract(y, 3, 5), lineY = Bits.clip(3, y);
        for (int tileX = 0; tileX < tilesCount; ++tileX) {
            int tileIndex = readVram(baseAddress + FULL_LINE_TILES * tileY + tileX);
            if (! regs.testBit(Reg.LCDC, LcdcBit.TILE_SOURCE))
                tileIndex = Bits.clip(8, tileIndex + 0x80);
            lineB.setBytes(tileX,
                    tileByte(BitsWeight.MSB, bgTilesStart, tileIndex, lineY, BitsOrder.REVERSED),
                    tileByte(BitsWeight.LSB, bgTilesStart, tileIndex, lineY, BitsOrder.REVERSED));
        }

        return lineB.build();
    }

    private int[] spritesIntersectingLine(int y) {
        int spriteHeight = spriteHeight();

        int[] packedSprites = new int[MAX_SPRITES_PER_LINE];
        int intersectingSpritesCount = 0;
        for (int i = 0; i < SPRITES_COUNT && intersectingSpritesCount < MAX_SPRITES_PER_LINE; ++i) {
            int spriteY = spriteField(i, SpriteField.Y) - SPRITE_OFFSET_Y;
            if (spriteY <= y && y < spriteY + spriteHeight)
                packedSprites[intersectingSpritesCount++] = packSprite(i, spriteField(i, SpriteField.X));
        }
        Arrays.sort(packedSprites, 0, intersectingSpritesCount);

        int[] spritesToDisplay = new int[intersectingSpritesCount];
        for (int i = 0; i < intersectingSpritesCount; ++i)
            spritesToDisplay[i] = unpackSpriteIndex(packedSprites[i]);

        return spritesToDisplay;
    }

    private int packSprite(int index, int x) {
        return (x << 8) | index;
    }

    private int unpackSpriteIndex(int packed) {
        return packed & 0xFF;
    }

    private enum SpriteKind { BACKGROUND, FOREGROUND }

    private LcdImageLine spriteLine(int[] spritesToDisplay, int y, SpriteKind k) {
        int spriteHeight = spriteHeight();

        LcdImageLine finalSpriteLine = EMPTY_LINE;

        for (int id: spritesToDisplay) {
            int spriteAttrs = spriteField(id, SpriteField.ATTRIBUTES);

            if (Bits.test(spriteAttrs, SpriteAttrBit.BEHIND_BG) && k == SpriteKind.FOREGROUND)
                continue;

            int spriteX = spriteField(id, SpriteField.X) - SPRITE_OFFSET_X;
            int spriteY = spriteField(id, SpriteField.Y) - SPRITE_OFFSET_Y;

            int tileY = y - spriteY;
            if (Bits.test(spriteAttrs, SpriteAttrBit.FLIP_V))
                tileY = spriteHeight - 1 - tileY;

            int tileIndex = spriteField(id, SpriteField.TILE);
            BitsOrder bitsO = Bits.test(spriteAttrs, SpriteAttrBit.FLIP_H) ? BitsOrder.STRAIGHT : BitsOrder.REVERSED;
            int tileLsbByte = tileByte(BitsWeight.LSB, AddressMap.TILE_SOURCE[1], tileIndex, tileY, bitsO);
            int tileMsbByte = tileByte(BitsWeight.MSB, AddressMap.TILE_SOURCE[1], tileIndex, tileY, bitsO);
            int palette = regs.get(Bits.test(spriteAttrs, SpriteAttrBit.PALETTE) ? Reg.OBP1 : Reg.OBP0);

            finalSpriteLine = new LcdImageLine.Builder(LCD_WIDTH)
                    .setBytes(0, tileMsbByte, tileLsbByte)
                    .build()
                    .shift(spriteX)
                    .mapColors(palette)
                    .below(finalSpriteLine);
        }

        return finalSpriteLine;
    }

    private int spriteHeight() {
        return regs.testBit(Reg.LCDC, LcdcBit.OBJ_SIZE) ? 16 : 8;
    }

    private int spriteField(int id, SpriteField field) {
        assert 0 <= id && id < SPRITES_COUNT;
        return readOam(AddressMap.OAM_START + id * 4 + field.ordinal());
    }

    private enum BitsWeight { MSB, LSB };
    private enum BitsOrder { STRAIGHT, REVERSED };

    private int tileByte(BitsWeight w, int base, int tileIndex, int y, BitsOrder order) {
        int b = readVram(base + tileIndex * TILE_BYTES + y * 2 + (w == BitsWeight.MSB ? 1 : 0));
        return order == BitsOrder.STRAIGHT ? b : Bits.reverse8(b);
    }
}
