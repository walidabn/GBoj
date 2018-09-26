package ch.epfl.gameboj;

import static ch.epfl.gameboj.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import ch.epfl.gameboj.component.Component;
import ch.epfl.gameboj.component.Joypad;
import ch.epfl.gameboj.component.Timer;
import ch.epfl.gameboj.component.cartridge.Cartridge;
import ch.epfl.gameboj.component.cpu.Cpu;
import ch.epfl.gameboj.component.lcd.LcdController;
import ch.epfl.gameboj.component.memory.BootRomController;
import ch.epfl.gameboj.component.memory.Ram;
import ch.epfl.gameboj.component.memory.RamController;

public final class GameBoy {
    public static final long CYCLES_PER_S = 1 << 20;
    public static final double CYCLES_PER_NS = CYCLES_PER_S / 1e9;

    private final Bus bus;
    private final Cpu cpu;
    private final Timer timer;
    private final Joypad joypad;
    private final LcdController lcdController;

    private long cycles;

    public GameBoy(Cartridge cartridge) {
        BootRomController bootRomController = new BootRomController(requireNonNull(cartridge));
        Ram workRam = new Ram(AddressMap.WORK_RAM_SIZE);
        RamController workRamController = new RamController(workRam, AddressMap.WORK_RAM_START);
        RamController echoRamController = new RamController(workRam, AddressMap.ECHO_RAM_START, AddressMap.ECHO_RAM_END);
        Cpu cpu = new Cpu();
        Timer timer = new Timer(cpu);
        Joypad joypad = new Joypad(cpu);
        LcdController lcdController = new LcdController(cpu);

        Bus bus = new Bus();
        for (Component c: new Component[] {bootRomController, workRamController, echoRamController, cpu, timer, joypad, lcdController})
            c.attachTo(bus);

        this.bus = bus;
        this.cpu = cpu;
        this.timer = timer;
        this.joypad = joypad;
        this.lcdController = lcdController;

        this.cycles = 0;
    }

    public void runUntil(long cycle) {
        checkArgument(cycles <= cycle);
        
        while (cycles < cycle) {
            timer.cycle(cycles);
            lcdController.cycle(cycles);
            cpu.cycle(cycles);

            cycles += 1;
        }
    }

    public Bus bus() { return bus; }
    public Cpu cpu() { return cpu; }
    public Timer timer() { return timer; }
    public Joypad joypad() { return joypad; }
    public LcdController lcdController() { return lcdController; }

    public long cycles() { return cycles; }
}
