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
import myau.util.RenderUtil;
import myau.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Darkheart interface: Rice-style flat module arraylist (left-top, white text
 * with grey suffixes) plus a dynamic-island widget at the top-center showing
 * client name, server address and ping, with the hotbar and a scaffold blocks
 * progress bar underneath.
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
    public final IntProperty offsetX = new IntProperty("offset-x", 4, 0, 255);
    public final IntProperty offsetY = new IntProperty("offset-y", 4, 0, 255);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 0);
    public final IntProperty rowSpacing = new IntProperty("row-spacing", 1, 0, 10);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final BooleanProperty suffixes = new BooleanProperty("suffixes", true);
    public final BooleanProperty lowerCase = new BooleanProperty("lower-case", false);
    public final BooleanProperty chatOutline = new BooleanProperty("chat-outline", true);
    public final BooleanProperty blinkTimer = new BooleanProperty("blink-timer", true);
    public final BooleanProperty toggleSound = new BooleanProperty("toggle-sounds", true);
    public final BooleanProperty toggleAlerts = new BooleanProperty("toggle-alerts", false);
    public final BooleanProperty glow = new BooleanProperty("glow", false);
    public final BooleanProperty dynamicIsland = new BooleanProperty("dynamic-island", true);
    public final BooleanProperty hotbar = new BooleanProperty("hotbar", true);
    public final BooleanProperty blocksProgress = new BooleanProperty("blocks-progress", true);

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
        int width = mc.fontRendererObj.getStringWidth(string);
        if (this.suffixes.getValue()) {
            for (String str : arr) {
                width += 3 + mc.fontRendererObj.getStringWidth(str);
            }
        }
        return width;
    }

    private static int setAlpha(int color, float alpha) {
        int a = (int) (alpha * 255.0F);
        return (color & 0xFFFFFF) | (a << 24);
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

    private void drawGlowText(String text, float x, float y, int color, int passes, float spread) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableTexture2D();
        for (int i = passes; i >= 1; i--) {
            float offset = i * spread;
            float intensity = (float) (passes - i + 1) / (float) passes;
            int glowColor = setAlpha(color, 0.10F * intensity * intensity);
            float diagonal = offset * 0.65F;
            mc.fontRendererObj.drawString(text, x + offset, y, glowColor, false);
            mc.fontRendererObj.drawString(text, x - offset, y, glowColor, false);
            mc.fontRendererObj.drawString(text, x, y + offset, glowColor, false);
            mc.fontRendererObj.drawString(text, x, y - offset, glowColor, false);
            mc.fontRendererObj.drawString(text, x + diagonal, y + diagonal, glowColor, false);
            mc.fontRendererObj.drawString(text, x - diagonal, y + diagonal, glowColor, false);
            mc.fontRendererObj.drawString(text, x + diagonal, y - diagonal, glowColor, false);
            mc.fontRendererObj.drawString(text, x - diagonal, y - diagonal, glowColor, false);
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
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) return;

        ScaledResolution sr = new ScaledResolution(mc);
        if (this.dynamicIsland.getValue()) {
            this.drawDynamicIsland(sr);
        }
        this.drawModuleList(sr);
    }

    private void drawModuleList(ScaledResolution sr) {
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        float height = mc.fontRendererObj.FONT_HEIGHT;
        float scale = this.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);

        float x = this.offsetX.getValue();
        float y = this.offsetY.getValue();
        if (this.posX.getValue() == 1) {
            x = sw / scale - x;
        }
        if (this.posY.getValue() == 1) {
            y = sh / scale - y;
        }

        long l = System.currentTimeMillis();
        long offset = 0L;
        float rowH = height + this.rowSpacing.getValue();

        for (Module module : this.activeModules) {
            String moduleName = this.getModuleName(module);
            String[] moduleSuffix = this.getModuleSuffix(module);
            float totalWidth = this.calculateStringWidth(moduleName, moduleSuffix);
            Color themeColor = this.getColor(l, offset);
            int color = themeColor.getRGB();

            float textX = x;
            if (this.posX.getValue() == 1) {
                textX = x - totalWidth;
            }
            float textY = y;
            if (this.posY.getValue() == 1) {
                textY = y - rowH;
            }

            // flat background (optional, Rice default is none)
            if (this.background.getValue() > 0) {
                float pad = 2.0F;
                float bgX1 = textX - pad;
                float bgY1 = textY - 1.0F;
                float bgW = totalWidth + pad * 2.0F;
                float bgH = height + 2.0F;
                int bgAlphaColor = new Color(0.0F, 0.0F, 0.0F, this.background.getValue().floatValue() / 100.0F).getRGB();
                RenderUtil.enableRenderState();
                RenderUtil.drawRoundedRect(bgX1, bgY1, bgW, bgH, 2.0F, bgAlphaColor);
                RenderUtil.disableRenderState();
            }

            if (this.glow.getValue()) {
                drawGlowText(moduleName, textX, textY, color, 3, 0.55F);
            }
            if (this.shadow.getValue()) {
                mc.fontRendererObj.drawStringWithShadow(moduleName, textX, textY, color);
            } else {
                mc.fontRendererObj.drawString(moduleName, textX, textY, color, false);
            }

            if (this.suffixes.getValue() && moduleSuffix.length > 0) {
                float suffixX = mc.fontRendererObj.getStringWidth(moduleName) + 3.0F;
                int grey = ChatColors.GRAY.toAwtColor();
                for (String string : moduleSuffix) {
                    if (this.glow.getValue()) {
                        drawGlowText(string, textX + suffixX, textY, grey, 2, 0.35F);
                    }
                    if (this.shadow.getValue()) {
                        mc.fontRendererObj.drawStringWithShadow(string, textX + suffixX, textY, grey);
                    } else {
                        mc.fontRendererObj.drawString(string, textX + suffixX, textY, grey, false);
                    }
                    suffixX += mc.fontRendererObj.getStringWidth(string) + (this.shadow.getValue() ? 3.0F : 2.0F);
                }
            }

            y += (this.posY.getValue() == 0 ? 1.0F : -1.0F) * rowH;
            offset++;
        }

        GlStateManager.popMatrix();
    }

    private String getServerLabel() {
        if (mc.getCurrentServerData() != null) {
            return mc.getCurrentServerData().serverIP;
        }
        return "Singleplayer";
    }

    private String getPingLabel() {
        if (mc.getCurrentServerData() != null) {
            return mc.getCurrentServerData().pingToServer + "ms";
        }
        return "0ms";
    }

    private int countHotbarBlocks() {
        if (mc.thePlayer == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack != null && stack.getItem() instanceof ItemBlock) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private void drawDynamicIsland(ScaledResolution sr) {
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();

        String client = "Darkheart";
        String server = this.getServerLabel();
        String ping = this.getPingLabel();
        int accent = this.getColor(System.currentTimeMillis()).getRGB();

        float padX = 14.0F;
        float textH = 9.0F;
        float islandH = 22.0F;

        int wClient = mc.fontRendererObj.getStringWidth(client);
        int wServer = mc.fontRendererObj.getStringWidth(server);
        int wPing = mc.fontRendererObj.getStringWidth(ping);
        float segGap = 18.0F;
        float totalW = wClient + segGap + wServer + segGap + wPing + padX * 2.0F;
        float x0 = sw / 2.0F - totalW / 2.0F;
        float y0 = 6.0F;

        // capsule
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(x0, y0, totalW, islandH, islandH / 2.0F, 0xB812141C);
        RenderUtil.drawRoundedRect(x0 + 1.0F, y0 + 1.0F, totalW - 2.0F, islandH - 2.0F, (islandH - 2.0F) / 2.0F, 0xE61A1D26);
        RenderUtil.disableRenderState();

        float textY = y0 + islandH / 2.0F - textH / 2.0F - 1.0F;

        // segment 1: client name (accent color)
        mc.fontRendererObj.drawStringWithShadow(client, x0 + padX, textY, accent);
        // segment 2: server address (white)
        float x2 = x0 + padX + wClient + segGap;
        mc.fontRendererObj.drawStringWithShadow(server, x2, textY, 0xFFFFFFFF);
        // segment 3: ping
        float x3 = x2 + wServer + segGap;
        mc.fontRendererObj.drawStringWithShadow(ping, x3, textY, 0xFFDFE4EE);

        // hotbar + scaffold progress below the island
        float belowY = y0 + islandH + 8.0F;
        if (this.hotbar.getValue() && mc.thePlayer != null) {
            this.drawIslandHotbar(sw, belowY);
            belowY += 26.0F;
        }
        if (this.blocksProgress.getValue() && mc.thePlayer != null) {
            this.drawScaffoldProgress(sw, belowY);
        }
    }

    private void drawIslandHotbar(int sw, float y) {
        int slotW = 18;
        int gap = 2;
        int totalW = 9 * slotW + 8 * gap;
        int x0 = sw / 2 - totalW / 2;

        // background
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(x0 - 4.0F, y - 4.0F, totalW + 8.0F, slotW + 8.0F, 6.0F, 0xB812141C);
        RenderUtil.disableRenderState();

        GlStateManager.enableRescaleNormal();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        RenderHelper.enableGUIStandardItemLighting();

        for (int slot = 0; slot < 9; slot++) {
            int sx = x0 + slot * (slotW + gap);
            ItemStack stack = mc.thePlayer.inventory.mainInventory[slot];
            if (stack != null) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, sx, (int) y);
                mc.getRenderItem().renderItemOverlayIntoGUI(mc.fontRendererObj, stack, sx, (int) y, null);
            }
            // slot background
            GlStateManager.disableLighting();
            RenderUtil.enableRenderState();
            RenderUtil.drawRect(sx, y, sx + slotW, y + slotW, 0x24FFFFFF);
            RenderUtil.disableRenderState();
            // selected slot border
            if (slot == mc.thePlayer.inventory.currentItem) {
                RenderUtil.enableRenderState();
                RenderUtil.drawOutlineRect(sx - 1.0F, y - 1.0F, sx + slotW + 1.0F, y + slotW + 1.0F, 1.5F, 0, 0xFFFFFFFF);
                RenderUtil.disableRenderState();
            }
            GlStateManager.enableLighting();
        }

        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableBlend();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawScaffoldProgress(int sw, float y) {
        int blocks = this.countHotbarBlocks();
        int barW = 9 * 18 + 8 * 2;
        int x0 = sw / 2 - barW / 2;
        float barH = 5.0F;

        String label = "Blocks " + blocks;
        int labelW = mc.fontRendererObj.getStringWidth(label);
        mc.fontRendererObj.drawStringWithShadow(label, sw / 2.0F - labelW / 2.0F, y - 10.0F, 0xFFDCE1EA);

        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(x0 - 2.0F, y, barW + 4.0F, barH + 2.0F, 3.5F, 0x99141820);
        float progress = Math.min(1.0F, blocks / 576.0F);
        if (progress > 0.01F) {
            RenderUtil.drawRoundedRect(x0 - 2.0F, y, (barW + 4.0F) * progress, barH + 2.0F, 3.5F,
                    this.getColor(System.currentTimeMillis()).getRGB() & 0xFFFFFF | 0xE0000000);
        }
        RenderUtil.disableRenderState();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
