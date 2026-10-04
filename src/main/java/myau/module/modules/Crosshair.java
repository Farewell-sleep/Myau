package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * CROSSHAIR — 现代化动态准星：呼吸圆环（drawCircleOutline）、中心圆点（fillCircle）、
 * 随视角移动漂移、主题蓝/彩虹/自定义色。视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class Crosshair extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long START = System.nanoTime();
    private static final int ACCENT = 0xFF3B82F6; // 规范 ACCENT

    public final ModeProperty colorMode = new ModeProperty("color", 1, new String[]{"RAINBOW", "ACCENT", "CUSTOM"});
    public final ColorProperty customColor = new ColorProperty("custom-color", java.awt.Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 2);
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

        RenderUtil.enableRenderState();
        RenderUtil.drawCircleOutline(cx, cy, radius, 48, thick, color);
        if (this.centerDot.getValue()) {
            float dot = Math.max(thick * 0.9F, 0.75F);
            RenderUtil.fillCircle(cx, cy, dot, 16, color);
        }
        RenderUtil.disableRenderState();
    }

    private int getColor(float t) {
        switch (this.colorMode.getValue()) {
            case 0:
                return ColorUtil.fromHSB(t * 60.0F % 360.0F / 360.0F, 0.75F, 1.0F).getRGB() | 0xFF000000;
            case 2:
                return this.customColor.getValue() | 0xFF000000;
            default:
                return ACCENT; // 规范主题蓝
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
}
