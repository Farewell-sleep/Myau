package myau.risefont;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.Character.UnicodeScript;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * Skidded from Rise (com.alan.clients.util.font.impl.rise.FontRenderer).
 * Rise-style texture-atlas font renderer with per-character GL isolation
 * (push/pop attrib + matrix), so it never pollutes world / entity rendering.
 */
public class RiseFontRenderer extends RiseFont {
    private static final String COLOR_CODE_CHARACTERS = "0123456789abcdefklmnor";
    private static final Color TRANSPARENT_COLOR = new Color(255, 255, 255, 0);
    private static final float SCALE = 0.5F;
    private static final char COLOR_INVOKER = 167;
    private static final int[] COLOR_CODES = new int[32];
    private static final int LATIN_MAX_AMOUNT = 256;
    private static final int INTERNATIONAL_MAX_AMOUNT = 65535;
    private final Font font;
    private final boolean fractionalMetrics;
    private final float fontHeight;
    private final RiseFontCharacter[] defaultCharacters = new RiseFontCharacter[LATIN_MAX_AMOUNT];
    private final RiseFontCharacter[] internationalCharacters = new RiseFontCharacter[INTERNATIONAL_MAX_AMOUNT];
    private final RiseFontCharacter[] boldCharacters = new RiseFontCharacter[LATIN_MAX_AMOUNT];
    private boolean antiAlias = true;
    private boolean internationalEnabled = false;
    private RiseGlyphCache harmonyCjk;
    private RiseGlyphCache japanese;
    private RiseGlyphCache korean;
    private RiseGlyphCache fallback;

    public void setCjkCache(RiseGlyphCache cache) {
        this.harmonyCjk = cache;
    }

    public void setJapaneseCache(RiseGlyphCache cache) {
        this.japanese = cache;
    }

    public void setKoreanCache(RiseGlyphCache cache) {
        this.korean = cache;
    }

    public void setFallbackCache(RiseGlyphCache cache) {
        this.fallback = cache;
    }

    public RiseFontRenderer(Font font, boolean fractionalMetrics, boolean antiAlias, boolean international) {
        calculateColorCodes();
        this.antiAlias = antiAlias;
        this.font = font;
        this.fractionalMetrics = fractionalMetrics;
        this.fontHeight = (float) (font.getStringBounds("ABCDEFGHOKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz",
                        new FontRenderContext(new AffineTransform(), antiAlias, fractionalMetrics)).getHeight() / 2.0);
        this.fillCharacters(this.defaultCharacters, 0);
        this.fillCharacters(this.boldCharacters, 1);
        this.internationalEnabled = international;
        if (this.internationalEnabled) {
            this.fillCharacters(this.internationalCharacters, 0);
        }
    }

    public RiseFontRenderer(Font font, boolean fractionalMetrics, boolean antiAlias) {
        this(font, fractionalMetrics, antiAlias, false);
    }

    public static void calculateColorCodes() {
        for (int i = 0; i < 32; i++) {
            int j = (i >> 3 & 1) * 85;
            int k = (i >> 2 & 1) * 170 + j;
            int l = (i >> 1 & 1) * 170 + j;
            int i1 = (i & 1) * 170 + j;
            if (i == 6) {
                k += 85;
            }
            if (i >= 16) {
                k /= 4;
                l /= 4;
                i1 /= 4;
            }
            COLOR_CODES[i] = (k & 0xFF) << 16 | (l & 0xFF) << 8 | i1 & 0xFF;
        }
    }

    public void fillCharacters(RiseFontCharacter[] characters, int style) {
        Font f = this.font.deriveFont(style);
        Graphics2D g2 = (Graphics2D) new BufferedImage(1, 1, 2).getGraphics();
        FontMetrics metrics = g2.getFontMetrics(f);
        for (int i = 0; i < characters.length; i++) {
            char c = (char) i;
            Rectangle2D bounds = metrics.getStringBounds(c + "", g2);
            BufferedImage image = new BufferedImage(MathHelper.ceiling_float_int((float) bounds.getWidth()) + 8,
                    MathHelper.ceiling_float_int((float) bounds.getHeight()), 2);
            Graphics2D imgG = (Graphics2D) image.getGraphics();
            imgG.setFont(f);
            int w = image.getWidth();
            int h = image.getHeight();
            imgG.setColor(TRANSPARENT_COLOR);
            imgG.fillRect(0, 0, w, h);
            this.setRenderHints(imgG);
            imgG.drawString(c + "", 4, font.getSize());
            int texture = GL11.glGenTextures();
            this.uploadTexture(texture, image, w, h);
            characters[i] = new RiseFontCharacter(texture, w, h);
        }
    }

