package myau.module.modules;

import myau.OpenMyau;
import myau.enums.BlinkModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.LeaderFontRender;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Modern arraylist HUD — skidded rendering style from Leader-Lite
 * (leader.module.modules.render.HUD). Rows are rendered top-aligned with a
 * fixed row height of getFontHeight() - 1, optional frosted-glass backdrop
 * (same FBO pipeline as the ClickGUI), per-row bar (RIGHT / LEFT / TOP /
 * BOTTOM), glow outline + glow text, gray suffixes and width-descending sort.
 * Text is drawn through the Leader font chain (LeaderFontManager ->
 * LeaderFontRender -> CustomFontRenderer), default Xylitol 18px.
 *
 * Positioning keeps Myau's posX / posY / offsetX / offsetY properties
 * (Leader uses a drag manager that Myau does not have).
 */
public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private List<Module> activeModules = new ArrayList<>();
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.5F, 1.5F);
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final ModeProperty posX = new ModeProperty("position-x", 0, new String[]{"LEFT", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 0, new String[]{"TOP", "BOTTOM"});
    public final IntProperty offsetX = new IntProperty("offset-x", 2, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 2, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty rowSpacing = new IntProperty("row-spacing", 0, 0, 10);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final BooleanProperty glow = new BooleanProperty("glow", true);
    public final IntProperty barless = new IntProperty("barless", 0, 0, 8, () -> this.showBar.getValue());
    public final ModeProperty barMode = new ModeProperty("bar-mode", 0, new String[]{"RIGHT", "LEFT", "TOP", "BOTTOM"}, () -> this.showBar.getValue());

    private String getModuleName(Module module) {
        String moduleName = module.getName();
        if (this.lowerCase.getValue()) {
            moduleName = moduleName.toLowerCase(Locale.ROOT);
        }
        return moduleName;
    }

    private String[] getModuleSuffix(Module module) {
        String[] moduleSuffix = module.getSuffix();
        if (this.lowerCase.getValue()) {
            for (int i = 0; i < moduleSuffix.length; i++) {
                moduleSuffix[i] = moduleSuffix[i].toLowerCase();
            }
        }
        return moduleSuffix;
    }

    private int getModuleWidth(Module module) {
        return this.calculateStringWidth(
                this.getModuleName(module), this.getModuleSuffix(module)
        );
    }

    private int calculateStringWidth(String string, String[] arr) {
        int width = LeaderFontRender.getStringWidth(string, 18.0F, LeaderFontManager.isCustomFont());
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + LeaderFontRender.getStringWidth(str, 18.0F, LeaderFontManager.isCustomFont());
            }
        }
        return width;
    }

    private static int setAlpha(int color, float alpha) {
        int a = (int) (alpha * 255.0F);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0xFFFFFF) | (a << 24);
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    // ---- color helpers (inline, no Myau ColorUtil) ----
    private static Color fromHSB(float hue, float sat, float bright) {
        return Color.getHSBColor(hue % 1.0F, clamp01(sat), clamp01(bright));
    }

    private static Color interpolate(float t, Color a, Color b) {
        t = clamp01(t);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t)
        );
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    public HUD() {
        super("HUD", true, true);
    }

    public Color getColor(long time) {
        return this.getColor(time, 0L);
    }

    public Color getColor(long time, long offset) {
        Color color = Color.white;
        switch (this.colorMode.getValue()) {
            case 0:
                color = fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue(), true);
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue(), true),
                        new Color(this.custom2.getValue(), true)
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = interpolate(floor * 2.0F, new Color(this.custom1.getValue(), true), new Color(this.custom2.getValue(), true));
                } else {
                    color = interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue(), true), new Color(this.custom3.getValue(), true));
                }
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return Color.getHSBColor(
                hsb[0],
                hsb[1] * (this.colorSaturation.getValue().floatValue() / 100.0F),
                hsb[2] * (this.colorBrightness.getValue().floatValue() / 100.0F)
        );
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST) {
            this.activeModules = OpenMyau.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
                    .collect(Collectors.toList());
        }
    }

    // ---- glow helpers (Leader-Lite style) ----

    private void drawGlowText(String text, float x, float y, int color, int passes, float spread) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        for (int i = passes; i >= 1; i--) {
            float offset = i * spread;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.35F * intensity * intensity);
            float diagonal = offset * 0.65F;
            LeaderFontRender.drawString(text, x + offset, y, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x - offset, y, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x, y + offset, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x, y - offset, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x + diagonal, y + diagonal, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x - diagonal, y + diagonal, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x + diagonal, y - diagonal, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
            LeaderFontRender.drawString(text, x - diagonal, y - diagonal, glowColor, false, 18.0F, LeaderFontManager.isCustomFont());
        }
        GlStateManager.disableBlend();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (OpenMyau.commandManager != null && OpenMyau.commandManager.isTypingCommand(text)) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 2),
                        (float) (mc.currentScreen.height - 2),
                        1.5F,
                        0,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
                RenderUtil.disableRenderState();
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            float height = (float) LeaderFontRender.getFontHeight(18.0F, LeaderFontManager.isCustomFont()) - 1.0F;
            ScaledResolution sr = new ScaledResolution(mc);
            boolean rightAlign = this.posX.getValue() == 1;
            boolean top = this.posY.getValue() == 0;
            float scale = this.scale.getValue();

            // Anchor (Myau positioning; Leader uses a drag manager we do not have)
            float anchorX = rightAlign
                    ? sr.getScaledWidth() - this.offsetX.getValue()
                    : this.offsetX.getValue();
            float anchorY = top
                    ? this.offsetY.getValue()
                    : sr.getScaledHeight() - this.offsetY.getValue() - height * scale;

            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1.0F);
            long l = System.currentTimeMillis();
            long idx = 0L;
            float x = anchorX / scale;
            float y = anchorY / scale;

            int gray = new Color(0x99, 0x99, 0x99).getRGB();

            for (Module module : this.activeModules) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float totalWidth = (float) (this.calculateStringWidth(moduleName, moduleSuffix) - (this.shadow.getValue() ? 0 : 1));
                Color themeColor = this.getColor(l, idx);
                int color = themeColor.getRGB();
                float sx = x;
                float sy = y;
                float bgX1, bgX2, textX;
                if (rightAlign) {
                    bgX2 = sx + 1.0F;
                    bgX1 = sx - totalWidth - 1.0F;
                    textX = sx - totalWidth;
                } else {
                    bgX1 = sx - 1.0F;
                    bgX2 = sx + 1.0F + totalWidth;
                    textX = sx;
                }
                float bgY1 = sy - this.rowSpacing.getValue() - (idx == 0L ? 1.0F : 0.0F);
                float bgY2 = sy + height + this.rowSpacing.getValue() + (this.shadow.getValue() ? 1.0F : 0.0F);
                float textY = sy;

                RenderUtil.enableRenderState();
                if (this.showBar.getValue()) {
                    int barModeVal = this.barMode.getValue();
                    int barlessVal = this.barless.getValue();
                    float barY1 = bgY1 + barlessVal;
                    float barY2 = bgY2 - barlessVal;
                    if (barModeVal == 0) {
                        if (rightAlign) {
                            RenderUtil.drawRect(bgX2, barY1, bgX2 + 1.0F, barY2, color);
                        } else {
                            RenderUtil.drawRect(sx - 2.0F, barY1, sx - 1.0F, barY2, color);
                        }
                    } else if (barModeVal == 1) {
                        if (rightAlign) {
                            RenderUtil.drawRect(bgX1 - 1.0F, barY1, bgX1, barY2, color);
                        } else {
                            RenderUtil.drawRect(bgX2, barY1, bgX2 + 1.0F, barY2, color);
                        }
                    } else if (barModeVal == 2) {
                        if (idx == 0L) {
                            RenderUtil.drawRect(bgX1, bgY1 - 1.0F, bgX2, bgY1, color);
                        }
                    } else if (barModeVal == 3) {
                        if (idx == this.activeModules.size() - 1) {
                            RenderUtil.drawRect(bgX1, bgY2, bgX2, bgY2 + 1.0F, color);
                        }
                    }
                }
                RenderUtil.disableRenderState();

                GlStateManager.disableDepth();

                if (this.glow.getValue()) {
                    drawGlowText(moduleName, textX, textY, color, 4, 0.65F);
                }
                if (this.shadow.getValue()) {
                    LeaderFontRender.drawStringWithShadow(moduleName, textX, textY, color, 18.0F, LeaderFontManager.isCustomFont());
                } else {
                    LeaderFontRender.drawString(moduleName, textX, textY, color, false, 18.0F, LeaderFontManager.isCustomFont());
                }
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float suffixX = (float) LeaderFontRender.getStringWidth(moduleName, 18.0F, LeaderFontManager.isCustomFont()) + 3.0F;
                    for (String string : moduleSuffix) {
                        if (this.glow.getValue()) {
                            drawGlowText(string, textX + suffixX, textY, gray, 2, 0.35F);
                        }
                        if (this.shadow.getValue()) {
                            LeaderFontRender.drawStringWithShadow(string, textX + suffixX, textY, gray, 18.0F, LeaderFontManager.isCustomFont());
                        } else {
                            LeaderFontRender.drawString(string, textX + suffixX, textY, gray, false, 18.0F, LeaderFontManager.isCustomFont());
                        }
                        suffixX += (float) LeaderFontRender.getStringWidth(string, 18.0F, LeaderFontManager.isCustomFont()) + (this.shadow.getValue() ? 3.0F : 2.0F);
                    }
                }
                y += (height + 2 * this.rowSpacing.getValue() + (this.shadow.getValue() ? 1.0F : 0.0F)) * (top ? 1.0F : -1.0F);
                idx++;
            }

            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = OpenMyau.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = OpenMyau.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        String count = String.valueOf(movementPacketSize);
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        LeaderFontRender.drawString(count,
                                sr.getScaledWidth() / 2.0F / scale - LeaderFontRender.getStringWidth(count, 18.0F, LeaderFontManager.isCustomFont()) / 2.0F,
                                sr.getScaledHeight() / 5.0F * 3.0F / scale,
                                this.getColor(l, idx).getRGB() & 16777215 | -1090519040,
                                this.shadow.getValue(), 18.0F, LeaderFontManager.isCustomFont());
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }
}
