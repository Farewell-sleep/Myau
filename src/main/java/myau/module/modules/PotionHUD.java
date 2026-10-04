package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.FontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 现代化药水状态 HUD：玻璃卡片（圆角 6px + 发丝描边）、药水图标、
 * FontManager 名称/时长、效果色圆点。视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class PotionHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private static final int GLASS_BODY = 0xB91A2028;
    private static final int GLASS_OUTLINE = 0x2AFFFFFF;
    private static final int TEXT_MAIN = 0xFFF2F4F8;
    private static final int TEXT_DIM = 0xFF8A92A6;
    private static final float FS = 8.0F;

    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"DURATION", "NAME", "LEVEL"});
    public final BooleanProperty icons = new BooleanProperty("icons", true);
    public final BooleanProperty duration = new BooleanProperty("duration", true);
    public final BooleanProperty amplifier = new BooleanProperty("amplifier", true);
    public final BooleanProperty potionColor = new BooleanProperty("potion-color", true);
    public final BooleanProperty lowTimeWarn = new BooleanProperty("low-time-warning", true);
    public final BooleanProperty hideAmbient = new BooleanProperty("hide-ambient", false);
    public final BooleanProperty background = new BooleanProperty("background", true);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final PercentProperty posX = new PercentProperty("position-x", 96);
    public final PercentProperty posY = new PercentProperty("position-y", 12);

    public PotionHUD() {
        super("PotionHUD", false, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.gameSettings.showDebugInfo) {
            return;
        }
        List<PotionEffect> effects = new ArrayList<>(mc.thePlayer.getActivePotionEffects());
        if (this.hideAmbient.getValue()) {
            effects.removeIf(PotionEffect::getIsAmbient);
        }
        if (effects.isEmpty()) {
            return;
        }
        Comparator<PotionEffect> comparator;
        switch (this.sort.getValue()) {
            case 1:
                comparator = Comparator.comparing(this::getPotionName, String.CASE_INSENSITIVE_ORDER);
                break;
            case 2:
                comparator = Comparator.comparingInt((PotionEffect e) -> -e.getAmplifier())
                        .thenComparing(this::getPotionName, String.CASE_INSENSITIVE_ORDER);
                break;
            case 0:
            default:
                comparator = Comparator.comparingInt(PotionEffect::getDuration).reversed();
        }
        effects.sort(comparator);

        ScaledResolution sr = new ScaledResolution(mc);
        float scale = this.scale.getValue();
        float rowH = 20.0F;
        float iconOffset = this.icons.getValue() ? 18.0F : 0.0F;

        int maxW = 0;
        for (PotionEffect e : effects) {
            String nameLine = this.getNameLine(e) + (this.amplifier.getValue() ? " " + getRoman(e) : "");
            int w = FontManager.getStringWidth(nameLine, FS);
            if (this.duration.getValue()) {
                w = Math.max(w, FontManager.getStringWidth(Potion.getDurationString(e), FS));
            }
            maxW = Math.max(maxW, w);
        }
        float width = 8.0F + iconOffset + (float) maxW + 8.0F;
        float height = rowH * (float) effects.size() + 4.0F;

        float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F);
        float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F);
        if (x + width * scale > (float) sr.getScaledWidth()) {
            x = (float) sr.getScaledWidth() - width * scale;
        }
        if (y + height * scale > (float) sr.getScaledHeight()) {
            y = (float) sr.getScaledHeight() - height * scale;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float sx = x / scale;
        float sy = y / scale;

        RenderUtil.enableRenderState();
        if (this.background.getValue()) {
            RenderUtil.drawRoundedRect(sx, sy, width, height, 6.0F, GLASS_BODY);
            RenderUtil.drawRoundedOutline(sx, sy, sx + width, sy + height, 6.0F, 1.0F, GLASS_OUTLINE);
        }

        float rowY = sy + 2.0F;
        for (PotionEffect effect : effects) {
            int color = TEXT_MAIN;
            int dotColor = 0xFFFFFFFF;
            if (this.potionColor.getValue() && effect.getPotionID() >= 0 && effect.getPotionID() < Potion.potionTypes.length) {
                Potion p = Potion.potionTypes[effect.getPotionID()];
                if (p != null) {
                    dotColor = 0xFF000000 | p.getLiquidColor();
                }
            }
            // 效果色玻璃圆点
            if (this.potionColor.getValue()) {
                RenderUtil.fillCircle(sx + 4.0F, rowY + rowH / 2.0F, 2.5, 12, dotColor);
            }
            float textX = sx + 6.0F + iconOffset;
            float textY = rowY + 5.0F;
            if (this.icons.getValue()) {
                RenderUtil.renderPotionEffect(effect, (int) (sx + 6.0F), (int) (rowY + 2.0F));
            }
            String nameLine = this.getNameLine(effect);
            if (this.amplifier.getValue()) {
                nameLine += " " + getRoman(effect);
            }
            FontManager.drawString(nameLine, textX, textY, color, false, FS);
            if (this.duration.getValue()) {
                boolean low = this.lowTimeWarn.getValue()
                        && !effect.getIsPotionDurationMax()
                        && effect.getDuration() < 200;
                String time = effect.getIsPotionDurationMax() ? "**:**" : Potion.getDurationString(effect);
                FontManager.drawString(time, sx + width - 6.0F - (float) FontManager.getStringWidth(time, FS), textY,
                        low ? 0xFFFF5555 : TEXT_DIM, false, FS);
            }
            rowY += rowH;
        }
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    private String getNameLine(PotionEffect effect) {
        if (effect.getPotionID() < 0 || effect.getPotionID() >= Potion.potionTypes.length) {
            return "Effect";
        }
        Potion potion = Potion.potionTypes[effect.getPotionID()];
        return potion == null ? "Effect" : I18n.format(potion.getName());
    }

    private String getPotionName(PotionEffect effect) {
        return this.getNameLine(effect);
    }

    private static String getRoman(PotionEffect effect) {
        int amp = effect.getAmplifier();
        return amp > 0 && amp < ROMAN.length ? ROMAN[amp] : "";
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Onxy"};
    }
}