    public void setRenderHints(Graphics2D g) {
        g.setColor(Color.WHITE);
        if (this.antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                this.fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    public void uploadTexture(int texture, BufferedImage image, int width, int height) {
        int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = pixels[x + y * width];
                buffer.put((byte) (p >> 16 & 0xFF));
                buffer.put((byte) (p >> 8 & 0xFF));
                buffer.put((byte) (p & 0xFF));
                buffer.put((byte) (p >> 24 & 0xFF));
            }
        }
        buffer.flip();
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(3553, 10241, 9728);
        GL11.glTexParameteri(3553, 10240, 9729);
        GL11.glTexImage2D(3553, 0, 6408, width, height, 0, 6408, 5121, buffer);
    }

    @Override
    public int a(String text, double x, double y, int color) {
        return this.b(text, x, y, color, false);
    }

    @Override
    public int drawString(String text, double x, double y, int color) {
        return this.b(text, x - (this.getStringWidth(text) >> 1), y, color, false);
    }

    @Override
    public int drawCenteredString(String text, double x, double y, int color) {
        return this.b(text, x - this.getStringWidth(text), y, color, false);
    }

    @Override
    public int b(String text, double x, double y, int color) {
        return this.b(text, x, y, color, false);
    }

    public void drawCenteredStringWithShadow(String text, float x, float y, int color) {
        this.b(text, x - (this.getStringWidth(text) >> 1), y, color, false);
    }

