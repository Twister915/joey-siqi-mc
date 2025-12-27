package sh.joey.mc.statue;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps RGB colors to the nearest Minecraft wool color.
 * Uses Euclidean distance in RGB space with optional Floyd-Steinberg dithering.
 */
public final class WoolColorMapper {

    private static final Map<Material, int[]> WOOL_COLORS = Map.ofEntries(
            Map.entry(Material.WHITE_WOOL, new int[]{233, 236, 236}),
            Map.entry(Material.ORANGE_WOOL, new int[]{234, 126, 53}),
            Map.entry(Material.MAGENTA_WOOL, new int[]{189, 68, 179}),
            Map.entry(Material.LIGHT_BLUE_WOOL, new int[]{58, 175, 217}),
            Map.entry(Material.YELLOW_WOOL, new int[]{248, 198, 39}),
            Map.entry(Material.LIME_WOOL, new int[]{112, 185, 25}),
            Map.entry(Material.PINK_WOOL, new int[]{237, 141, 172}),
            Map.entry(Material.GRAY_WOOL, new int[]{62, 68, 71}),
            Map.entry(Material.LIGHT_GRAY_WOOL, new int[]{142, 142, 134}),
            Map.entry(Material.CYAN_WOOL, new int[]{21, 137, 145}),
            Map.entry(Material.PURPLE_WOOL, new int[]{121, 42, 172}),
            Map.entry(Material.BLUE_WOOL, new int[]{53, 57, 157}),
            Map.entry(Material.BROWN_WOOL, new int[]{114, 71, 40}),
            Map.entry(Material.GREEN_WOOL, new int[]{84, 109, 27}),
            Map.entry(Material.RED_WOOL, new int[]{160, 39, 34}),
            Map.entry(Material.BLACK_WOOL, new int[]{20, 21, 25})
    );

    private final Map<Integer, Material> cache = new HashMap<>();

    /**
     * Maps an RGB color to the nearest wool Material.
     */
    public Material mapToWool(int r, int g, int b) {
        int packed = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        Material cached = cache.get(packed);
        if (cached != null) {
            return cached;
        }

        Material best = Material.WHITE_WOOL;
        double minDistance = Double.MAX_VALUE;

        for (var entry : WOOL_COLORS.entrySet()) {
            int[] woolRgb = entry.getValue();
            double distance = Math.sqrt(
                    Math.pow(r - woolRgb[0], 2) +
                    Math.pow(g - woolRgb[1], 2) +
                    Math.pow(b - woolRgb[2], 2)
            );
            if (distance < minDistance) {
                minDistance = distance;
                best = entry.getKey();
            }
        }

        cache.put(packed, best);
        return best;
    }

    /**
     * Gets the RGB values for a wool Material.
     */
    public int[] getWoolRgb(Material wool) {
        return WOOL_COLORS.getOrDefault(wool, new int[]{255, 255, 255});
    }

    /**
     * Maps a 2D array of RGB pixels to their nearest wool Materials.
     *
     * @param pixels 2D array where pixels[y][x] contains packed ARGB
     * @param width  width of the pixel array
     * @param height height of the pixel array
     * @return 2D array of Materials (null for transparent pixels)
     */
    public Material[][] mapFace(int[][] pixels, int width, int height) {
        Material[][] result = new Material[height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y][x];
                int alpha = (argb >> 24) & 0xFF;

                if (alpha < 128) {
                    result[y][x] = null;
                    continue;
                }

                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                result[y][x] = mapToWool(r, g, b);
            }
        }

        return result;
    }
}
