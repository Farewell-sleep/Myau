package myau.risefont;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLContext;

/**
 * Skidded from Rise (com.alan.clients.util.font.impl.rise.GlyphCache).
 * Per-glyph texture cache used for CJK / fallback characters.
 */
public final class RiseGlyphCache {
    private static final Color TRANSPARENT = new Color(255, 255, 255, 0);
    private final Font font;
    private final boolean fractionalMetrics;
    private final boolean antiAlias;
    private final ConcurrentHashMap<Integer, Float> widths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, RiseFontCharacter> glyphs = new ConcurrentHashMap<>();

    public RiseGlyphCache(Font font, boolean fractionalMetrics, boolean antiAlias) {
        this.font = font;
        this.fractionalMetrics = fractionalMetrics;
        this.antiAlias = antiAlias;
    }

    public float getWidth(char c) {
        return this.widths.computeIfAbsent((int) c, k -> this.measure(c));
    }

    public void render(char c, float x, float y) {
        RiseFontCharacter character = this.glyphs.get((int) c);
        if (character == null) {
            character = this.create(c);
            if (character == null) {
                return;
            }
            RiseFontCharacter existing = this.glyphs.putIfAbsent((int) c, character);
            if (existing != null) {
                character = existing;
            }
        }
        character.render(x, y);
    }

    private float measure(char c) {
        if (!this.font.canDisplay(c)) {
            return 0.0F;
        }
        Graphics2D graphics = (Graphics2D) new BufferedImage(1, 1, 2).getGraphics();
        graphics.setFont(this.font);
        this.applyHints(graphics);
        int width = MathHelper.ceiling_float_int((float) graphics.getFontMetrics(this.font).getStringBounds(String.valueOf(c), graphics).getWidth()) + 8;
        return Math.max(0.0F, (float) (width - 8));
    }

    private RiseFontCharacter create(char c) {
        if (!contextReady() || !this.font.canDisplay(c)) {
            return null;
        }
        Graphics2D probe = (Graphics2D) new BufferedImage(1, 1, 2).getGraphics();
        probe.setFont(this.font);
        this.applyHints(probe);
        Rectangle2D bounds = probe.getFontMetrics(this.font).getStringBounds(String.valueOf(c), probe);
        BufferedImage image = new BufferedImage(MathHelper.ceiling_float_int((float) bounds.getWidth()) + 8,
                MathHelper.ceiling_float_int((float) bounds.getHeight()), 2);
        Graphics2D g2 = (Graphics2D) image.getGraphics();
        g2.setFont(this.font);
        this.applyHints(g2);
        int width = image.getWidth();
        int height = image.getHeight();
        g2.setColor(TRANSPARENT);
        g2.fillRect(0, 0, width, height);
        g2.setColor(Color.WHITE);
        g2.drawString(String.valueOf(c), 4, this.font.getSize());
        int texture = GL11.glGenTextures();
        this.upload(texture, image, width, height);
        return new RiseFontCharacter(texture, width, height);
    }

    private static boolean contextReady() {
        try {
            if (!Display.isCreated()) {
                return false;
            }
            GLContext.getCapabilities();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyHints(Graphics2D g) {
        g.setColor(Color.WHITE);
        if (this.antiAlias) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                this.fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }

    private void upload(int texture, BufferedImage image, int width, int height) {
        int[] pixels = image.getRGB(0, 0, width, height, new int[width * height], 0, width);
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
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
}
