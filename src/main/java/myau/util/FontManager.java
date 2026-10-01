package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Modern font renderer used by the HUD, ClickGUI and other UI.
 * Rasterizes TTF glyphs (bundled HarmonyOS Sans, rendered at 2x) into
 * power-of-two texture cells so every GL context renders text correctly.
 * Falls back to the vanilla font when disabled.
 */
public class FontManager {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<Float, FontRenderer> CACHE = new HashMap<>();

    /** When false, every call falls through to the vanilla font renderer. */
    public static boolean enabled = true;

    public static boolean customFont() {
        return enabled;
    }

    private static FontRenderer renderer(float size) {
        FontRenderer r = CACHE.get(size);
        if (r == null) {
            r = new FontRenderer(Math.round(size));
            CACHE.put(size, r);
        }
        return r;
    }

    public static int getStringWidth(String text) {
        return getStringWidth(text, 14.0F);
    }

    public static int getStringWidth(String text, float size) {
        if (text == null || text.isEmpty()) return 0;
        if (!enabled) return mc.fontRendererObj.getStringWidth(text);
        return renderer(size).getStringWidth(text);
    }

    public static int getFontHeight() {
        if (!enabled) return mc.fontRendererObj.FONT_HEIGHT;
        return renderer(14.0F).getHeight();
    }

    public static float getCapHeight(float size) {
        if (!enabled) return (float) mc.fontRendererObj.FONT_HEIGHT;
        return size * 0.72F;
    }

    public static float getBaseline(float size) {
        if (!enabled) return 2.0F;
        return size * 0.82F;
    }

    public static void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, false);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        drawString(text, x, y, color, shadow, 14.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size) {
        if (text == null || text.isEmpty()) return;
        if (!enabled) {
            if (shadow) {
                mc.fontRendererObj.drawStringWithShadow(text, x, y, color);
            } else {
                mc.fontRendererObj.drawString(text, x, y, color, false);
            }
            return;
        }
        renderer(size).drawString(text, x, y, color, shadow);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        drawString(text, x, y, color, true);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size) {
        drawString(text, x, y, color, true, size);
    }

    /** Disposes all cached glyph textures (called on shutdown if needed). */
    public static void clear() {
        CACHE.clear();
    }

    private static final class FontRenderer {
        private final Font awtFont;
        private final float scale;
        private final int fontSize;
        private final Map<Character, Glyph> glyphs = new HashMap<>();

        private FontRenderer(int size) {
            this.fontSize = size;
            Font base;
            try {
                base = Font.createFont(Font.TRUETYPE_FONT,
                        new java.io.ByteArrayInputStream(FontData.harmonyosSansRegular()));
            } catch (Exception e) {
                base = new Font("Dialog", Font.PLAIN, size);
            }
            this.awtFont = base.deriveFont(Font.PLAIN, size * 2.0F);
            this.scale = 0.5F;
        }

        private static final class Glyph {
            int textureId;
            int width;
            int height;
            int texWidth;
            int texHeight;
        }

        private int getHeight() {
            return this.fontSize + 3;
        }

        private int getStringWidth(String text) {
            float width = 0.0F;
            for (char c : text.toCharArray()) {
                width += this.getGlyph(c).width * this.scale;
            }
            return Math.round(width);
        }

        private Glyph getGlyph(char c) {
            Glyph glyph = this.glyphs.get(c);
            if (glyph == null) {
                glyph = this.renderGlyph(c);
                this.glyphs.put(c, glyph);
            }
            return glyph;
        }

        private Glyph renderGlyph(char c) {
            Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            probe.setFont(this.awtFont);
            FontMetrics metrics = probe.getFontMetrics();
            int width = Math.max(1, metrics.charWidth(c));
            int height = Math.max(1, metrics.getHeight());
            int baseline = metrics.getAscent();
            probe.dispose();

            // power-of-two texture cell: NPOT textures break on old GL contexts
            int texW = 1;
            while (texW < width) texW <<= 1;
            int texH = 1;
            while (texH < height) texH <<= 1;

            BufferedImage image = new BufferedImage(texW, texH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setFont(this.awtFont);
            g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.drawString(String.valueOf(c), 0, baseline);
            g.dispose();

            int textureId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            int[] pixels = image.getRGB(0, 0, texW, texH, null, 0, texW);
            ByteBuffer buffer = ByteBuffer.allocateDirect(texW * texH * 4);
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
            buffer.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, texW, texH, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);

            Glyph glyph = new Glyph();
            glyph.textureId = textureId;
            glyph.width = width;
            glyph.height = height;
            glyph.texWidth = texW;
            glyph.texHeight = texH;
            return glyph;
        }

        private void drawString(String text, float x, float y, int color, boolean shadow) {
            if (text == null || text.isEmpty()) return;
            if (shadow) {
                this.drawInternal(text, x + 0.6F, y + 0.6F, (color & 0xFF000000) | 0x00101010);
            }
            this.drawInternal(text, x, y, color);
        }

        private void drawInternal(String text, float x, float y, int color) {
            float alpha = (float) ((color >> 24) & 0xFF) / 255.0F;
            float red = (float) ((color >> 16) & 0xFF) / 255.0F;
            float green = (float) ((color >> 8) & 0xFF) / 255.0F;
            float blue = (float) (color & 0xFF) / 255.0F;

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GL11.glColor4f(red, green, blue, alpha);
            float cursor = x;
            for (char c : text.toCharArray()) {
                Glyph glyph = this.getGlyph(c);
                float glyphWidth = glyph.width * this.scale;
                float glyphHeight = glyph.height * this.scale;
                float u = glyph.width / (float) glyph.texWidth;
                float v = glyph.height / (float) glyph.texHeight;
                GlStateManager.bindTexture(glyph.textureId);
                GL11.glBegin(GL11.GL_QUADS);
                GL11.glTexCoord2f(0.0F, 0.0F);
                GL11.glVertex2f(cursor, y);
                GL11.glTexCoord2f(u, 0.0F);
                GL11.glVertex2f(cursor + glyphWidth, y);
                GL11.glTexCoord2f(u, v);
                GL11.glVertex2f(cursor + glyphWidth, y + glyphHeight);
                GL11.glTexCoord2f(0.0F, v);
                GL11.glVertex2f(cursor, y + glyphHeight);
                GL11.glEnd();
                cursor += glyphWidth;
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableAlpha();
            GlStateManager.disableTexture2D();
            GlStateManager.disableBlend();
        }
    }
}
