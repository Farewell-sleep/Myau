package myau.module.modules;

import myau.OpenMyau;
import myau.enums.BlinkModules;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.mixin.IAccessorGuiChat;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.FontManager;
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Modern arraylist HUD: every active module is an independent rounded card
 * (semi-transparent dark body or theme-color gradient, 1px hairline outline),
 * text rendered with the bundled font (ShuYaoHengShui fallback chain).
 *
 * Style tokens follow the frozen render spec v1.0 (rounded 6px cards, hairline
 * outline, accent bar 2px wide, ease-out cubic entrance with 30ms stagger).
 *
 * Hard contract kept: class name/package, getColor(long)/getColor(long,long)
 * returning java.awt.Color, and every public property (config compatibility).
 */
public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Font size (spec section 5) and card metrics (spec section 2: card radius 6px). */
    private static final float FONT = 14.0F;
    private static final float PAD_X = 5.0F;
    private static final float PAD_Y = 3.0F;
    private static final float CARD_H = FONT + PAD_Y * 2.0F;
    private static final float CARD_R = 6.0F;

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
    public final PercentProperty background = new PercentProperty("background", 25);
    public final IntProperty rowSpacing = new IntProperty("row-spacing", 6, 0, 10);
    public final BooleanProperty showBar = new BooleanProperty("bar", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final BooleanProperty bgColor = new BooleanProperty("bg-color", false);
    public final BooleanProperty glow = new BooleanProperty("glow", false);
    public final IntProperty barless = new IntProperty("barless", 0, 0, 8, () -> this.showBar.getValue());
    public final ModeProperty barMode = new ModeProperty("bar-mode", 0, new String[]{"RIGHT", "LEFT", "TOP", "BOTTOM"}, () -> this.showBar.getValue());

    // ---- animation state (entrance stagger / hover / exit fade) ----
    private long lastFrame = System.currentTimeMillis();
    private final Map<Module, Long> rowBorn = new HashMap<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, float[]> lastRect = new HashMap<>();
    private final Map<Module, float[]> dying = new HashMap<>();

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
        int width = FontManager.getStringWidth(string, FONT);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + FontManager.getStringWidth(str, FONT);
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
                color = ColorUtil.fromHSB(this.getColorCycle(time, offset), 1.0F, 1.0F);
                break;
            case 1:
                color = ColorUtil.fromHSB(this.getColorCycle(time / 3L, 0L), 1.0F, 1.0F);
                break;
            case 2:
                float cycle = this.getColorCycle(time, offset);
                if (cycle % 1.0F < 0.5F) {
                    cycle = 1.0F - cycle % 1.0F;
                }
                color = ColorUtil.fromHSB(cycle, 1.0F, 1.0F);
                break;
            case 3:
                color = new Color(this.custom1.getValue(), true);
                break;
            case 4:
                double cycle1 = this.getColorCycle(time, offset);
                color = ColorUtil.interpolate(
                        (float) (2.0 * Math.abs(cycle1 - Math.floor(cycle1 + 0.5))),
                        new Color(this.custom1.getValue(), true),
                        new Color(this.custom2.getValue(), true)
                );
                break;
            case 5:
                double cycle2 = this.getColorCycle(time, offset);
                float floor = (float) (2.0 * Math.abs(cycle2 - Math.floor(cycle2 + 0.5)));
                if (floor <= 0.5F) {
                    color = ColorUtil.interpolate(floor * 2.0F, new Color(this.custom1.getValue(), true), new Color(this.custom2.getValue(), true));
                } else {
                    color = ColorUtil.interpolate((floor - 0.5F) * 2.0F, new Color(this.custom2.getValue(), true), new Color(this.custom3.getValue(), true));
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
            this.activeModules = OpenMyau.moduleManager.modules.values().stream().filter(module -> module.isEnabled() && !module.isHidden()).sorted(Comparator.comparingInt(this::getModuleWidth).reversed()).collect(Collectors.<Module>toList());
        }
    }

    /** Soft glow around a card: layered expanding rects on the outer edges. */
    private void drawGlowOutline(float x1, float y1, float x2, float y2, int color, int passes, float step,
                                 boolean top, boolean bottom, boolean left, boolean right, float alphaMul) {
        for (int i = passes; i >= 1; i--) {
            float expand = i * step;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.045F * intensity * intensity * alphaMul);

            RenderUtil.enableRenderState();
            if (top) {
                RenderUtil.drawRect(x1 - expand, y1 - expand, x2 + expand, y1, glowColor);
            }
            if (bottom) {
                RenderUtil.drawRect(x1 - expand, y2, x2 + expand, y2 + expand, glowColor);
            }
            if (left) {
                RenderUtil.drawRect(x1 - expand, y1, x1, y2, glowColor);
            }
            if (right) {
                RenderUtil.drawRect(x2, y1, x2 + expand, y2, glowColor);
            }
            RenderUtil.disableRenderState();
        }
    }

    /** Text glow: the main font stamped at 8 surrounding offsets with fading alpha. */
    private void drawGlowText(String text, float x, float y, int color, int passes, float spread, float alphaMul) {
        for (int i = passes; i >= 1; i--) {
            float offset = i * spread;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.10F * intensity * intensity * alphaMul);
            float diagonal = offset * 0.65F;
            FontManager.drawString(text, x + offset, y, glowColor, false, FONT);
            FontManager.drawString(text, x - offset, y, glowColor, false, FONT);
            FontManager.drawString(text, x, y + offset, glowColor, false, FONT);
            FontManager.drawString(text, x, y - offset, glowColor, false, FONT);
            FontManager.drawString(text, x + diagonal, y + diagonal, glowColor, false, FONT);
            FontManager.drawString(text, x - diagonal, y + diagonal, glowColor, false, FONT);
            FontManager.drawString(text, x + diagonal, y - diagonal, glowColor, false, FONT);
            FontManager.drawString(text, x - diagonal, y - diagonal, glowColor, false, FONT);
        }
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
            long now = System.currentTimeMillis();
            float dt = Math.min(60.0F, now - this.lastFrame);
            this.lastFrame = now;

            float scale = this.scale.getValue();
            ScaledResolution sr = new ScaledResolution(mc);

            // mouse position in unscaled (pre-matrix) gui coords, for hover highlight
            float mouseX = Mouse.getX() * sr.getScaledWidth() / (float) mc.displayWidth / scale;
            float mouseY = (sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / (float) mc.displayHeight) / scale;

            boolean left = this.posX.getValue() == 0;
            boolean top = this.posY.getValue() == 0;
            float anchorX = this.offsetX.getValue() / scale;
            float rightEdge = sr.getScaledWidth() / scale - this.offsetX.getValue() / scale;
            float rowStep = CARD_H + this.rowSpacing.getValue() + 1.0F;
            float curY = top
                    ? this.offsetY.getValue() / scale + 1.0F
                    : sr.getScaledHeight() / scale - this.offsetY.getValue() / scale - CARD_H;

            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1.0F);

            long idx = 0L;
            float bgPct = this.background.getValue().floatValue() / 100.0F;
            boolean useThemeBg = this.bgColor.getValue();
            boolean hasBg = bgPct > 0.001F;
            int gray = ChatColors.GRAY.toAwtColor();

            for (Module module : this.activeModules) {
                String moduleName = this.getModuleName(module);
                String[] moduleSuffix = this.getModuleSuffix(module);
                float textW = (float) this.calculateStringWidth(moduleName, moduleSuffix);
                float cardW = textW + PAD_X * 2.0F;

                // entrance progress: ease-out cubic with 30ms per-row stagger
                Long born = this.rowBorn.get(module);
                if (born == null) {
                    born = now;
                    this.rowBorn.put(module, born);
                }
                float p = clamp01((now - born - idx * 30L) / 200.0F);
                p = 1.0F - (float) Math.pow(1.0F - p, 3.0);

                float slideY = (1.0F - p) * 4.0F;
                float cardX1 = left ? anchorX : rightEdge - cardW;
                float cardX2 = cardX1 + cardW;
                float cardY1 = curY + slideY;
                float cardY2 = cardY1 + CARD_H;

                // hover highlight (only while the cursor rests on this row)
                boolean hovered = mouseX >= cardX1 && mouseX <= cardX2
                        && mouseY >= cardY1 && mouseY <= cardY2;
                float ha = this.hoverAnim.getOrDefault(module, 0.0F);
                ha += ((hovered ? 1.0F : 0.0F) - ha) * Math.min(1.0F, dt / 150.0F);
                this.hoverAnim.put(module, ha);

                Color themeColor = this.getColor(now, idx);
                int rgb = themeColor.getRGB();

                if (hasBg) {
                    if (this.glow.getValue()) {
                        boolean firstRow = idx == 0L;
                        boolean lastRow = idx == this.activeModules.size() - 1;
                        drawGlowOutline(cardX1, cardY1, cardX2, cardY2, rgb, 5, 0.6F,
                                firstRow, lastRow, !left, left, p);
                    }

                    // 1px hairline outline (border trick: outer rounded rect, inner fill)
                    int outline = useThemeBg
                            ? setAlpha(rgb, 0.55F * p)
                            : setAlpha(0xFFFFFF, 0.16F * p);
                    RenderUtil.drawRoundedRect(cardX1 - 0.5F, cardY1 - 0.5F, cardW + 1.0F, CARD_H + 1.0F, CARD_R + 0.5F, outline);

                    // card body: semi-transparent dark glass, or theme-color gradient
                    if (useThemeBg) {
                        int topC = new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(),
                                (int) (bgPct * 200.0F * p)).getRGB();
                        int botC = new Color(themeColor.getRed() / 2, themeColor.getGreen() / 2, themeColor.getBlue() / 2,
                                (int) (bgPct * 200.0F * p)).getRGB();
                        RenderUtil.drawRoundedRectGradient(cardX1, cardY1, cardX2, cardY2, CARD_R, topC, botC);
                    } else {
                        int fill = new Color(0.06F, 0.08F, 0.11F, bgPct * p).getRGB();
                        RenderUtil.drawRoundedRect(cardX1, cardY1, cardW, CARD_H, CARD_R, fill);
                    }

                    // hover wash
                    if (ha > 0.01F) {
                        RenderUtil.drawRoundedRect(cardX1, cardY1, cardW, CARD_H, CARD_R,
                                setAlpha(0xFFFFFF, 0.10F * ha * p));
                    }
                }

                // theme accent bar (2px wide, 1px corner radius), barless insets vertical bar
                if (this.showBar.getValue()) {
                    int barModeVal = this.barMode.getValue();
                    float by1 = cardY1 + this.barless.getValue();
                    float by2 = cardY2 - this.barless.getValue();
                    float bh = by2 - by1;
                    int barColor = setAlpha(rgb, p);
                    if (barModeVal == 0) { // anchor edge
                        if (left) {
                            RenderUtil.drawRoundedRect(cardX1 - 3.0F, by1, 2.0F, bh, 1.0F, barColor);
                        } else {
                            RenderUtil.drawRoundedRect(cardX2 + 1.0F, by1, 2.0F, bh, 1.0F, barColor);
                        }
                    } else if (barModeVal == 1) { // far edge
                        if (left) {
                            RenderUtil.drawRoundedRect(cardX2 + 1.0F, by1, 2.0F, bh, 1.0F, barColor);
                        } else {
                            RenderUtil.drawRoundedRect(cardX1 - 3.0F, by1, 2.0F, bh, 1.0F, barColor);
                        }
                    } else if (barModeVal == 2) { // top edge of the column
                        if (idx == 0L) {
                            RenderUtil.drawRoundedRect(cardX1, cardY1 - 3.0F, cardW, 2.0F, 1.0F, barColor);
                        }
                    } else if (barModeVal == 3) { // bottom edge of the column
                        if (idx == this.activeModules.size() - 1) {
                            RenderUtil.drawRoundedRect(cardX1, cardY2 + 1.0F, cardW, 2.0F, 1.0F, barColor);
                        }
                    }
                }

                GlStateManager.disableDepth();
                float textX = left ? cardX1 + PAD_X : cardX2 - PAD_X - textW;
                float textY = cardY1 + PAD_Y;
                int textColor = setAlpha(rgb, p);
                int suffixColor = setAlpha(gray, p);

                if (this.glow.getValue()) {
                    drawGlowText(moduleName, textX, textY, textColor, 3, 0.55F, p);
                }
                if (this.shadow.getValue()) {
                    FontManager.drawStringWithShadow(moduleName, textX, textY, textColor, FONT);
                } else {
                    FontManager.drawString(moduleName, textX, textY, textColor, false, FONT);
                }

                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float suffixX = textX + FontManager.getStringWidth(moduleName, FONT) + 3.0F;
                    for (String string : moduleSuffix) {
                        if (this.glow.getValue()) {
                            drawGlowText(string, suffixX, textY, suffixColor, 2, 0.35F, p);
                        }
                        if (this.shadow.getValue()) {
                            FontManager.drawStringWithShadow(string, suffixX, textY, suffixColor, FONT);
                        } else {
                            FontManager.drawString(string, suffixX, textY, suffixColor, false, FONT);
                        }
                        suffixX += FontManager.getStringWidth(string, FONT) + 3.0F;
                    }
                }
                GlStateManager.enableDepth();

                this.lastRect.put(module, new float[]{cardX1, cardY1, cardX2, cardY2});
                curY += rowStep * (top ? 1.0F : -1.0F);
                idx++;
            }

            // exit fade: rows of modules that just got toggled off fade out in place
            Iterator<Map.Entry<Module, float[]>> it = this.dying.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Module, float[]> e = it.next();
                float[] r = e.getValue();
                r[4] -= dt / 220.0F;
                if (r[4] <= 0.0F) {
                    it.remove();
                    continue;
                }
                RenderUtil.drawRoundedRect(r[0] - 0.5F, r[1] - 0.5F, (r[2] - r[0]) + 1.0F, (r[3] - r[1]) + 1.0F, CARD_R + 0.5F,
                        setAlpha(0xFFFFFF, 0.16F * r[4]));
                RenderUtil.drawRoundedRect(r[0], r[1], r[2] - r[0], r[3] - r[1], CARD_R,
                        new Color(0.06F, 0.08F, 0.11F, 0.25F * r[4]).getRGB());
            }
            // collect modules that left the active list into the dying map
            for (Module m : new ArrayList<>(this.rowBorn.keySet())) {
                if (!this.activeModules.contains(m)) {
                    float[] rect = this.lastRect.get(m);
                    if (rect != null) {
                        this.dying.put(m, new float[]{rect[0], rect[1], rect[2], rect[3], 1.0F});
                    }
                    this.rowBorn.remove(m);
                    this.hoverAnim.remove(m);
                }
            }

            if (this.blinkTimer.getValue()) {
                BlinkModules blinkingModule = OpenMyau.blinkManager.getBlinkingModule();
                if (blinkingModule != BlinkModules.NONE && blinkingModule != BlinkModules.AUTO_BLOCK) {
                    long movementPacketSize = OpenMyau.blinkManager.countMovement();
                    if (movementPacketSize > 0L) {
                        String count = String.valueOf(movementPacketSize);
                        GlStateManager.enableBlend();
                        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        FontManager.drawString(count,
                                sr.getScaledWidth() / 2.0F / scale - FontManager.getStringWidth(count, FONT) / 2.0F,
                                sr.getScaledHeight() / 5.0F * 3.0F / scale,
                                setAlpha(this.getColor(now, idx).getRGB(), 0.75F),
                                this.shadow.getValue(), FONT);
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.popMatrix();
        }
    }
}
