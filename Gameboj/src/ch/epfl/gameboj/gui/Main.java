package ch.epfl.gameboj.gui;

import java.io.File;
import java.util.List;
import java.util.Map;

import ch.epfl.gameboj.GameBoy;
import ch.epfl.gameboj.component.Joypad;
import ch.epfl.gameboj.component.Joypad.Key;
import ch.epfl.gameboj.component.cartridge.Cartridge;
import ch.epfl.gameboj.component.lcd.LcdController;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public final class Main extends Application {
    private static final int IMAGE_SCALE_FACTOR = 2;

    private static final Map<KeyCode, Joypad.Key> KEY_CODE_MAP =
            Map.of(KeyCode.RIGHT, Key.RIGHT,
                    KeyCode.LEFT, Key.LEFT,
                    KeyCode.UP, Key.UP,
                    KeyCode.DOWN, Key.DOWN);
    private static final Map<String, Joypad.Key> KEY_TEXT_MAP =
            Map.of("a", Key.A, "b", Key.B, " ", Key.SELECT, "s", Key.START);

    public static void main(String[] args) {
        Application.launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        List<String> params = getParameters().getRaw();
        if (params.size() != 1) {
            System.err.println("Invalid arguments (" + params + ")");
            System.exit(1);
        }

        String romFileName = params.get(0);

        GameBoy gameBoy = new GameBoy(Cartridge.ofFile(new File(romFileName)));
        Joypad joyPad = gameBoy.joypad();
        LcdController lcdController = gameBoy.lcdController();

        ImageView lcdScreen = new ImageView();
        lcdScreen.setFitWidth(LcdController.LCD_WIDTH * IMAGE_SCALE_FACTOR);
        lcdScreen.setFitHeight(LcdController.LCD_HEIGHT * IMAGE_SCALE_FACTOR);

        EventHandler<KeyEvent> keyHandler = e -> {
            Joypad.Key b = KEY_CODE_MAP.getOrDefault(e.getCode(), KEY_TEXT_MAP.get(e.getText()));
            if (b != null) {
                if (e.getEventType() == KeyEvent.KEY_PRESSED)
                    joyPad.keyPressed(b);
                else
                    joyPad.keyReleased(b);
            }
        };
        lcdScreen.setOnKeyPressed(keyHandler);
        lcdScreen.setOnKeyReleased(keyHandler);

        Scene scene = new Scene(new BorderPane(lcdScreen));

        primaryStage.setScene(scene);
        primaryStage.setTitle("Gameboj");
        primaryStage.show();

        lcdScreen.requestFocus();

        long start = System.nanoTime();
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                long elapsed = now - start;
                gameBoy.runUntil((long) (elapsed * GameBoy.CYCLES_PER_NS));
                lcdScreen.setImage(ImageConverter.convert(lcdController.currentImage()));
            }
        };
        timer.start();
    }
}
