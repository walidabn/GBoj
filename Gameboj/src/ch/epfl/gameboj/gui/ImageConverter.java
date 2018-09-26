package ch.epfl.gameboj.gui;

import ch.epfl.gameboj.component.lcd.LcdImage;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

public final class ImageConverter {
    private static final int[] COLOR_MAP = new int[] {
            0xFF_FF_FF_FF, // white
            0xFF_D3_D3_D3, // light gray
            0xFF_A9_A9_A9, // dark gray
            0xFF_00_00_00  // black
    };

    public static Image convert(LcdImage lcdImage) {
        WritableImage jfxImage = new WritableImage(lcdImage.width(), lcdImage.height());
        PixelWriter pixWriter = jfxImage.getPixelWriter();
        for (int y = 0; y < lcdImage.height(); ++y) {
            for (int x = 0; x < lcdImage.width(); ++x) {
                pixWriter.setArgb(x, y, COLOR_MAP[lcdImage.get(x, y)]);
            }
        }
        return jfxImage;
    }
}
