package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.risefont.RiseFontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

/**
 * 现代化客户端水印：玻璃胶囊（圆角=高度一半）+ 主题色标题 + FontManager 状态行。
 * 视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class Watermark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int GLASS_BODY = 0xB91A2028;
    private static final int GLASS_OUTLINE = 0x2AFFFFFF;
    private static final int TEXT_MAIN = 0xFFF2F4F8;
    private static final int TEXT_DIM = 0xFF8A92A6;
    private static final float FS = 8.0F;

    public final BooleanProperty showFps = new BooleanProperty("fps", true);
    public final BooleanProperty showPing = new BooleanProperty("ping", true);
    public final BooleanProperty showUsername = new BooleanProperty("username", false);
    public final BooleanProperty showServer = new BooleanProperty("server", true);
    public final BooleanProperty background = new BooleanProperty("background", true);
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final PercentProperty posX = new PercentProperty("position-x", 2);
    public final PercentProperty posY = new PercentProperty("position-y", 2);

    public Watermark() {
        super("Watermark", false, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.gameSettings.showDebugInfo) {
            return;
        }
        String title = "Myau " + OpenMyau.version;
        StringBuilder stats = new StringBuilder();
        if (this.showFps.getValue()) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append("FPS ").append(Minecraft.getDebugFPS());
        }
        if (this.showPing.getValue()) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append("Ping ").append(this.getPing());
        }
        if (this.showUsername.getValue()) {
            if (stats.length() > 0) stats.append("  ·  ");
            stats.append("User ").append(mc.getSession() != null ? mc.getSession().getUsername() : "Player");
        }
        if (this.showServer.getValue()) {
            if (stats.length() > 0) stats.append("  ·  ");
            ServerData serverData = mc.getCurrentServerData();
            stats.append("Server ").append(serverData != null ? serverData.serverIP : "Singleplayer");
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float scale = this.scale.getValue();
        float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F);
        float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float sx = x / scale;
        float sy = y / scale;

        HUD hud = (HUD) OpenMyau.moduleManager.modules.get(HUD.class);
        int accent = hud.getColor(System.currentTimeMillis()).getRGB();

        float w1 = (float) RiseFontManager.getStringWidth(title, FS);
        float w2 = stats.length() > 0 ? (float) RiseFontManager.getStringWidth(stats.toString(), FS) : 0.0F;
        float boxW = Math.max(w1, w2) + 12.0F;
        float boxH = 18.0F;
        float radius = boxH / 2.0F;

        RenderUtil.enableRenderState();
        if (this.background.getValue()) {
            RenderUtil.drawRoundedRect(sx, sy, boxW, boxH, radius, GLASS_BODY);
            RenderUtil.drawRoundedOutline(sx, sy, sx + boxW, sy + boxH, radius, 1.0F, GLASS_OUTLINE);
            // 主题色左强调点
            RenderUtil.drawRoundedRect(sx + 4.0F, sy + boxH / 2.0F - 1.5F, 3.0F, 3.0F, 1.5F, accent);
        }
        RenderUtil.disableRenderState();

        RiseFontManager.drawString(title, sx + 9.0F, sy + 2.0F, accent, false, FS);
        if (stats.length() > 0) {
            RiseFontManager.drawString(stats.toString(), sx + 9.0F, sy + 10.0F, TEXT_DIM, false, FS);
        }
        GlStateManager.popMatrix();
    }

    private int getPing() {
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return 0;
        }
        try {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            return info == null ? 0 : info.getResponseTime();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{"Onxy"};
    }
}
