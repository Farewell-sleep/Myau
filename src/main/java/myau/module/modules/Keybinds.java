package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.risefont.RiseFontManager;
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
 * 现代化按键列表：每行玻璃圆角卡片（键位芯片 + 模块名 FontManager），
 * 启用态高亮。视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class Keybinds extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int GLASS_BODY = 0xB91A2028;
    private static final int GLASS_OUTLINE = 0x2AFFFFFF;
    private static final int CHIP_BODY = 0x40FFFFFF;
    private static final int TEXT_MAIN = 0xFFF2F4F8;
    private static final int TEXT_DIM = 0xFF8A92A6;
    private static final int ENABLED = 0xFF4ADE80;
    private static final float FS = 12.0F;

    public final BooleanProperty background = new BooleanProperty("background", false);
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
        float rowH = (float) RiseFontManager.getFontHeight() + 4.0F;
        float gap = 2.0F;

        GlStateManager.pushMatrix();
        try {
            GlStateManager.scale(scale, scale, 1.0F);
            float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F) / scale;
            float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F) / scale;

            RenderUtil.enableRenderState();
            float rowY = y;
            for (Module m : bound) {
                String keyName = KeyBindUtil.getKeyName(m.getKey());
                String modName = m.getName();
                float keyW = (float) RiseFontManager.getStringWidth(keyName, FS);
                float nameW = (float) RiseFontManager.getStringWidth(modName, FS);
                float boxW = keyW + 6.0F + nameW;
                if (this.background.getValue()) {
                    RenderUtil.drawRoundedRect(x, rowY, boxW, rowH, 4.0F, 0xB91A2028);
                }
                // 纯文字直接显示：按键 + 模块名，无框无芯片
                RiseFontManager.drawString(keyName, x, rowY + 2.0F, TEXT_DIM, false, FS);
                int nameColor = this.colored.getValue() && m.isEnabled() ? ENABLED : TEXT_MAIN;
                RiseFontManager.drawString(modName, x + keyW + 6.0F, rowY + 2.0F, nameColor, false, FS);
                rowY += rowH + gap;
            }
            RenderUtil.disableRenderState();
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Onxy"};
    }
}
