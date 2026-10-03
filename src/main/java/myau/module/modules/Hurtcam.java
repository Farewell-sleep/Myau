package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * HURTCAM — skidded from Onxy (HurtcamModule). Soft coloured flash around
 * the screen when you take damage, drawn as a 2D edge gradient.
 */
public class Hurtcam extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty intensity = new FloatProperty("Intensity", 45.0F, 5.0F, 100.0F);
    public final FloatProperty size = new FloatProperty("Size", 55.0F, 10.0F, 100.0F);
    public final IntProperty duration = new IntProperty("Duration", 450, 100, 1500);
    public final BooleanProperty scaleWithDamage = new BooleanProperty("Scale with damage", true);
    public final BooleanProperty directional = new BooleanProperty("Directional", false);
    public final ColorProperty color = new ColorProperty("Color", 0xFF4900);

    private float wash;
    private float washSpeed;
    private float bias;
    private int lastHurtTime;
    private long lastFrame;

    public Hurtcam() {
        super("Hurtcam", false);
    }

    @Override
    public void onDisabled() {
        wash = 0.0F;
        washSpeed = 0.0F;
        bias = 0.0F;
        lastHurtTime = 0;
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        long now = System.nanoTime();
        float frameMs = lastFrame == 0L ? 16.0F : Math.min((float) (now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;

        int hurt = mc.thePlayer.hurtTime;
        if (hurt > lastHurtTime) {
            washSpeed = 1.0F;
            if (this.scaleWithDamage.getValue()) {
                float max = Math.max(1.0F, (float) mc.thePlayer.maxHurtTime);
                washSpeed = MathHelper.clamp_float(hurt / max, 0.35F, 1.0F);
            }
            wash = Math.max(wash, washSpeed);
            bias = mc.thePlayer.attackedAtYaw;
        }
        lastHurtTime = hurt;

        float dur = this.duration.getValue();
        wash -= (dur <= 0.0F ? wash : frameMs / dur * washSpeed);
        if (wash < 0.0F) {
            wash = 0.0F;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || wash <= 0.002F) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth();
        int h = sr.getScaledHeight();

        float strength = wash * (this.intensity.getValue() / 100.0F);
        float reach = this.size.getValue() / 100.0F;

        int rgb = this.color.getValue();
        Color c = new Color(rgb);
        float r = c.getRed() / 255.0F;
        float g = c.getGreen() / 255.0F;
        float b = c.getBlue() / 255.0F;

        // directional: shift the wash towards where the hit came from
        float dirX = 0.0F;
        if (this.directional.getValue()) {
            float diff = wrapDegrees(mc.thePlayer.attackedAtYaw - mc.thePlayer.rotationYaw);
            dirX = diff / 180.0F;
        }

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableAlpha();

        // top
        drawGradientQuad(0, 0, w, (int) (h * reach), strength, r, g, b, false, 0.0F, 0.0F, dirX);
        // bottom
        drawGradientQuad(0, h - (int) (h * reach), w, h, strength, r, g, b, true, 0.0F, 0.0F, dirX);
        // left
        drawGradientQuad(0, 0, (int) (w * reach), h, strength, r, g, b, false, 1.0F, 0.0F, dirX);
        // right
        drawGradientQuad(w - (int) (w * reach), 0, w, h, strength, r, g, b, false, -1.0F, 0.0F, dirX);

        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.resetColor();
    }

    private void drawGradientQuad(int x1, int y1, int x2, int y2, float strength, float r, float g, float b,
                                  boolean invert, float axisX, float axisY, float dirX) {
        // strength is modulated by the directional bias along the x axis
        float biasAmt = 1.0F + dirX * axisX * 0.6F;
        float aOuter = Math.max(0.0F, Math.min(1.0F, strength * biasAmt));

        GL11.glBegin(GL11.GL_QUADS);
        // edge (opaque) -> inner (transparent)
        if (invert) {
            // bottom: edge at bottom
            GL11.glColor4f(r, g, b, 0.0F);
            GL11.glVertex2f(x1, y1);
            GL11.glColor4f(r, g, b, 0.0F);
            GL11.glVertex2f(x2, y1);
            GL11.glColor4f(r, g, b, aOuter);
            GL11.glVertex2f(x2, y2);
            GL11.glColor4f(r, g, b, aOuter);
            GL11.glVertex2f(x1, y2);
        } else {
            // top/left/right: edge at the outer side
            GL11.glColor4f(r, g, b, aOuter);
            GL11.glVertex2f(x1, y1);
            GL11.glColor4f(r, g, b, aOuter);
            GL11.glVertex2f(x2, y1);
            GL11.glColor4f(r, g, b, 0.0F);
            GL11.glVertex2f(x2, y2);
            GL11.glColor4f(r, g, b, 0.0F);
            GL11.glVertex2f(x1, y2);
        }
        GL11.glEnd();
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
}
