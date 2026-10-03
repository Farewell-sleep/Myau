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

/**
 * Myau main menu: Rise-style minimal dark backdrop, animated falling
 * client title and vanilla-sized buttons (200x20, original layout).
 */
public class MainMenuScreen extends GuiScreen {

    private static final int BTN_W = 200;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 24;
    private static final String[] BUTTONS = {"Singleplayer", "Multiplayer", "Options", "Quit"};

    private final Color accent = new Color(120, 170, 255);
    private long openedAt;
    private float titleSlide = 0.0F;
    private float fade = 0.0F;
    private int hoveredButton = -1;
    private float[] buttonAnims = new float[BUTTONS.length];

    @Override
    public void initGui() {
        this.openedAt = System.currentTimeMillis();
        this.titleSlide = 0.0F;
        this.fade = 0.0F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.currentTimeMillis();
        float t = (now - this.openedAt) / 1000.0F;
        this.fade = Math.min(1.0F, (now - this.openedAt) / 600.0F);

        // dark minimal backdrop, subtle vertical gradient
        drawVGradient(0, 0, this.width, this.height, 0xFF0B0D13, 0xFF04050A);

        // soft radial glow behind the title (very subtle)
        drawGlow(this.width / 2.0F, this.height * 0.20F, 320.0F, accent, 26);

        drawTitle(now, fade, t);
        drawButtons(mouseX, mouseY, fade);
        drawFooter(fade);

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawTitle(long now, float fade, float t) {
        // Rise-style title that slides down and settles
        float duration = 0.7F;
        float p = Math.min(1.0F, t / duration);
        p = 1.0F - (float) Math.pow(1.0F - p, 4.0); // ease-out quart
        float startY = this.height * 0.20F - 40.0F;
        float targetY = this.height * 0.20F;
        float y = startY + (targetY - startY) * p;

        float size = 44.0F;
        String title = "Myau";
        float tw = FontManager.getStringWidth(title, size);
        float alpha = 255.0F * fade;
        float x0 = this.width / 2.0F - tw / 2.0F;

        FontManager.drawString(title, x0, y - FontManager.getBaseline(size) + FontManager.getCapHeight(size) / 2.0F,
                rgba(246, 249, 253, (int) alpha), false, size);

        // thin accent underline
        float uw = tw * 0.82F;
        float uy = y + size * 0.62F;
        float pulse = 0.55F + 0.45F * (float) Math.sin(t * 1.1F);
        RenderUtil.drawRoundedRect(this.width / 2.0F - uw / 2.0F, uy, uw, 2.0F, 1.0F,
                rgba(accent, (int) (170 * fade * pulse)));

        // subtitle
        String sub = "a modern 1.8.9 utility client";
        float ss = 12.0F;
        FontManager.drawString(sub, this.width / 2.0F - FontManager.getStringWidth(sub, ss) / 2.0F,
                uy + 16.0F, rgba(140, 150, 168, (int) (200 * fade)), false, ss);
    }

    private void drawButtons(int mouseX, int mouseY, float fade) {
        // vanilla layout: 200x20 centered, y = height/4 + 48 + i*24
        float cx = this.width / 2.0F;
        float startY = this.height / 4.0F + 48.0F;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = startY + i * BTN_GAP;
            boolean hovered = mouseX >= cx - BTN_W / 2.0F && mouseX <= cx + BTN_W / 2.0F
                    && mouseY >= by && mouseY <= by + BTN_H;
            float target = hovered ? 1.0F : 0.0F;
            this.buttonAnims[i] += (target - this.buttonAnims[i]) * 0.16F;
            float a = this.buttonAnims[i];

            int bg = rgba(17, 19, 26, (int) ((130 + 70 * a) * fade));
            int border = rgba(accent, (int) ((40 + 110 * a) * fade));
            RenderUtil.drawRoundedRect(cx - BTN_W / 2.0F - 0.5F, by - 0.5F, BTN_W + 1.0F, BTN_H + 1.0F, 5.5F, border);
            RenderUtil.drawRoundedRect(cx - BTN_W / 2.0F, by, BTN_W, BTN_H, 5.0F, bg);

            // left accent bar on hover
            if (a > 0.01F) {
                RenderUtil.drawRoundedRect(cx - BTN_W / 2.0F + 6.0F, by + 4.0F, 2.0F + a * 2.0F, BTN_H - 8.0F, 1.0F,
                        rgba(accent, (int) (230 * a * fade)));
            }

            int textColor = rgba(mix(new Color(160, 167, 182), new Color(245, 247, 252), a), (int) (255 * fade));
            float textSize = 13.0F;
            float dx = a * 2.0F;
            FontManager.drawString(BUTTONS[i],
                    cx - FontManager.getStringWidth(BUTTONS[i], textSize) / 2.0F + dx,
                    by + BTN_H / 2.0F - FontManager.getBaseline(textSize) + FontManager.getCapHeight(textSize) / 2.0F,
                    textColor, false, textSize);
        }
    }

    private void drawFooter(float fade) {
        String version = "Myau 1.0.0   -   MC 1.8.9";
        float vs = 11.0F;
        FontManager.drawString(version, (this.width - FontManager.getStringWidth(version, vs)) / 2.0F,
                this.height - 26.0F, rgba(120, 129, 147, (int) (190 * fade)), false, vs);
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
        float startY = this.height / 4.0F + 48.0F;
        for (int i = 0; i < BUTTONS.length; i++) {
            float by = startY + i * BTN_GAP;
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
