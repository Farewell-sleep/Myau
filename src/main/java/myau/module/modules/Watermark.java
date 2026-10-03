package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.util.RenderUtil;
import myau.util.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;

import java.awt.*;

/**
 * Onxy-style client watermark with optional live stats, rewritten with Myau native rendering.
 */
public class Watermark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

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
        String title = "\u2665 Myau " + OpenMyau.version;
        StringBuilder line2 = new StringBuilder();
        if (this.showFps.getValue()) {
            if (line2.length() > 0) line2.append("  \u00a77\u2503  ");
            line2.append("\u00a7fFPS \u00a7a").append(Minecraft.getDebugFPS());
        }
        if (this.showPing.getValue()) {
            if (line2.length() > 0) line2.append("  \u00a77\u2503  ");
            line2.append("\u00a7fPing \u00a7a").append(this.getPing());
        }
        if (this.showUsername.getValue()) {
            if (line2.length() > 0) line2.append("  \u00a77\u2503  ");
            line2.append("\u00a7fUser \u00a7a").append(mc.getSession() != null ? mc.getSession().getUsername() : "Player");
        }
        if (this.showServer.getValue()) {
            if (line2.length() > 0) line2.append("  \u00a77\u2503  ");
            ServerData serverData = mc.getCurrentServerData();
            line2.append("\u00a7fServer \u00a7a").append(serverData != null ? serverData.serverIP : "Singleplayer");
        }

        ScaledResolution sr = new ScaledResolution(mc);
        float scale = this.scale.getValue();
        float x = (float) sr.getScaledWidth() * (this.posX.getValue().floatValue() / 100.0F);
        float y = (float) sr.getScaledHeight() * (this.posY.getValue().floatValue() / 100.0F);

        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        float sx = x / scale;
        float sy = y / scale;
        int w1 = FontManager.getStringWidth(title, 14.0F);
        int w2 = line2.length() > 0 ? FontManager.getStringWidth(line2.toString(), 12.0F) : 0;
        float boxW = Math.max(w1, w2) + 10.0F;
        float boxH = 22.0F;
        if (this.background.getValue()) {
            RenderUtil.drawRect(sx, sy, sx + boxW, sy + boxH, new Color(0, 0, 0, 90).getRGB());
            RenderUtil.drawRect(sx, sy, sx + 2.0F, sy + boxH, new Color(255, 85, 255, 220).getRGB());
        }
        FontManager.drawString(title, sx + 5.0F, sy + 2.0F, new Color(255, 85, 255).getRGB(), true, 14.0F);
        if (line2.length() > 0) {
            FontManager.drawString(line2.toString(), sx + 5.0F, sy + 14.0F, 0xFFFFFFFF, true, 12.0F);
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
