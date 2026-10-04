package myau.risefont;

import java.awt.Font;
import java.util.HashMap;
import net.minecraft.client.Minecraft;

/**
 * Skidded from Rise (com.alan.clients.util.font.FontManager).
 * Rise-style font manager: MAIN = Product Sans (latin) + HarmonyOS Sans SC (CJK).
 * Font resources live in assets/myau/fonts/.
 */
public enum RiseFontManager {
    MAIN("myau:fonts/product_sans_%s.ttf", "HarmonyOS_Sans_SC_");

    private final String nameTemplate;
    private final String cjkPrefix;
    private final HashMap<Integer, RiseFont> fonts = new HashMap<>();

    RiseFontManager(String nameTemplate, String cjkPrefix) {
        this.nameTemplate = nameTemplate;
        this.cjkPrefix = cjkPrefix;
    }

    public RiseFont get(int size, RiseFontWeight weight) {
        int key = Integer.parseInt("" + size + weight.getWeight());
        RiseFont renderer = this.fonts.get(key);
        if (renderer == null) {
            java.awt.Font font = null;
            for (String alias : weight.getAliases()) {
                font = RiseFontUtil.loadFromResource(String.format(this.nameTemplate, alias), size);
                if (font != null) {
                    break;
                }
            }
            if (font == null) {
                font = RiseFontUtil.loadFromResource(String.format(this.nameTemplate, "regular"), size);
            }
            if (font == null) {
                // Never crash the game on a missing font: fall back to AWT system font,
                // and as a last resort to the vanilla font renderer.
                try {
                    font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, size);
                } catch (Throwable ignored) {
                }
            }
            if (font != null) {
                try {
                    RiseFontRenderer riseRenderer = new RiseFontRenderer(font, true, true, false);
                    java.awt.Font cjk = RiseFontUtil.loadFromResource("myau:fonts/" + this.cjkPrefix + "Regular.ttf", size);
                    if (cjk != null) {
                        riseRenderer.setCjkCache(new RiseGlyphCache(cjk, true, true));
                    }
                    java.awt.Font deng = RiseFontUtil.loadFromResource("myau:fonts/DengXian.ttf", size);
                    if (deng != null) {
                        riseRenderer.setFallbackCache(new RiseGlyphCache(deng, true, true));
                    }
                    renderer = riseRenderer;
                } catch (Throwable ignored) {
                    renderer = null;
                }
            }
            if (renderer == null) {
                renderer = minecraft();
            }
            this.fonts.put(key, renderer);
        }
        return renderer;
    }

    public RiseFont get(int size) {
        return this.get(size, RiseFontWeight.REGULAR);
    }

    /** Default HUD / arraylist font size. */
    public RiseFont hudFont() {
        return this.get(18, RiseFontWeight.REGULAR);
    }

    // ---- static convenience API (drop-in replacement for Myau FontManager) ----

    private static RiseFont bySize(float size) {
        return MAIN.get(Math.max(8, Math.round(size)), RiseFontWeight.REGULAR);
    }

    public static int getStringWidth(String text, float size) {
        return bySize(size).getStringWidth(text);
    }

    public static int getStringWidth(String text) {
        return getStringWidth(text, 14.0F);
    }

    public static int getFontHeight() {
        return bySize(14.0F).height() > 0 ? Math.max(9, (int) bySize(14.0F).height()) : 9;
    }

    public static float getCapHeight(float size) {
        return size * 0.72F;
    }

    public static float getBaseline(float size) {
        return size * 0.82F;
    }

    public static void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, false, 14.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        drawString(text, x, y, color, shadow, 14.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size) {
        bySize(size).b(text, x, y, color, shadow);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        drawString(text, x, y, color, true, 14.0F);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size) {
        drawString(text, x, y, color, true, size);
    }

    /** Minecraft vanilla font adapter (for consistency with world rendering). */
    public static RiseFont minecraft() {
        return new MinecraftFontAdapter();
    }

    private static class MinecraftFontAdapter extends RiseFont {
        private final net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRendererObj;

        @Override
        public int b(String s, double x, double y, int color, boolean shadow) {
            return fr.drawString(s, (int) x, (int) y, color, shadow);
        }

        @Override
        public int a(String s, double x, double y, int color) {
            return fr.drawString(s, (int) x, (int) y, color, false);
        }

        @Override
        public int b(String s, double x, double y, int color) {
            return fr.drawString(s, (int) x, (int) y, color, false);
        }

        @Override
        public int getStringWidth(String s) {
            return fr.getStringWidth(s);
        }

        @Override
        public int drawString(String s, double x, double y, int color) {
            return fr.drawString(s, (int) x, (int) y, color);
        }

        @Override
        public int drawCenteredString(String s, double x, double y, int color) {
            return fr.drawString(s, (int) (x - fr.getStringWidth(s) / 2.0), (int) y, color, false);
        }

        @Override
        public float height() {
            return fr.FONT_HEIGHT;
        }

        @Override
        public void a(char c, int x, int y, java.awt.Color color) {
            fr.drawString(String.valueOf(c), x, y, color.getRGB());
        }
    }
}
