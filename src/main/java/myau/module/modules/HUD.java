package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.ColorUtil;
import myau.util.FontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Onxy-style array list, rewritten with Myau native rendering.
 * Style: Accent Bar / Pills / Text · Animation: Slide / Fade / Slide+Fade / Scale
 * Sort: Longest first / Shortest first / A-Z · Colors: Accent / Static / Fade /
 * Gradient / Rainbow / Category. Draggable while the chat is open.
 */
public class HUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty style = new ModeProperty("style", 0, new String[]{"ACCENT BAR", "PILLS", "TEXT"});
    public final ModeProperty animation = new ModeProperty("animation", 2, new String[]{"SLIDE", "FADE", "SLIDE FADE", "SCALE"});
    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"WIDTH DESC", "WIDTH ASC", "ALPHABETICAL"});
    public final ModeProperty colors = new ModeProperty("colors", 0, new String[]{"ACCENT", "STATIC", "FADE", "GRADIENT", "RAINBOW", "CATEGORY"});
    public final ColorProperty color = new ColorProperty("color", 0xFF3B82F6, () -> this.colors.getValue() == 1);
    public final ColorProperty firstColor = new ColorProperty("first-color", 0xFF3B82F6, () -> this.colors.getValue() == 2 || this.colors.getValue() == 3);
    public final ColorProperty secondColor = new ColorProperty("second-color", 0xFFEC4899, () -> this.colors.getValue() == 2 || this.colors.getValue() == 3);
    public final FloatProperty colorSpeed = new FloatProperty("color-speed", 1.0F, 0.1F, 5.0F);
    public final FloatProperty colorSpread = new FloatProperty("color-spread", 12.0F, 0.0F, 60.0F, () -> this.colors.getValue() == 2 || this.colors.getValue() == 4);
    public final BooleanProperty suffix = new BooleanProperty("suffix", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty categoryCombat = new BooleanProperty("category-combat", true);
    public final BooleanProperty categoryMovement = new BooleanProperty("category-movement", true);
    public final BooleanProperty categoryPlayer = new BooleanProperty("category-player", true);
    public final BooleanProperty categoryRender = new BooleanProperty("category-render", true);
    public final BooleanProperty categoryHud = new BooleanProperty("category-hud", true);
    public final BooleanProperty categoryMisc = new BooleanProperty("category-misc", true);
    public final BooleanProperty background = new BooleanProperty("background", true, () -> this.style.getValue() != 2);
    public final BooleanProperty textShadow = new BooleanProperty("text-shadow", true, () -> this.style.getValue() == 2);
    public final BooleanProperty outline = new BooleanProperty("outline", false, () -> this.style.getValue() == 1);
    /** Shared text-shadow flag used by Radar / BedNuker overlays. */
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final FloatProperty rowSpacing = new FloatProperty("row-spacing", 0.0F, 0.0F, 6.0F);
    public final FloatProperty animationSpeed = new FloatProperty("animation-speed", 1.0F, 0.25F, 3.0F);
    public final PercentProperty positionX = new PercentProperty("position-x", 100);
    public final PercentProperty positionY = new PercentProperty("position-y", 0);

    // Myau theme colours — shared with ESP lines / old ClickGui components.
    public final ModeProperty colorMode = new ModeProperty(
            "color", 3, new String[]{"RAINBOW", "CHROMA", "ASTOLFO", "CUSTOM1", "CUSTOM12", "CUSTOM123"}
    );
    public final PercentProperty colorSaturation = new PercentProperty("color-saturation", 50);
    public final PercentProperty colorBrightness = new PercentProperty("color-brightness", 100);
    public final ColorProperty custom1 = new ColorProperty("custom-color-1", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 3 || this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom2 = new ColorProperty("custom-color-2", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 4 || this.colorMode.getValue() == 5);
    public final ColorProperty custom3 = new ColorProperty("custom-color-3", Color.WHITE.getRGB(), () -> this.colorMode.getValue() == 5);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);

    private final Map<Module, Long> enterTimes = new HashMap<>();
    private final List<Module> activeModules = new ArrayList<>();
    private boolean dragging;
    private float dragOffX;
    private float dragOffY;

    public HUD() {
        super("HUD", true, true);
    }

    private float getColorCycle(long long3, long long4) {
        long speed = (long) (3000.0 / Math.pow(Math.min(Math.max(0.5F, this.colorSpeed.getValue()), 1.5F), 3.0));
        return 1.0F - (float) (Math.abs(long3 - long4 * 300L) % speed) / (float) speed;
    }

    /** Shared theme colour used by ESP lines / old ClickGui components. */
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
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Module> fresh = OpenMyau.moduleManager.modules.values().stream()
                .filter(m -> m.isEnabled() && !m.isHidden() && this.categoryEnabled(m))
                .collect(Collectors.toList());
        this.enterTimes.keySet().retainAll(fresh);
        for (Module m : fresh) {
            if (!this.enterTimes.containsKey(m)) {
                this.enterTimes.put(m, now);
            }
        }
        switch (this.sort.getValue()) {
            case 0:
                fresh.sort(Comparator.comparingDouble((Module m) -> -this.rowWidth(m)).thenComparing(Module::getName));
                break;
            case 1:
                fresh.sort(Comparator.comparingDouble((Module m) -> (double) this.rowWidth(m)).thenComparing(Module::getName));
                break;
            default:
                fresh.sort(Comparator.comparing(Module::getName));
        }
        this.activeModules.clear();
        this.activeModules.addAll(fresh);
    }

    private boolean categoryEnabled(Module m) {
        String cat = categoryOf(m);
        switch (cat == null ? "Misc" : cat) {
            case "Combat":
                return this.categoryCombat.getValue();
            case "Movement":
                return this.categoryMovement.getValue();
            case "Player":
                return this.categoryPlayer.getValue();
            case "Render":
                return this.categoryRender.getValue();
            case "HUD":
                return this.categoryHud.getValue();
            default:
                return this.categoryMisc.getValue();
        }
    }

    private static String categoryOf(Module m) {
        Class<? extends Module> c = m.getClass();
        for (Class<? extends Module> k : COMBAT) if (k.isAssignableFrom(c)) return "Combat";
        for (Class<? extends Module> k : MOVEMENT) if (k.isAssignableFrom(c)) return "Movement";
        for (Class<? extends Module> k : PLAYER) if (k.isAssignableFrom(c)) return "Player";
        for (Class<? extends Module> k : RENDER) if (k.isAssignableFrom(c)) return "Render";
        for (Class<? extends Module> k : HUD_CAT) if (k.isAssignableFrom(c)) return "HUD";
        return "Misc";
    }

    private static final Class<? extends Module>[] COMBAT = new Class[]{
            AimAssist.class, AutoClicker.class, KillAura.class, Wtap.class, Velocity.class, Freeze.class,
            Reach.class, TargetStrafe.class, NoHitDelay.class, AntiFireball.class, LagRange.class, BackTrack.class,
            BlockHit.class, Autoblock.class, HitBox.class, MoreKB.class, Refill.class, HitSelect.class,
            AutoThrow.class, NewKillAura.class, SmartAttack.class
    };
    private static final Class<? extends Module>[] MOVEMENT = new Class[]{
            AntiAFK.class, Fly.class, Speed.class, LongJump.class, Sprint.class, SafeWalk.class, Jesus.class,
            Blink.class, NoFall.class, NoSlow.class, KeepSprint.class, Eagle.class, NoJumpDelay.class, AntiVoid.class
    };
    private static final Class<? extends Module>[] PLAYER = new Class[]{
            Clutch.class, AutoHeal.class, AutoTool.class, ChestStealer.class, InvManager.class, InvWalk.class,
            Scaffold.class, Telly.class, NewScaffold.class, AutoBlockIn.class, SpeedMine.class, FastPlace.class,
            GhostHand.class, MCF.class, AntiDebuff.class
    };
    private static final Class<? extends Module>[] RENDER = new Class[]{
            ESP.class, Chams.class, FullBright.class, Tracers.class, NameTags.class, Xray.class, BedESP.class,
            ItemESP.class, ItemPhysics.class, BreakProgress.class, Freelook.class, ViewClip.class, NoHurtCam.class,
            GuiModule.class, ChestESP.class, Trajectories.class, Radar.class, CuteVisuals.class, SnowFog.class,
            Zoom.class, Crosshair.class, SeeInvisibles.class, Ambience.class, Hurtcam.class, FogRemove.class
    };
    private static final Class<? extends Module>[] HUD_CAT = new Class[]{
            HUD.class, TargetHUD.class, Indicators.class, PotionHUD.class, Watermark.class, Keybinds.class
    };

    private String moduleName(Module m) {
        return this.lowerCase.getValue() ? m.getName().toLowerCase() : m.getName();
    }

    private String moduleSuffix(Module m) {
        String[] s = m.getSuffix();
        if (s == null || s.length == 0 || s[0] == null || s[0].isEmpty()) {
            return null;
        }
        return this.lowerCase.getValue() ? s[0].toLowerCase() : s[0];
    }

    private int rowWidth(Module m) {
        int w = FontManager.getStringWidth(this.moduleName(m), 9.0F);
        String suffix = this.suffix.getValue() ? this.moduleSuffix(m) : null;
        if (suffix != null) {
            w += 3 + FontManager.getStringWidth(suffix, 9.0F);
        }
        return w;
    }

    private int rowColor(int index, int total, Module m) {
        double now = System.nanoTime() / 1.0E9D;
        int cat = (this.colors.getValue() == 5 && this.categoryOf(m) != null)
                ? this.categoryColor(this.categoryOf(m)) : 0;
        switch (this.colors.getValue()) {
            case 0:
                return 0xFF3B82F6;
            case 1:
                return this.color.getValue();
            case 2:
                float t = (float) (Math.sin(now * this.colorSpeed.getValue() * 2.0D
                        + Math.toRadians(index * this.colorSpread.getValue())) + 1.0F) / 2.0F;
                return ColorUtil.interpolate(t, new Color(this.firstColor.getValue(), true), new Color(this.secondColor.getValue(), true)).getRGB();
            case 3:
                float g = total <= 1 ? 0.0F : (float) index / (float) (total - 1);
                return ColorUtil.interpolate(g, new Color(this.firstColor.getValue(), true), new Color(this.secondColor.getValue(), true)).getRGB();
            case 4:
                float h = (float) ((now * 60.0D * this.colorSpeed.getValue() + index * this.colorSpread.getValue()) % 360.0D);
                return Color.getHSBColor(h / 360.0F, 0.75F, 1.0F).getRGB();
            case 5:
                return cat;
            default:
                return 0xFF3B82F6;
        }
    }

    private int categoryColor(String cat) {
        switch (cat) {
            case "Combat":
                return 0xFFFF5555;
            case "Movement":
                return 0xFF55FF55;
            case "Player":
                return 0xFF55FFFF;
            case "Render":
                return 0xFFAA55FF;
            case "HUD":
                return 0xFF3B82F6;
            default:
                return 0xFFFFAA55;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo || this.activeModules.isEmpty()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float scale = this.scale.getValue();
        float rowH = (14.0F + this.rowSpacing.getValue()) * scale;
        boolean right = this.positionX.getValue() > 50;
        float anchorX = (float) sr.getScaledWidth() * (this.positionX.getValue().floatValue() / 100.0F);
        float anchorY = (float) sr.getScaledHeight() * (this.positionY.getValue().floatValue() / 100.0F);
        if (right) {
            anchorX -= 1.0F * scale;
        }

        this.handleDrag(sr, anchorX, anchorY, right, scale, rowH);

        long now = System.currentTimeMillis();
        float duration = 250.0F / this.animationSpeed.getValue();
        float x = anchorX / scale;
        float y0 = anchorY / scale;

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        int total = this.activeModules.size();
        for (int i = 0; i < total; i++) {
            Module m = this.activeModules.get(i);
            String name = this.moduleName(m);
            String suffixText = this.suffix.getValue() ? this.moduleSuffix(m) : null;
            float nameW = FontManager.getStringWidth(name, 9.0F);
            float suffixW = suffixText != null ? FontManager.getStringWidth(suffixText, 9.0F) + 3.0F : 0.0F;
            float textW = nameW + suffixW;
            float padLeft = this.style.getValue() == 0 ? 8.0F : (this.style.getValue() == 1 ? 8.0F : 2.0F);
            float padRight = this.style.getValue() == 0 ? 6.0F : (this.style.getValue() == 1 ? 8.0F : 2.0F);
            float rowW = textW + padLeft + padRight;

            float progress = 1.0F;
            Long enter = this.enterTimes.get(m);
            if (enter != null && (this.animation.getValue() == 1 || this.animation.getValue() == 2 || this.animation.getValue() == 3)) {
                progress = Math.min(1.0F, (now - enter) / duration);
            }
            float alpha = progress;
            float slideOff = 0.0F;
            if (this.animation.getValue() == 0 || this.animation.getValue() == 2) {
                slideOff = (1.0F - progress) * 14.0F * (right ? -1.0F : 1.0F);
            }

            float rowY = y0 + i * rowH;
            float rowX = right ? x - rowW : x;
            float drawX = rowX + slideOff;
            int color = this.rowColor(i, total, m);

            GlStateManager.pushMatrix();
            if (this.animation.getValue() == 3) {
                float cx = drawX + rowW / 2.0F;
                float cy = rowY + 7.0F;
                GlStateManager.translate(cx, cy, 0);
                GlStateManager.scale(0.9F + 0.1F * progress, 0.9F + 0.1F * progress, 1.0F);
                GlStateManager.translate(-cx, -cy, 0);
            }

            int bgColor = (0x00000000 | ((int) (0.42F * 255.0F * alpha) << 24));
            int rowColorAlpha = (color & 0xFFFFFF) | ((int) (255.0F * alpha) << 24);
            RenderUtil.enableRenderState();
            if (this.style.getValue() == 0) {
                if (this.background.getValue()) {
                    RenderUtil.drawRoundedRect(drawX, rowY, rowW, 14.0F, 2.0F, bgColor);
                    RenderUtil.drawRect(right ? drawX : drawX + rowW - 2.0F, rowY + 1.0F, (right ? drawX + 2.0F : drawX + rowW), rowY + 13.0F, rowColorAlpha);
                }
            } else if (this.style.getValue() == 1) {
                if (this.background.getValue()) {
                    RenderUtil.drawRoundedRect(drawX, rowY, rowW, 14.0F, 7.0F, bgColor);
                }
                if (this.outline.getValue()) {
                    RenderUtil.drawOutlineRect(drawX, rowY, drawX + rowW, rowY + 14.0F, 1.0F, 0, rowColorAlpha);
                }
            }
            RenderUtil.disableRenderState();

            float textX = drawX + padLeft;
            float textY = rowY + 1.0F;
            FontManager.drawString(name, textX, textY, rowColorAlpha, this.textShadow.getValue() || this.style.getValue() != 2, 9.0F);
            if (suffixText != null) {
                int suffixColor = (0xFFAAAAAA & 0xFFFFFF) | ((int) (255.0F * alpha) << 24);
                FontManager.drawString(suffixText, textX + nameW + 3.0F, textY, suffixColor, this.textShadow.getValue() || this.style.getValue() != 2, 9.0F);
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.popMatrix();
    }

    private void handleDrag(ScaledResolution sr, float anchorX, float anchorY, boolean right, float scale, float rowH) {
        if (!(mc.currentScreen instanceof GuiChat)) {
            this.dragging = false;
            return;
        }
        float x = anchorX / scale;
        float y0 = anchorY / scale;
        float totalW = 0.0F;
        for (Module m : this.activeModules) {
            float w = this.rowWidth(m) + (this.style.getValue() == 0 ? 14.0F : (this.style.getValue() == 1 ? 16.0F : 4.0F));
            totalW = Math.max(totalW, w);
        }
        float boxX = right ? x - totalW : x;
        float boxY = y0 - 1.0F;
        float boxH = this.activeModules.size() * rowH + 2.0F;
        int mx = Mouse.getX() * sr.getScaledWidth() / mc.displayWidth;
        int my = sr.getScaledHeight() - Mouse.getY() * sr.getScaledHeight() / mc.displayHeight - 1;
        boolean over = mx >= boxX && mx <= boxX + totalW && my >= boxY && my <= boxY + boxH;
        if (Mouse.isButtonDown(0)) {
            if (!this.dragging && over) {
                this.dragging = true;
                this.dragOffX = mx - anchorX;
                this.dragOffY = my - anchorY;
            }
            if (this.dragging) {
                float nx = (mx - this.dragOffX) / (float) sr.getScaledWidth() * 100.0F;
                float ny = (my - this.dragOffY) / (float) sr.getScaledHeight() * 100.0F;
                this.positionX.setValue((int) Math.round(Math.max(0.0F, Math.min(100.0F, nx))));
                this.positionY.setValue((int) Math.round(Math.max(0.0F, Math.min(100.0F, ny))));
            }
        } else {
            this.dragging = false;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Onxy"};
    }
}
