package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.RenderUtil;
import myau.util.FontManager;
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
 * Onxy-style potion status HUD, rewritten with Myau's native rendering.
 */
public class PotionHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

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
        float rowH = 20.0F * scale;
        float iconOffset = this.icons.getValue() ? 24.0F * scale : 0.0F;

        int maxW = 0;
        for (PotionEffect e : effects) {
            int w = FontManager.getStringWidth(this.getNameLine(e) + (this.amplifier.getValue() ? " " + getRoman(e) : ""), 12.0F);
            if (this.duration.getValue()) {
                w = Math.max(w, FontManager.getStringWidth(Potion.getDurationString(e), 12.0F));
            }
            maxW = Math.max(maxW, w);
        }
        float width = 12.0F * scale + iconOffset + (float) maxW + 6.0F * scale;
        float height = rowH * (float) effects.size() + 4.0F * scale;

        float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F);
        float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F);
        if (x + width > (float) sr.getScaledWidth()) {
            x = (float) sr.getScaledWidth() - width;
        }
        if (y + height > (float) sr.getScaledHeight()) {
            y = (float) sr.getScaledHeight() - height;
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float sx = x / scale;
        float sy = y / scale;
        float sRowH = 20.0F;
        float sIcon = this.icons.getValue() ? 24.0F : 0.0F;

        if (this.background.getValue()) {
            RenderUtil.drawRect(sx, sy, sx + width / scale, sy + height / scale, new Color(0, 0, 0, 90).getRGB());
        }

        float rowY = sy + 2.0F;
        for (PotionEffect effect : effects) {
            int color = 0xFFFFFFFF;
            if (this.potionColor.getValue() && effect.getPotionID() >= 0 && effect.getPotionID() < Potion.potionTypes.length) {
                Potion p = Potion.potionTypes[effect.getPotionID()];
                if (p != null) {
                    color = 0xFF000000 | p.getLiquidColor();
                }
            }
            float textX = sx + 6.0F * scale + sIcon;
            float textY = rowY + 1.0F;
            if (this.icons.getValue()) {
                RenderUtil.renderPotionEffect(effect, (int) (sx + 3.0F * scale), (int) (rowY + 1.0F));
            }
            String nameLine = this.getNameLine(effect);
            if (this.amplifier.getValue()) {
                nameLine += " " + getRoman(effect);
            }
            FontManager.drawString(nameLine, textX, textY, color, true, 12.0F);
            if (this.duration.getValue()) {
                boolean low = this.lowTimeWarn.getValue()
                        && !effect.getIsPotionDurationMax()
                        && effect.getDuration() < 200;
                String time = effect.getIsPotionDurationMax() ? "**:**" : Potion.getDurationString(effect);
                FontManager.drawString(
                        time,
                        textX + (float) FontManager.getStringWidth(nameLine, 12.0F) + 6.0F * scale,
                        textY,
                        low ? 0xFFFF5555 : 0xFFAAAAAA,
                        true,
                        12.0F
                );
            }
            rowY += sRowH;
        }
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
