package sh.joey.mc.statue;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Represents a parsed Minecraft skin texture with pixel access.
 */
public final class SkinTexture {

    private final int[][] pixels;
    private final int width;
    private final int height;

    private SkinTexture(int[][] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
    }

    /**
     * Parses a PNG image from bytes into a SkinTexture.
     *
     * @param pngBytes raw PNG image data
     * @return parsed SkinTexture
     * @throws IOException if the image cannot be read
     */
    public static SkinTexture fromPng(byte[] pngBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        if (image == null) {
            throw new IOException("Failed to decode PNG image");
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // Minecraft skins are either 64x64 (modern) or 64x32 (legacy)
        if (width != 64 || (height != 64 && height != 32)) {
            throw new IOException("Invalid skin dimensions: " + width + "x" + height);
        }

        int[][] pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y][x] = image.getRGB(x, y);
            }
        }

        return new SkinTexture(pixels, width, height);
    }

    /**
     * Gets the ARGB pixel value at the given coordinates.
     *
     * @param x X coordinate (0 = left)
     * @param y Y coordinate (0 = top)
     * @return packed ARGB value
     */
    public int getPixel(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0; // Transparent for out-of-bounds
        }
        return pixels[y][x];
    }

    /**
     * Checks if the pixel at the given coordinates is transparent.
     */
    public boolean isTransparent(int x, int y) {
        int alpha = (getPixel(x, y) >> 24) & 0xFF;
        return alpha < 128;
    }

    /**
     * Gets the red component (0-255) of the pixel.
     */
    public int getRed(int x, int y) {
        return (getPixel(x, y) >> 16) & 0xFF;
    }

    /**
     * Gets the green component (0-255) of the pixel.
     */
    public int getGreen(int x, int y) {
        return (getPixel(x, y) >> 8) & 0xFF;
    }

    /**
     * Gets the blue component (0-255) of the pixel.
     */
    public int getBlue(int x, int y) {
        return getPixel(x, y) & 0xFF;
    }

    /**
     * Extracts a rectangular region of pixels for a face.
     *
     * @param uv the UV mapping defining the region
     * @return 2D array of ARGB pixels [y][x]
     */
    public int[][] extractFace(StatueGeometry.UvMapping uv) {
        int[][] face = new int[uv.height()][uv.width()];
        for (int y = 0; y < uv.height(); y++) {
            for (int x = 0; x < uv.width(); x++) {
                face[y][x] = getPixel(uv.u() + x, uv.v() + y);
            }
        }
        return face;
    }

    /**
     * Returns whether this is a legacy 64x32 skin.
     */
    public boolean isLegacy() {
        return height == 32;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
