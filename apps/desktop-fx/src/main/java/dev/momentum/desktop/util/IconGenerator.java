package dev.momentum.desktop.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Generates a simple .ico file for the MOMENTUM application icon.
 * Creates a 256x256 PNG embedded in an ICO container with the MOMENTUM
 * brand accent color (neon cyan #22d3ee) on a dark background.
 */
public final class IconGenerator {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 256;
    private static final int ACCENT_RGB = 0x22D3EE; // Neon cyan
    private static final int BG_RGB = 0x05070D;     // Dark cyber background

    public static void main(String[] args) throws IOException {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        // Fill background
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                // Gradient circle effect
                double dx = x - WIDTH / 2.0;
                double dy = y - HEIGHT / 2.0;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < WIDTH / 2.0 - 10) {
                    double t = dist / (WIDTH / 2.0);
                    int r = (int) ((BG_RGB >> 16 & 0xFF) * (1 - t) + (ACCENT_RGB >> 16 & 0xFF) * t * 0.3);
                    int g = (int) ((BG_RGB >> 8 & 0xFF) * (1 - t) + (ACCENT_RGB >> 8 & 0xFF) * t * 0.8);
                    int b = (int) ((BG_RGB & 0xFF) * (1 - t) + (ACCENT_RGB & 0xFF) * t);
                    img.setRGB(x, y, (255 << 24) | (r << 16) | (g << 8) | b);
                } else {
                    img.setRGB(x, y, 0); // transparent
                }
            }
        }

        // Draw "M" glyph in accent color
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(ACCENT_RGB, true));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 140));
        g2.drawString("M", WIDTH / 2 - 55, HEIGHT / 2 + 55);
        g2.dispose();

        // Write ICO file
        writeIco(img, new java.io.File("apps/desktop-fx/src/main/resources/dev/momentum/desktop/icon.ico"));
        System.out.println("Icon written to apps/desktop-fx/src/main/resources/dev/momentum/desktop/icon.ico");
    }

    private static void writeIco(BufferedImage img, java.io.File out) throws IOException {
        // Resize to multiple sizes for the ICO
        int[][] sizes = {{256, 256}, {128, 128}, {64, 64}, {32, 32}, {16, 16}};

        try (FileOutputStream fos = new FileOutputStream(out)) {
            // ICO header
            writeShort(fos, 0); // reserved
            writeShort(fos, 1); // type = 1 (icon)
            writeShort(fos, sizes.length); // count

            int offset = 6 + sizes.length * 16; // header + directory entries

            for (int[] size : sizes) {
                BufferedImage resized = resize(img, size[0], size[1]);
                byte[] pngBytes = toPngBytes(resized);

                // Directory entry
                fos.write(size[0] >= 256 ? 0 : size[0]); // width
                fos.write(size[1] >= 256 ? 0 : size[1]); // height
                fos.write(0); // color count
                fos.write(0); // reserved
                writeShort(fos, 0); // color planes
                writeShort(fos, 32); // bits per pixel
                writeInt(fos, pngBytes.length); // image size
                writeInt(fos, offset); // offset
                offset += pngBytes.length;
            }

            // Image data
            for (int[] size : sizes) {
                BufferedImage resized = resize(img, size[0], size[1]);
                byte[] pngBytes = toPngBytes(resized);
                fos.write(pngBytes);
            }
        }
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dst;
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "PNG", bos);
        return bos.toByteArray();
    }

    private static void writeShort(java.io.OutputStream os, int value) throws IOException {
        os.write(value & 0xFF);
        os.write((value >> 8) & 0xFF);
    }

    private static void writeInt(java.io.OutputStream os, int value) throws IOException {
        os.write(value & 0xFF);
        os.write((value >> 8) & 0xFF);
        os.write((value >> 16) & 0xFF);
        os.write((value >> 24) & 0xFF);
    }
}
