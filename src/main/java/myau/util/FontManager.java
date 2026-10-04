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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modern font renderer used by the HUD, ClickGUI and other UI.
 *
 * 主字体 = 仓库内置的等线（DengXian，Windows 系统标准现代 UI 中文字体），
 * 英文与中文均由它直接渲染；仅当个别码点缺失时逐码点回退到 Segoe UI / SimHei /
 * HarmonyOS / 系统字体链。
 *
 * Every size class owns one glyph-atlas texture (power-of-two). Glyphs are
 * rasterized per codepoint from whichever font in the chain covers it and
 * uploaded with glTexSubImage2D. Text is drawn with the standard Tessellator
 * quad path (same as the vanilla FontRenderer), which is reliable in every
 * GL context and far cheaper than per-character immediate-mode batches.
 *
 * The renderer saves the full GL state it touches (texture binding, alpha test,
 * blend, texture2D) before drawing and restores it afterwards, so world/entity
 * rendering is never corrupted after the HUD is drawn.
 */
public class FontManager {

    /** 系统回退字体名（按优先级），用于覆盖内置字体缺失的 CJK 与符号。 */
    private static final String[] FALLBACK_FONT_NAMES = {
            "Microsoft YaHei", "Microsoft YaHei UI", "SimSun", "NSimSun",
            "Segoe UI Emoji", "Segoe UI Symbol", "Arial", "SansSerif"
    };

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
        private final Font[] chain;
        private final float scale;
        private final int fontSize;
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        private final Map<Integer, Font> resolved = new HashMap<>();

        private int atlasTexture = -1;
        private int atlasW = 256;
        private int atlasH = 256;
        private int cursorX = 2;
        private int cursorY = 2;
        private int rowH = 0;

        private FontRenderer(int size) {
            this.fontSize = size;
            Font systemStandard = loadTrueType(loadResource("/assets/myau/fonts/DengXian.ttf"));
            Font systemSans = loadTrueType(loadResource("/assets/myau/fonts/SystemSans.ttf"));
            Font systemCjk = loadTrueType(loadResource("/assets/myau/fonts/SystemCJK.ttf"));
            Font harmony = loadTrueType(FontData.harmonyosSansRegular());
            this.awtFont = systemStandard.deriveFont(Font.PLAIN, size * 2.0F);
            Font sansScaled = systemSans.deriveFont(Font.PLAIN, size * 2.0F);
            Font cjkScaled = systemCjk.deriveFont(Font.PLAIN, size * 2.0F);
            Font harmonyScaled = harmony.deriveFont(Font.PLAIN, size * 2.0F);
            List<Font> list = new ArrayList<>();
            list.add(this.awtFont);              // 1) 等线 DengXian（系统标准，主）
            list.add(sansScaled);                // 2) Segoe UI（英文回退）
            list.add(cjkScaled);                 // 3) SimHei 黑体（中文回退）
            list.add(harmonyScaled);             // 4) HarmonyOS（内嵌回退）
            for (String name : FALLBACK_FONT_NAMES) {
                list.add(new Font(name, Font.PLAIN, size * 2)); // 5) 系统 CJK / 通用
            }
            this.chain = list.toArray(new Font[0]);
            this.scale = 0.5F;
        }

        /** 从 jar 资源读取字体文件字节；失败返回 null。 */
        private static byte[] loadResource(String path) {
            try (InputStream in = FontManager.class.getResourceAsStream(path)) {
                if (in == null) return null;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            } catch (Exception e) {
                return null;
            }
        }

        /** 从字节加载 TTF；失败时退回逻辑字体，保证运行时不抛异常。 */
        private static Font loadTrueType(byte[] data) {
            try {
                if (data == null) {
                    return new Font("Dialog", Font.PLAIN, 16);
                }
                return Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(data));
            } catch (Exception e) {
                return new Font("Dialog", Font.PLAIN, 16);
            }
        }

        /** 码点级回退：返回链中第一个 canDisplay 该码点的字体，结果按码点缓存。 */
        private Font resolveFont(int codePoint) {
            Font cached = this.resolved.get(codePoint);
            if (cached != null) return cached;
            Font chosen = this.chain[0];
            for (Font candidate : this.chain) {
                if (candidate.canDisplay(codePoint)) {
                    chosen = candidate;
                    break;
                }
            }
            this.resolved.put(codePoint, chosen);
            return chosen;
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
            int i = 0, len = text.length();
            while (i < len) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                width += this.getGlyph(cp).width * this.scale;
            }
            return Math.round(width);
        }

        private Glyph getGlyph(int codePoint) {
            Glyph glyph = this.glyphs.get(codePoint);
            if (glyph == null) {
                glyph = this.renderGlyph(codePoint);
                this.glyphs.put(codePoint, glyph);
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

        private Glyph renderGlyph(int codePoint) {
            Font font = this.resolveFont(codePoint);
            String s = new String(Character.toChars(codePoint));
            Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            probe.setFont(font);
            FontMetrics metrics = probe.getFontMetrics();
            int width = Math.max(1, metrics.stringWidth(s));
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
            g.setFont(font);
            g.setColor(Color.WHITE);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            g.drawString(s, 0, baseline);
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

            // 保存完整 GL 状态，绘制后逐项恢复，避免污染后续世界 / 实体（皮肤）渲染。
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            float prevAlphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            boolean prevAlpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
            boolean prevTex2D = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            int prevBlendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
            int prevBlendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);

            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableTexture2D();
            GlStateManager.disableAlpha();
            GlStateManager.bindTexture(this.atlasTexture);

            Tessellator tessellator = Tessellator.getInstance();
            WorldRenderer wr = tessellator.getWorldRenderer();
            wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            float cursor = x;
            int i = 0, len = text.length();
            while (i < len) {
                int cp = text.codePointAt(i);
                i += Character.charCount(cp);
                Glyph glyph = this.getGlyph(cp);
                float glyphWidth = glyph.width * this.scale;
                float glyphHeight = glyph.height * this.scale;
                wr.pos(cursor, y, 0.0D).tex(glyph.u1, glyph.v1).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor + glyphWidth, y, 0.0D).tex(glyph.u2, glyph.v1).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor + glyphWidth, y + glyphHeight, 0.0D).tex(glyph.u2, glyph.v2).color(red, green, blue, alpha).endVertex();
                wr.pos(cursor, y + glyphHeight, 0.0D).tex(glyph.u1, glyph.v2).color(red, green, blue, alpha).endVertex();
                cursor += glyphWidth;
            }
            tessellator.draw();

            // Restore every state we touched.
            GlStateManager.bindTexture(prevTexture);
            if (prevAlpha) {
                GlStateManager.enableAlpha();
                GlStateManager.alphaFunc(prevAlphaFunc, prevAlphaRef);
            } else {
                GlStateManager.disableAlpha();
            }
            if (prevTex2D) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (prevBlend) {
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(prevBlendSrc, prevBlendDst);
            } else {
                GlStateManager.disableBlend();
            }
        }
    }
}