    @Override
    public int b(String text, double x, double y, int color, boolean shadow) {
        if (text == null) {
            return 0;
        }
        if (requiresInternationalFont(text)) {
            return Minecraft.getMinecraft().fontRendererObj.drawString(text, (int) x, (int) y, color, shadow);
        }
        RiseFontCharacter[] chars = this.internationalEnabled ? this.internationalCharacters : this.defaultCharacters;
        double startX = x;
        GL11.glPushMatrix();
        GL11.glPushAttrib(1048575);
        try {
            GL11.glEnable(3553);
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glScalef(SCALE, SCALE, SCALE);
            double d1 = x - 2.0;
            double d2 = y - 2.0;
            double d3 = d1 * 2.0;
            double d4 = d2 * 2.0;
            // Top-left semantics (vanilla FontRenderer): glyph top-left lands
            // on (x, y). Rise's original d5 = d4 - fontHeight/5 shifted every
            // glyph ~1.2px above the requested y, which mis-aligned ClickGUI
            // labels against their capsules/sliders.
            double d5 = d4;
            double d6 = d3;
            glColor(shadow ? Color.WHITE.getRGB() : color);
            String s = text.replaceAll("\u00a7l", "");
            try {
                char[] array = s.toCharArray();
                int lineHeight = (int) (this.height() * 2.0F);
                for (int i = 0; i < array.length; i++) {
                    char c = array[i];
                    if (c == '\n') {
                        d3 = d6;
                        d5 += lineHeight;
                    } else if (c == COLOR_INVOKER && i + 1 < array.length) {
                        int code = COLOR_CODE_CHARACTERS.indexOf(array[++i]);
                        if (code >= 0 && code < COLOR_CODES.length) {
                            glColor(new Color(COLOR_CODES[code]));
                        }
                    } else {
                        if (c >= 0 && c < chars.length) {
                            RiseFontCharacter character = chars[c];
                            if (character == null) {
                                this.renderFallback(c, (float) d3, (float) d5);
                            } else {
                                float width = character.getWidth();
                                character.render((float) d3, (float) d5);
                                d3 += width - 8.0F;
                            }
                        } else {
                            this.renderFallback(c, (float) d3, (float) d5);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return (int) (d3 - startX);
        } finally {
            GL11.glDisable(3042);
            GL11.glDisable(3553);
            GlStateManager.bindTexture(0);
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private void renderFallback(char c, float x, float y) {
        if (this.harmonyCjk != null && isCjk(c)) {
            this.harmonyCjk.render(c, x, y);
            return;
        }
        if (this.japanese != null && isJapanese(c)) {
            this.japanese.render(c, x, y);
            return;
        }
        if (this.korean != null && isKorean(c)) {
            this.korean.render(c, x, y);
            return;
        }
        if (this.fallback != null) {
            this.fallback.render(c, x, y);
        }
    }

    private float fallbackWidth(char c) {
        if (this.harmonyCjk != null && isCjk(c)) {
            return this.harmonyCjk.getWidth(c);
        }
        if (this.japanese != null && isJapanese(c)) {
            return this.japanese.getWidth(c);
        }
        if (this.korean != null && isKorean(c)) {
            return this.korean.getWidth(c);
        }
        if (this.fallback != null) {
            return this.fallback.getWidth(c);
        }
        return 0.0F;
    }

    @Override
    public void a(char c, int x, int y, Color color) {
        RiseFontCharacter[] chars = this.internationalEnabled ? this.internationalCharacters : this.defaultCharacters;
        if (c < chars.length && chars[c] != null) {
            RiseFontCharacter character = chars[c];
            GlStateManager.color(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                    color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
            character.render(x, y);
        }
    }

    @Override
    public int getStringWidth(String text) {
        String s = text.replaceAll("\u00a7l", "");
        if (requiresInternationalFont(s)) {
            return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);
        }
        RiseFontCharacter[] chars = this.internationalEnabled ? this.internationalCharacters : this.defaultCharacters;
        int length = s.length();
        int width = 0;
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            if (c == COLOR_INVOKER) {
                i++;
            } else {
                if (c >= 0 && c < chars.length) {
                    RiseFontCharacter character = chars[c];
                    if (character == null) {
                        width = (int) (width + this.fallbackWidth(c));
                    } else {
                        width = (int) (width + (character.getWidth() - 8.0F));
                    }
                } else {
                    width = (int) (width + this.fallbackWidth(c));
                }
            }
        }
        return width / 2;
    }

    @Override
    public float height() {
        return this.fontHeight;
    }

    private static boolean isCjk(char c) {
        if (UnicodeScript.of(c) == UnicodeScript.HAN) {
            return true;
        }
        if (c >= 12288 && c <= 12351) {
            return true;
        }
        return (c >= '\uff00' && c <= '\uffef') || c == 8226 || c == 183 || c == 8230 || c == 8211 || c == 8212
                || (c >= 8216 && c <= 8223);
    }

    private static boolean isJapanese(char c) {
        if (c >= 12352 && c <= 12447) {
            return true;
        }
        if (c >= 12448 && c <= 12543) {
            return true;
        }
        return (c >= 12784 && c <= 12799) || (c >= '･' && c <= 'ﾟ');
    }

    private static boolean isKorean(char c) {
        if (c >= 4352 && c <= 4607) {
            return true;
        }
        if (c >= 12592 && c <= 12687) {
            return true;
        }
        if (c >= '가' && c <= '\ud7af') {
            return true;
        }
        return (c >= 'ꥠ' && c <= '\ua97f') || (c >= 'ힰ' && c <= '\ud7ff');
    }

    private static boolean requiresInternationalFont(String text) {
        if (text != null && !text.isEmpty()) {
            for (int i = 0; i < text.length(); i += Character.charCount(text.codePointAt(i))) {
                int cp = text.codePointAt(i);
                if (cp > 127 && !isCjk((char) cp) && !isJapanese((char) cp) && !isKorean((char) cp)) {
                    UnicodeScript script = UnicodeScript.of(cp);
                    if (script != UnicodeScript.CYRILLIC && script != UnicodeScript.LATIN
                            && script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED) {
                        return true;
                    }
                }
            }
            return false;
        }
        return false;
    }

    private static void glColor(int color) {
        float a = (color >> 24 & 0xFF) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        GL11.glColor4f(r, g, b, a);
    }

    private static void glColor(Color color) {
        GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F,
                color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
    }
}
