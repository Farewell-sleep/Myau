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
import myau.risefont.RiseFont;
import myau.risefont.RiseFontManager;
import myau.risefont.RiseFontWeight;
import myau.ui.liquid.GlassRenderer;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

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
    /** Hengshui-era card radius (3px): rows sit flush together and read as
     *  one continuous frame column instead of separate pills. */
    private static final float CARD_R = 3.0F;
    /** Onyx Array List fixed row height (14px text line). */
    private static final float ONYX_ROW_H = 14.0F;
    /** Onyx ACCENT_BAR accent width. */
    private static final float ACCENT_W = 2.0F;

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
    public final IntProperty rowSpacing = new IntProperty("row-spacing", 0, 0, 10);
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

    // ---- Liquid-glass backdrop (same pipeline as the ClickGUI) ----
    private Framebuffer hudBlurA;
    private boolean hudBlurFailed;

    // ---- Rise-style entry animation state ----
    private long lastFrame = System.currentTimeMillis();
    private final Map<Module, Long> rowBorn = new HashMap<>();
    private final Map<Module, float[]> lastRect = new HashMap<>();
    private final Map<Module, float[]> dying = new HashMap<>();

    /** Onyx-style font instance (13px optical weight — the Array List text
     *  size), rendered through the skidded Rise font chain. */
    private RiseFont font() {
        return RiseFontManager.MAIN.get(13, RiseFontWeight.MEDIUM);
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
        GlStateManager.color(red, green, blue, a);
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

    // ---- Liquid-glass backdrop (same FBO pipeline as the ClickGUI) ----

    private void ensureBlurFbo() {
        if (hudBlurFailed) return;
        if (hudBlurA == null || hudBlurA.framebufferWidth != mc.displayWidth
                || hudBlurA.framebufferHeight != mc.displayHeight) {
            try {
                if (hudBlurA != null) hudBlurA.deleteFramebuffer();
                hudBlurA = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                hudBlurA.setFramebufferColor(0, 0, 0, 0);
            } catch (Throwable t) {
                hudBlurFailed = true;
                hudBlurA = null;
                System.out.println("[Myau] HUD glass FBO init failed: " + t);
            }
        }
    }

    /** Copy the current main frame into hudBlurA once per frame so the cards
     *  can sample it as their frosted-glass backdrop. Restores every GL state
     *  it touches (scissor / depth / blend / alpha / texture / color). */
    private void renderBlurBackdrop(int scaledW, int scaledH) {
        if (hudBlurFailed || hudBlurA == null || mc.getFramebuffer() == null
                || mc.getFramebuffer().framebufferTexture == 0) {
            return;
        }
        int srcTex = mc.getFramebuffer().framebufferTexture;
        boolean scissorWasEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager.disableBlend();
            GlStateManager.disableAlpha();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, hudBlurA.framebufferObject);
            GL11.glViewport(0, 0, hudBlurA.framebufferWidth, hudBlurA.framebufferHeight);
            GlassRenderer.drawTextureQuad(srcTex, scaledW, scaledH);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        } catch (Throwable t) {
            hudBlurFailed = true;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getFramebuffer().framebufferObject);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        } finally {
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.color(1, 1, 1, 1);
            if (scissorWasEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (depthWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
    }

    /** Frosted-glass card backdrop: samples the blurred frame copy with the
     *  LiquidGlass edge-refraction shader (same look as the ClickGUI). Falls
     *  back to the translucent gradient when the blur pipeline is unavailable.
     *  @return true if the glass shader path was used. */
    private boolean drawGlassCardBg(float x1, float y1, float x2, float y2, float r, float alpha,
                                    int themeRgb, boolean useThemeBg, boolean hasBg, float bgPct, float p) {
        if (!hasBg) return false;
        if (hudBlurFailed || hudBlurA == null) {
            return false;
        }
        try {
            ScaledResolution sr = new ScaledResolution(mc);
            int sw = sr.getScaledWidth(), sh = sr.getScaledHeight();
            float w = x2 - x1, h = y2 - y1;
            if (w <= 0 || h <= 0) return false;
            float rClamp = Math.min(r, Math.min(w / 2.0F, h / 2.0F));
            int prevTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager.bindTexture(hudBlurA.framebufferTexture);
            GlStateManager.enableTexture2D();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            if (useThemeBg) {
                Color c = new Color(themeRgb);
                GlStateManager.color(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, alpha);
            } else {
                GlStateManager.color(1, 1, 1, alpha);
            }
            GlassRenderer.LiquidGlassShader.INSTANCE.use();
            float halfH = 0.5F * h / w;
            float rUv = Math.min(rClamp / w, Math.min(0.5F, halfH));
            GlassRenderer.LiquidGlassShader.INSTANCE.setPanelParams(
                    0.5F, halfH, rUv, w / sw, h / sh, x1 / sw, 1 - y1 / sh);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(x1, y1);
            GL11.glTexCoord2f(1, 0);
            GL11.glVertex2f(x2, y1);
            GL11.glTexCoord2f(1, 1);
            GL11.glVertex2f(x2, y2);
            GL11.glTexCoord2f(0, 1);
            GL11.glVertex2f(x1, y2);
            GL11.glEnd();
            GlassRenderer.LiquidGlassShader.INSTANCE.stop();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.bindTexture(prevTexture);
            return true;
        } catch (Throwable t) {
            hudBlurFailed = true;
            GlStateManager.color(1, 1, 1, 1);
            return false;
        }
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

            boolean left = this.posX.getValue() == 0;
            boolean top = this.posY.getValue() == 0;
            float anchorX = this.offsetX.getValue() / scale;
            float rightEdge = sr.getScaledWidth() / scale - this.offsetX.getValue() / scale;
            RiseFont f = this.font();
            // Onyx Array List: fixed 14px text line, rows are 14px tall plus spacing.
            float rowH = ONYX_ROW_H;
            float rowStep = rowH + this.rowSpacing.getValue();
            float curY = top
                    ? this.offsetY.getValue() / scale + 1.0F
                    : sr.getScaledHeight() / scale - this.offsetY.getValue() / scale - rowH;

            // Frosted-glass frame copy runs OUTSIDE the scale transform so the
            // full-screen quad always covers the whole viewport.
            float bgPct0 = this.background.getValue().floatValue() / 100.0F;
            if (bgPct0 > 0.001F) {
                this.ensureBlurFbo();
                this.renderBlurBackdrop(sr.getScaledWidth(), sr.getScaledHeight());
            }

            GlStateManager.pushMatrix();
            GlStateManager.scale(scale, scale, 1.0F);
            try {
            long idx = 0L;
            float bgPct = bgPct0;
            boolean useThemeBg = this.bgColor.getValue();
            boolean hasBg = bgPct > 0.001F;
            int gray = new Color(0x99, 0x99, 0x99).getRGB();

            // Side bar: one continuous vertical bar spanning the whole column
            // (instead of per-row fragments) — Onyx ACCENT_BAR style.
            float colTop = curY;
            float colBottom = curY + Math.max(0, this.activeModules.size() - 1) * rowStep + rowH;
            int firstBarColor = 0xFFFFFFFF;
            int barModeVal = this.showBar.getValue() ? this.barMode.getValue() : -1;
            boolean sideBar = barModeVal == 0 || barModeVal == 1;
            float barX = 0.0F;
            if (sideBar) {
                if (barModeVal == 0) {
                    barX = left ? anchorX - ACCENT_W - 1.0F : rightEdge + 1.0F;
                } else {
                    barX = left ? rightEdge + 1.0F : anchorX - ACCENT_W - 1.0F;
                }
            }

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

                // Onyx SLIDE_FADE: rows slide in horizontally by 14px and
                // scale 0.9 -> 1.0 while fading in.
                float slideX = (1.0F - p) * 14.0F;
                float rowScale = 0.9F + 0.1F * p;
                float cardX1 = (left ? anchorX : rightEdge - cardW) + (left ? slideX : -slideX);
                float cardX2 = cardX1 + cardW;
                float cardY1 = curY;
                float cardY2 = cardY1 + rowH;

                Color themeColor = this.getColor(now, idx);
                int rgb = themeColor.getRGB();

                if (hasBg) {
                    // Onyx ACCENT_BAR backdrop: surface 0.55 alpha, frosted
                    // glass when the blur pipeline is available.
                    float glassAlpha = (0.30F + 0.55F * bgPct) * p;
                    boolean glass = this.drawGlassCardBg(cardX1, cardY1, cardX2, cardY2, CARD_R, glassAlpha,
                            themeColor.getRGB(), useThemeBg, hasBg, bgPct, p);
                    if (!glass) {
                        if (useThemeBg) {
                            RenderUtil.drawRoundedRectGradient(cardX1, cardY1, cardX2, cardY2, CARD_R,
                                    new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(),
                                            (int) (bgPct * 220.0F * p)).getRGB(),
                                    new Color(themeColor.getRed(), themeColor.getGreen(), themeColor.getBlue(),
                                            (int) (bgPct * 110.0F * p)).getRGB());
                        } else {
                            RenderUtil.drawRoundedRectGradient(cardX1, cardY1, cardX2, cardY2, CARD_R,
                                    new Color(1.0F, 1.0F, 1.0F, 0.07F * bgPct * p).getRGB(),
                                    new Color(1.0F, 1.0F, 1.0F, 0.02F * bgPct * p).getRGB());
                        }
                    }
                    // no per-row bottom highlight — rows sit flush so the
                    // whole column reads as one continuous frame (Hengshui era)
                }

                // Onyx ACCENT_BAR: leading accent bar (drawn as one continuous
                // column line after the loop; per-row bar color comes from the
                // first row). TOP / BOTTOM bar modes keep their old behaviour.
                if (this.showBar.getValue()) {
                    if (idx == 0L) {
                        firstBarColor = rgb;
                    }
                    int barModeVal2 = this.barMode.getValue();
                    if (barModeVal2 == 2) {
                        if (idx == 0L) {
                            drawRoundedRect(cardX1, cardY1 - 3.0F, cardW, 2.0F, 1.0F, setAlpha(rgb, p));
                        }
                    } else if (barModeVal2 == 3) {
                        if (idx == this.activeModules.size() - 1) {
                            drawRoundedRect(cardX1, cardY2 + 1.0F, cardW, 2.0F, 1.0F, setAlpha(rgb, p));
                        }
                    }
                }

                GlStateManager.disableDepth();
                GlStateManager.pushMatrix();
                GlStateManager.translate(cardX1 + cardW / 2.0F, cardY1 + rowH / 2.0F, 0.0F);
                GlStateManager.scale(rowScale, rowScale, 1.0F);
                GlStateManager.translate(-(cardX1 + cardW / 2.0F), -(cardY1 + rowH / 2.0F), 0.0F);
                float textX = left ? cardX1 + PAD_X : cardX2 - PAD_X - textW;
                float textY = cardY1 + (rowH - f.height()) / 2.0F;
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
                GlStateManager.popMatrix();
                GlStateManager.enableDepth();

                this.lastRect.put(module, new float[]{cardX1, cardY1, cardX2, cardY2});
                curY += rowStep * (top ? 1.0F : -1.0F);
                idx++;
            }

            // One continuous side bar across the whole column.
            if (sideBar) {
                drawRoundedRect(barX, colTop, ACCENT_W, Math.max(0.0F, colBottom - colTop), 1.0F,
                        setAlpha(firstBarColor, 1.0F));
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
                drawRoundedRect(r[0], r[1], r[2] - r[0], r[3] - r[1], CARD_R,
                        new Color(1.0F, 1.0F, 1.0F, 0.06F * r[4]).getRGB());
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
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }
}
