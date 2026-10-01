package myau.ui;

import myau.util.FontManager;
import myau.util.RenderUtil;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Modern Darkheart main menu: animated gradient background with drifting
 * particles and soft glows, centered logo and rounded glass buttons.
 */
public class MainMenuScreen extends GuiScreen {

    private static final int BTN_W = 220;
    private static final int BTN_H = 34;
    private static final float BTN_GAP = 12.0F;
    private static final String[] BUTTONS = {"Singleplayer", "Multiplayer", "Options", "Quit"};

    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final Color accent = new Color(110, 170, 255);
    private long openedAt;
    private int hoveredButton = -1;
    private float[] buttonAnims = new float[BUTTONS.length];
    private float logoGlow;

    private static final class Particle {
        float x, y, speed, size;
        float phase;
    }

    @Override
    public void initGui() {
        this.openedAt = System.currentTimeMillis();
        if (particles.isEmpty()) {
            for (int i = 0; i < 90; i++) {
                Particle p = new Particle();
                p.x = random.nextFloat() * this.width;
                p.y = random.nextFloat() * this.height;
                p.speed = 6.0F + random.nextFloat() * 14.0F;
                p.size = 0.8F + random.nextFloat() * 1.8F;
                p.phase = random.nextFloat() * (float) Math.PI * 2.0F;
                particles.add(p);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float t = (now - openedAt) / 1000.0F;
        float fade = Math.min(1.0F, (now - openedAt) / 700.0F);

        // vertical gradient backdrop
        drawVGradient(0, 0, this.width, this.height, 0xFF0A0C12, 0xFF06070B);

        // drifting soft glows
        drawGlow(this.width * (0.22F + 0.05F * (float) Math.sin(t * 0.22F)), this.height * 0.30F, 240.0F + 30.0F * (float) Math.sin(t * 0.35F), accent, 60);
        drawGlow(this.width * (0.78F + 0.05F * (float) Math.cos(t * 0.18F)), this.height * 0.72F, 300.0F + 40.0F * (float) Math.cos(t * 0.28F), new Color(90, 130, 255), 42);
        drawGlow(this.width * 0.5F, this.height * 0.52F, 520.0F, new Color(30, 40, 70), 55);

        // drifting particles
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, fade);
        for (Particle p : particles) {
            p.y -= p.speed * partialTicks;
            if (p.y < -5.0F) {
                p.y = this.height + 5.0F;
                p.x = random.nextFloat() * this.width;
            }
            float alpha = (0.25F + 0.55F * (0.5F + 0.5F * (float) Math.sin(t * 0.8F + p.phase))) * fade;
            RenderUtil.drawRect(p.x, p.y, p.x + p.size, p.y + p.size, rgba(190, 205, 235, (int) (255 * alpha)));
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        drawLogo(this.width / 2.0F, this.height * 0.30F, fade, t);
        drawButtons(this.width / 2.0F, this.height * 0.52F, mouseX, mouseY, fade, t);
        drawFooter(t, fade);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawLogo(float cx, float cy, float fade, float t) {
        float scale = 0.94F + 0.06F * (float) Math.sin(t * 0.9F);
        float size = 46.0F * scale;
        String a = "Dark";
        String b = "heart";
        float aw = FontManager.getStringWidth(a, size);
        float bw = FontManager.getStringWidth(b, size);
        float total = aw + 4.0F + bw;
        float x0 = cx - total / 2.0F;
        float base = cy + FontManager.getCapHeight(size) / 2.0F;
        FontManager.drawString(a, x0, base - FontManager.getBaseline(size), rgba(242, 245, 250, (int) (255 * fade)), false, size);
        FontManager.drawString(b, x0 + aw + 4.0F, base - FontManager.getBaseline(size), rgba(accent, (int) (255 * fade)), false, size);

        // glow underline
        float uw = total + 26.0F;
        float ux = cx - uw / 2.0F;
        float uy = cy + size * 0.72F;
        float pulse = 0.55F + 0.45F * (float) Math.sin(t * 1.2F);
        RenderUtil.drawRoundedRectGradientH(ux, uy, cx - 2.0F, uy + 2.2F, 1.1F,
                rgba(accent, (int) (20 * fade * pulse)), rgba(accent, (int) (150 * fade * pulse)));
        RenderUtil.drawRoundedRectGradientH(cx + 2.0F, uy, ux + uw, uy + 2.2F, 1.1F,
                rgba(accent, (int) (150 * fade * pulse)), rgba(accent, (int) (20 * fade * pulse)));

        String tag = "A modern 1.8.9 utility client";
        float ts = 13.0F;
        FontManager.drawString(tag, cx - FontManager.getStringWidth(tag, ts) / 2.0F, uy + 18.0F,
                rgba(140, 150, 170, (int) (210 * fade)), false, ts);
    }

    private void drawButtons(float cx, float startY, int mouseX, int mouseY, float fade, float t) {
        float totalH = BUTTONS.length * BTN_H + (BUTTONS.length - 1) * BTN_GAP;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = startY + i * (BTN_H + BTN_GAP) - totalH / 2.0F;
            boolean hovered = mouseX >= cx - BTN_W / 2.0F && mouseX <= cx + BTN_W / 2.0F
                    && mouseY >= by && mouseY <= by + BTN_H;
            float target = hovered ? 1.0F : 0.0F;
            buttonAnims[i] += (target - buttonAnims[i]) * 0.14F;
            float a = buttonAnims[i];

            int bg = rgba(20, 22, 30, (int) ((120 + 60 * a) * fade));
            int border = rgba(accent, (int) ((50 + 120 * a) * fade));
            RenderUtil.drawRoundedRectWithGl(cx - BTN_W / 2.0F - 0.75F, by - 0.75F, cx + BTN_W / 2.0F + 0.75F, by + BTN_H + 0.75F, 10.5F, border);
            RenderUtil.drawRoundedRectWithGl(cx - BTN_W / 2.0F, by, cx + BTN_W / 2.0F, by + BTN_H, 9.75F, bg);

            // left accent bar that slides in on hover
            if (a > 0.01F) {
                float barW = 3.0F + a * 2.0F;
                RenderUtil.drawRoundedRectWithGl(cx - BTN_W / 2.0F + 8.0F, by + 6.0F, cx - BTN_W / 2.0F + 8.0F + barW, by + BTN_H - 6.0F, 1.5F,
                        rgba(accent, (int) (220 * a * fade)));
            }

            int textColor = rgba(mix(new Color(170, 177, 192), new Color(245, 247, 252), a), (int) (255 * fade));
            float textSize = 16.0F;
            float dx = a * 3.0F;
            FontManager.drawString(BUTTONS[i], cx - FontManager.getStringWidth(BUTTONS[i], textSize) / 2.0F + dx,
                    by + BTN_H / 2.0F - FontManager.getBaseline(textSize) + FontManager.getCapHeight(textSize) / 2.0F,
                    textColor, false, textSize);
        }
    }

    private void drawFooter(float t, float fade) {
        String version = "Darkheart 1.0.0   -   MC 1.8.9";
        float vs = 12.0F;
        FontManager.drawString(version, (this.width - FontManager.getStringWidth(version, vs)) / 2.0F, this.height - 34.0F,
                rgba(125, 134, 152, (int) (200 * fade)), false, vs);
        String credit = "Built with pride - github.com/Farewell-sleep/Myau";
        float cs = 11.0F;
        FontManager.drawString(credit, (this.width - FontManager.getStringWidth(credit, cs)) / 2.0F, this.height - 20.0F,
                rgba(90, 98, 115, (int) (160 * fade)), false, cs);
    }

    private void drawGlow(float cx, float cy, float radius, Color color, int alpha) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        int segments = 48;
        wr.pos(cx, cy, 0.0D).color(color.getRed(), color.getGreen(), color.getBlue(), 0).endVertex();
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double px = cx + Math.cos(angle) * radius;
            double py = cy + Math.sin(angle) * radius;
            wr.pos(px, py, 0.0D).color(color.getRed(), color.getGreen(), color.getBlue(), alpha).endVertex();
        }
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void drawVGradient(float x1, float y1, float x2, float y2, int c1, int c2) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        wr.pos(x1, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, c1 >> 24 & 255).endVertex();
        wr.pos(x1, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, c2 >> 24 & 255).endVertex();
        wr.pos(x2, y2, 0.0D).color(c2 >> 16 & 255, c2 >> 8 & 255, c2 & 255, c2 >> 24 & 255).endVertex();
        wr.pos(x2, y1, 0.0D).color(c1 >> 16 & 255, c1 >> 8 & 255, c1 & 255, c1 >> 24 & 255).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private Color mix(Color a, Color b, float t) {
        t = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static int rgba(Color c, int a) {
        return rgba(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        float cx = this.width / 2.0F;
        float startY = this.height * 0.52F;
        float totalH = BUTTONS.length * BTN_H + (BUTTONS.length - 1) * BTN_GAP;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = startY + i * (BTN_H + BTN_GAP) - totalH / 2.0F;
            if (mouseX >= cx - BTN_W / 2.0F && mouseX <= cx + BTN_W / 2.0F && mouseY >= by && mouseY <= by + BTN_H) {
                switch (i) {
                    case 0:
                        this.mc.displayGuiScreen(new GuiSelectWorld(this));
                        break;
                    case 1:
                        this.mc.displayGuiScreen(new GuiMultiplayer(this));
                        break;
                    case 2:
                        this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                        break;
                    case 3:
                        this.mc.shutdown();
                        break;
                }
                return;
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
