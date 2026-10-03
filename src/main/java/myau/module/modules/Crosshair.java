package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * CROSSHAIR — skidded from Onxy (CrosshairModule). Animated dynamic
 * crosshair: breathing ring, center dot, drifts with view movement,
 * rainbow / accent / custom colour.
 */
public class Crosshair extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long START = System.nanoTime();

    public final ModeProperty colorMode = new ModeProperty("color", 1, new String[]{"RAINBOW", "ACCENT", "CUSTOM"});
    public final ColorProperty customColor = new ColorProperty("custom-color", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 2);
    public final BooleanProperty centerDot = new BooleanProperty("center-dot", true);
    public final BooleanProperty pulse = new BooleanProperty("pulse", true);
    public final FloatProperty size = new FloatProperty("size", 5.0F, 2.0F, 16.0F);
    public final FloatProperty thickness = new FloatProperty("thickness", 1.5F, 0.5F, 4.0F);
    public final FloatProperty follow = new FloatProperty("follow", 40.0F, 0.0F, 100.0F);

    private float prevYaw;
    private float prevPitch;
    private boolean initialized;
    private float driftX;
    private float driftY;
    private long lastFrame;
    private float animTime;

    public Crosshair() {
        super("Crosshair", false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.gameSettings.thirdPersonView != 0) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float w = sr.getScaledWidth();
        float h = sr.getScaledHeight();

        float frameMs = 16.0F;
        long now = System.nanoTime();
        if (lastFrame != 0L) {
            frameMs = Math.min((float) (now - lastFrame) / 1000000.0F, 100.0F);
        }
        lastFrame = now;
        animTime += frameMs;

        // drift with view movement
        float yaw = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;
        if (!initialized) {
            initialized = true;
            prevYaw = yaw;
            prevPitch = pitch;
        }
        float dyaw = wrapDegrees(yaw - prevYaw);
        float dpitch = pitch - prevPitch;
        prevYaw = yaw;
        prevPitch = pitch;
        float followAmt = this.follow.getValue() / 100.0F * 1.1F;
        float limit = this.size.getValue() * 2.0F;
        float tx = Math.max(-limit, Math.min(limit, -dyaw * followAmt));
        float ty = Math.max(-limit, Math.min(limit, -dpitch * followAmt));
        float lerp = 1.0F - (float) Math.exp(-frameMs / 70.0F);
        driftX += (tx - driftX) * lerp;
        driftY += (ty - driftY) * lerp;

        float t = (float) (now - START) / 1.0E9F;
        float pulseScale = this.pulse.getValue() ? 1.0F + 0.05F * (float) Math.sin(t * 2.2F) : 1.0F;
        float radius = this.size.getValue() * pulseScale;
        float thick = this.thickness.getValue();
        int color = getColor(t);

        float cx = w / 2.0F + driftX;
        float cy = h / 2.0F + driftY;

        drawRing(cx, cy, radius, thick, color);
        if (this.centerDot.getValue()) {
            float dot = Math.max(thick * 0.9F, 0.75F);
            drawDot(cx, cy, dot, color);
        }
    }

    private int getColor(float t) {
        switch (this.colorMode.getValue()) {
            case 0:
                return ColorUtil.fromHSB(t * 60.0F % 360.0F / 360.0F, 0.75F, 1.0F).getRGB() | 0xFF000000;
            case 2:
                return this.customColor.getValue() | 0xFF000000;
            default:
                return 0xFF00E5FF; // accent (onyx cyan)
        }
    }

    private static float wrapDegrees(float v) {
        float x = v % 360.0F;
        if (x >= 180.0F) {
            x -= 360.0F;
        }
        if (x < -180.0F) {
            x += 360.0F;
        }
        return x;
    }

    private static void drawRing(float cx, float cy, float radius, float thick, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        Color c = new Color(color);
        GL11.glLineWidth(thick);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < 48; i++) {
            double a = i / 48.0 * Math.PI * 2.0;
            GL11.glColor4f(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
            GL11.glVertex2f((float) (cx + Math.cos(a) * radius), (float) (cy + Math.sin(a) * radius));
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void drawDot(float cx, float cy, float r, int color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Color c = new Color(color);
        GL11.glColor4f(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, c.getAlpha() / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx - r, cy - r);
        GL11.glVertex2f(cx + r, cy - r);
        GL11.glVertex2f(cx + r, cy + r);
        GL11.glVertex2f(cx - r, cy + r);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
