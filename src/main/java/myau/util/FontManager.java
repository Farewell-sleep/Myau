package myau.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
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
 *
 * Every size class owns one glyph-atlas texture (power-of-two). Glyphs are
 * rasterized from the bundled HarmonyOS Sans TTF and uploaded with
 * glTexSubImage2D. Text is drawn with the standard Tessellator quad path
 * (same as the vanilla FontRenderer), which is reliable in every GL context
 * and far cheaper than per-character immediate-mode batches.
 *
 * The renderer never disables the texture or blend state it did not own, so
 * world rendering is never corrupted after the HUD is drawn.
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
        for (FontRenderer r : CACHE.values()) {
            r.deleteAtlas();
        }
        CACHE.clear();
    }

    private static final class FontRenderer {
        private final Font awtFont;
        private final float scale;
        private final int fontSize;
        private final Map<Character, Glyph> glyphs = new HashMap<>();

        private int atlasTexture = -1;
        private int atlasW = 256;
        private int atlasH = 256;
        private int cursorX = 2;
        private int cursorY = 2;
        private int rowH = 0;

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
            int width;
            int height;
            int x;
            int y;
            float u1;
            float v1;
            float u2;
            float v2;
            BufferedImage image;
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

        private void ensureAtlas() {
            if (this.atlasTexture != -1) return;
            this.atlasTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.atlasTexture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, this.atlasW, this.atlasH, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            this.setTexParams();
        }

        private void setTexParams() {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F); // GL_CLAMP_TO_EDGE
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F); // GL_CLAMP_TO_EDGE
        }

        private void growAtlas() {
            int newW = this.atlasW * 2;
            int newH = this.atlasH * 2;
            int newTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, newTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, newW, newH, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            this.setTexParams();
            for (Glyph g : this.glyphs.values()) {
                this.uploadGlyph(g, newTex, g.x, g.y);
                g.u1 = g.x / (float) newW;
                g.v1 = g.y / (float) newH;
                g.u2 = (g.x + g.width) / (float) newW;
                g.v2 = (g.y + g.height) / (float) newH;
            }
            GL11.glDeleteTextures(this.atlasTexture);
            this.atlasTexture = newTex;
            this.atlasW = newW;
            this.atlasH = newH;
            this.cursorX = 2;
            this.cursorY = 2;
            this.rowH = 0;
        }

        private void uploadGlyph(Glyph g, int texture, int x, int y) {
            int[] pixels = g.image.getRGB(0, 0, g.width, g.height, null, 0, g.width);
            ByteBuffer buffer = ByteBuffer.allocateDirect(g.width * g.height * 4);
            for (int pixel : pixels) {
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
            buffer.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, g.width, g.height,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        }

        private Glyph renderGlyph(char c) {
            Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            probe.setFont(this.awtFont);
            FontMetrics metrics = probe.getFontMetrics();
            int width = Math.max(1, metrics.charWidth(c));
            int height = Math.max(1, metrics.getHeight());
            int baseline = metrics.getAscent();
            probe.dispose();

            this.ensureAtlas();
            int pad = 1;
            if (this.cursorX + width + pad > this.atlasW) {
                this.cursorX = 2;
                this.cursorY += this.rowH + pad;
                this.rowH = 0;
            }
            if (this.cursorY + height + pad > this.atlasH) {
                this.growAtlas();
            }
            if (this.cursorX + width + pad > this.atlasW) {
                this.cursorX = 2;
                this.cursorY += this.rowH + pad;
                this.rowH = 0;
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setFont(this.awtFont);
            g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.drawString(String.valueOf(c), 0, baseline);
            g.dispose();

            Glyph glyph = new Glyph();
            glyph.width = width;
            glyph.height = height;
            glyph.x = this.cursorX;
            glyph.y = this.cursorY;
            glyph.image = image;
            glyph.u1 = glyph.x / (float) this.atlasW;
            glyph.v1 = glyph.y / (float) this.atlasH;
            glyph.u2 = (glyph.x + width) / (float) this.atlasW;
            glyph.v2 = (glyph.y + height) / (float) this.atlasH;
            this.uploadGlyph(glyph, this.atlasTexture, glyph.x, glyph.y);

            this.cursorX += width + pad;
            this.rowH = Math.max(this.rowH, height);
            return glyph;
        }

        private void deleteAtlas() {
            if (this.atlasTexture != -1) {
                GL11.glDeleteTextures(this.atlasTexture);
                this.atlasTexture = -1;
            }
        }

        private void drawString(String text, float x, float y, int color, boolean shadow) {
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

            this.ensureAtlas();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.bindTexture(this.atlasTexture);

            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer wr = tessellator.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            float cursor = x;
            for (char c : text.toCharArray()) {
                Glyph glyph = this.getGlyph(c);
                float glyphWidth = glyph.width * this.scale;
                float glyphHeight = glyph.height * this.scale;
                wr.pos(cursor, y, 0.0D).tex(glyph.u1, glyph.v1).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor + glyphWidth, y, 0.0D).tex(glyph.u2, glyph.v1).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor + glyphWidth, y + glyphHeight, 0.0D).tex(glyph.u2, glyph.v2).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor, y + glyphHeight, 0.0D).tex(glyph.u1, glyph.v2).color(red, green, blue, alpha).endVertex();
                cursor += glyphWidth;
            }
            tessellator.draw();

            // Restore only the states we changed; never leave texture/alpha disabled.
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
        }
    }
}
