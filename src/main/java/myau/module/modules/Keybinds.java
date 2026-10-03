package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.util.KeyBindUtil;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Onxy-style keybind list HUD, rewritten with Myau native rendering.
 */
public class Keybinds extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty background = new BooleanProperty("background", true);
    public final BooleanProperty colored = new BooleanProperty("enabled-color", true);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final PercentProperty posX = new PercentProperty("position-x", 2);
    public final PercentProperty posY = new PercentProperty("position-y", 35);

    public Keybinds() {
        super("Keybinds", false, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        List<Module> bound = new ArrayList<>();
        for (Module module : OpenMyau.moduleManager.modules.values()) {
            if (module.getKey() != 0) {
                bound.add(module);
            }
        }
        if (bound.isEmpty()) {
            return;
        }
        bound.sort(Comparator.comparing(m -> m.getName().toLowerCase()));

        ScaledResolution sr = new ScaledResolution(mc);
        float scale = this.scale.getValue();
        int maxW = 0;
        for (Module m : bound) {
            String line = m.getName() + ": " + KeyBindUtil.getKeyName(m.getKey());
            maxW = Math.max(maxW, mc.fontRendererObj.getStringWidth(line));
        }
        float boxW = (float) (maxW + 10) * scale;
        float boxH = (float) (bound.size() * mc.fontRendererObj.FONT_HEIGHT + 6) * scale;
        float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F);
        float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float sx = x / scale;
        float sy = y / scale;
        if (this.background.getValue()) {
            RenderUtil.drawRect(sx, sy, sx + boxW / scale, sy + boxH / scale, new Color(0, 0, 0, 90).getRGB());
        }
        float rowY = sy + 3.0F;
        for (Module m : bound) {
            String line = m.getName() + ": " + KeyBindUtil.getKeyName(m.getKey());
            int color = this.colored.getValue() && m.isEnabled() ? 0xFF55FF55 : 0xFFFFFFFF;
            mc.fontRendererObj.drawStringWithShadow(line, sx + 5.0F, rowY, color);
            rowY += (float) mc.fontRendererObj.FONT_HEIGHT;
        }
        GlStateManager.popMatrix();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Onxy"};
    }
}
