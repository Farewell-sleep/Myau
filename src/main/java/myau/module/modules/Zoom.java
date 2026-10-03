package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import myau.module.Module;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

/**
 * ZOOM — skidded from Onxy (ZoomModule). Smooth hold-to-zoom camera.
 * Holds the bind (default C) to interpolate the FOV down to
 * fov / Distance with an eased animation; releasing restores it.
 */
public class Zoom extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Animation duration in ms (0 = instant). */
    public final IntProperty animation = new IntProperty("Animation", 180, 0, 500);
    /** Zoom strength (divide FOV by this). */
    public final FloatProperty distance = new FloatProperty("Distance", 4.0F, 1.5F, 12.0F);

    private float progress;
    private long lastNano;
    private boolean wasKeyDown;

    public Zoom() {
        super("Zoom", false);
        this.setKey(Keyboard.KEY_C);
    }

    @Override
    public void onDisabled() {
        progress = 0.0F;
        lastNano = 0L;
        wasKeyDown = false;
        if (mc.gameSettings != null && mc.thePlayer != null) {
            mc.gameSettings.fovSetting = restoreFov();
        }
    }

    private float restoreFov() {
        return 70.0F;
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            if (progress > 0.0F) {
                progress = 0.0F;
            }
            return;
        }
        long now = System.nanoTime();
        float delta = lastNano == 0L ? 16.0F : Math.min((float) (now - lastNano) / 1000000.0F, 100.0F);
        lastNano = now;

        boolean keyDown = Keyboard.isKeyDown(this.getKey());
        float target = keyDown && this.isEnabled() ? 1.0F : 0.0F;

        float anim = this.animation.getValue();
        if (anim <= 0.0F) {
            progress = target;
        } else {
            float t = Math.min(delta / anim, 1.0F);
            t = 1.0F - (float) Math.pow(1.0F - t, 3.0);
            progress += (target - progress) * t;
            if (Math.abs(target - progress) < 0.001F) {
                progress = target;
            }
        }

        float base = 70.0F;
        mc.gameSettings.fovSetting = base + (base / this.distance.getValue() - base) * ease(progress);
        wasKeyDown = keyDown;
    }

    private static float ease(float v) {
        float x = Math.max(0.0F, Math.min(1.0F, v));
        return x * x * (3.0F - 2.0F * x);
    }
}
