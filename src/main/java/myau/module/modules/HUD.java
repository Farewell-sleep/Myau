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
import myau.property.properties.*;
import myau.risefont.RiseFont;
import myau.risefont.RiseFontManager;
import myau.util.ColorUtil;
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
 * Modern arraylist HUD — skidded rendering style from Rise (ModernInterface /
 * ArrayListEntry) and Onyx (module toggles). Text is rendered with the skidded
 * Rise font chain (RiseFontManager: Product Sans + HarmonyOS Sans SC), and all
 * card geometry is drawn with raw GL primitives (no Myau RenderUtil /
 * FontManager), so this HUD never pollutes world / entity rendering.
 *
 * Public API (class name, getColor, properties) stays config-compatible.
 */
public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final float PAD_X = 5.0F;
    private static final float PAD_Y = 3.0F;
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

    // ---- Rise-style entry animation state ----
    private long lastFrame = System.currentTimeMillis();
    private final Map<Module, Long> rowBorn = new HashMap<>();
    private final Map<Module, float[]> lastRect = new HashMap<>();
    private final Map<Module, float[]> dying = new HashMap<>();

    /** Rise-style font instance (Product Sans + HarmonyOS SC fallback). */
    private RiseFont font() {
        return RiseFontManager.MAIN.hudFont();
    }

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
        return this.calculateStringWidth(this.getModuleName(module), this.getModuleSuffix(module));
    }

    private int calculateStringWidth(String string, String[] arr) {
        RiseFont f = this.font();
        int width = f.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + f.getStringWidth(str);
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
            this.activeModules = OpenMyau.moduleManager.modules.values().stream()
                    .filter(module -> module.isEnabled() && !module.isHidden())
                    .sorted(Comparator.comparingInt(this::getModuleWidth).reversed())
                    .collect(Collectors.toList());
        }
    }

    // ---- raw GL rounded-rect primitives (no Myau RenderUtil) ----
    private static void roundedArc(float cx, float cy, float r, int startDeg, int endDeg) {
        GL11.glBegin(GL11.GL_POLYGON);
        for (int i = startDeg; i <= endDeg; i += 6) {
            double ang = Math.toRadians(i);
            GL11.glVertex2f(cx + (float) (Math.cos(ang) * r), cy + (float) (Math.sin(ang) * r));
        }
        GL11.glEnd();
    }

    private static void drawRoundedRect(float x, float y, float w, float h, float r, int color) {
        if (w <= 0.0F || h <= 0.0F) return;
        r = Math.min(r, Math.min(w / 2.0F, h / 2.0F));
        float a = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, a);
        roundedArc(x + r, y + r, r, 180, 270);
        roundedArc(x + w - r, y + r, r, 270, 360);
        roundedArc(x + w - r, y + h - r, r, 0, 90);
        roundedArc(x + r, y + h - r, r, 90, 180);
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x + r, y);
        GL11.glVertex2f(x + w - r, y);
        GL11.glVertex2f(x + w - r, y + h);
        GL11.glVertex2f(x + r, y + h);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_POLYGON);
        GL11.glVertex2f(x, y + r);
        GL11.glVertex2f(x + w, y + r);
        GL11.glVertex2f(x + w, y + h - r);
        GL11.glVertex2f(x, y + h - r);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    private static void drawRect(float x, float y, float w, float h, int color) {
        if (w <= 0.0F || h <= 0.0F) return;
        float a = (color >> 24 & 0xFF) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, a);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    /** 1px hairline rounded outline (outer rounded rect minus inner fill). */
    private static void drawRoundedOutline(float x, float y, float w, float h, float r, int color) {
        drawRoundedRect(x, y, w, h, r, color);
        drawRoundedRect(x + 0.75F, y + 0.75F, Math.max(0.0F, w - 1.5F), Math.max(0.0F, h - 1.5F), Math.max(0.5F, r - 0.75F), 0x00000000);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (this.chatOutline.getValue() && mc.currentScreen instanceof GuiChat) {
            String text = ((IAccessorGuiChat) mc.currentScreen).getInputField().getText().trim();
            if (OpenMyau.commandManager != null && OpenMyau.commandManager.isTypingCommand(text)) {
                drawRoundedOutline(
                        2.0F,
                        (float) (mc.currentScreen.height - 14),
                        (float) (mc.currentScreen.width - 4),
                        (float) (mc.currentScreen.height - 2) - (float) (mc.currentScreen.height - 14),
                        1.5F,
                        this.getColor(System.currentTimeMillis()).getRGB()
                );
            }
        }
        if (this.isEnabled() && !mc.gameSettings.showDebugInfo) {
            long now = System.currentTimeMillis();
            float dt = Math.min(60.0F, now - this.lastFrame);
            this.lastFrame = now;

            float scale = this.scale.getValue();
            ScaledResolution sr = new ScaledResolution(mc);

            float mouseX = Mouse.getX() * sr.getScaledWidth() / (float) mc.displayWidth / scale;
            float mouseY = (sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / (float) mc.displayHeight) / scale;

            boolean left = this.posX.getValue() == 0;
            boolean top = this.posY.getValue() == 0;
            float anchorX = this.offsetX.getValue() / scale;
            float rightEdge = sr.getScaledWidth() / scale - this.offsetX.getValue() / scale;
            RiseFont f = this.font();
            float rowH = f.height() + PAD_Y * 2.0F;
            float rowStep = rowH + this.rowSpacing.getValue() + 1.0F;
            float curY = top
                    ? this.offsetY.getValue() / scale + 1.0F
                    : sr.getScaledHeight() / scale - this.offsetY.getValue() / scale - rowH;

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
                float cardY2 = cardY1 + rowH;

                Color themeColor = this.getColor(now, idx);
                int rgb = themeColor.getRGB();

                if (hasBg) {
                    int outline = useThemeBg
                            ? setAlpha(rgb, 0.55F * p)
                            : setAlpha(0xFFFFFF, 0.16F * p);
                    drawRoundedOutline(cardX1 - 0.5F, cardY1 - 0.5F, cardW + 1.0F, rowH + 1.0F, CARD_R + 0.5F, outline);

                    if (useThemeBg) {
                        drawRoundedRect(cardX1, cardY1, cardW, rowH, CARD_R,
                                new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(),
                                        (int) (bgPct * 200.0F * p)).getRGB());
                    } else {
                        drawRoundedRect(cardX1, cardY1, cardW, rowH, CARD_R,
                                new Color(0.06F, 0.08F, 0.11F, bgPct * p).getRGB());
                    }
                }

                // Rise-style accent bar at the leading edge
                if (this.showBar.getValue()) {
                    int barModeVal = this.barMode.getValue();
                    float by1 = cardY1 + this.barless.getValue();
                    float by2 = cardY2 - this.barless.getValue();
                    float bh = by2 - by1;
                    int barColor = setAlpha(rgb, p);
                    if (barModeVal == 0) {
                        if (left) {
                            drawRoundedRect(cardX1 - 3.0F, by1, 2.0F, bh, 1.0F, barColor);
                        } else {
                            drawRoundedRect(cardX2 + 1.0F, by1, 2.0F, bh, 1.0F, barColor);
                        }
                    } else if (barModeVal == 1) {
                        if (left) {
                            drawRoundedRect(cardX2 + 1.0F, by1, 2.0F, bh, 1.0F, barColor);
                        } else {
                            drawRoundedRect(cardX1 - 3.0F, by1, 2.0F, bh, 1.0F, barColor);
                        }
                    } else if (barModeVal == 2) {
                        if (idx == 0L) {
                            drawRoundedRect(cardX1, cardY1 - 3.0F, cardW, 2.0F, 1.0F, barColor);
                        }
                    } else if (barModeVal == 3) {
                        if (idx == this.activeModules.size() - 1) {
                            drawRoundedRect(cardX1, cardY2 + 1.0F, cardW, 2.0F, 1.0F, barColor);
                        }
                    }
                }

                GlStateManager.disableDepth();
                float textX = left ? cardX1 + PAD_X : cardX2 - PAD_X - textW;
                float textY = cardY1 + PAD_Y;
                int textColor = setAlpha(rgb, p);
                int suffixColor = setAlpha(gray, p);

                f.a(moduleName, textX, textY, textColor);
                if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                    float suffixX = textX + f.getStringWidth(moduleName) + 3.0F;
                    for (String string : moduleSuffix) {
                        f.a(string, suffixX, textY, suffixColor);
                        suffixX += f.getStringWidth(string) + 3.0F;
                    }
                }
                GlStateManager.enableDepth();

                this.lastRect.put(module, new float[]{cardX1, cardY1, cardX2, cardY2});
                curY += rowStep * (top ? 1.0F : -1.0F);
                idx++;
            }

            Iterator<Map.Entry<Module, float[]>> it = this.dying.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Module, float[]> e = it.next();
                float[] r = e.getValue();
                r[4] -= dt / 220.0F;
                if (r[4] <= 0.0F) {
                    it.remove();
                    continue;
                }
                drawRoundedOutline(r[0] - 0.5F, r[1] - 0.5F, (r[2] - r[0]) + 1.0F, (r[3] - r[1]) + 1.0F, CARD_R + 0.5F,
                        setAlpha(0xFFFFFF, 0.16F * r[4]));
                drawRoundedRect(r[0], r[1], r[2] - r[0], r[3] - r[1], CARD_R,
                        new Color(0.06F, 0.08F, 0.11F, 0.25F * r[4]).getRGB());
            }
            for (Module m : new ArrayList<>(this.rowBorn.keySet())) {
                if (!this.activeModules.contains(m)) {
                    float[] rect = this.lastRect.get(m);
                    if (rect != null) {
                        this.dying.put(m, new float[]{rect[0], rect[1], rect[2], rect[3], 1.0F});
                    }
                    this.rowBorn.remove(m);
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
                        f.a(count,
                                sr.getScaledWidth() / 2.0F / scale - f.getStringWidth(count) / 2.0F,
                                sr.getScaledHeight() / 5.0F * 3.0F / scale,
                                setAlpha(this.getColor(now, idx).getRGB(), 0.75F));
                        GlStateManager.disableBlend();
                    }
                }
            }
            GlStateManager.popMatrix();
        }
    }
}
