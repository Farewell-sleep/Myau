package myau.module.modules;

import myau.OpenMyau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.event.types.Priority;
import myau.events.Render2DEvent;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.FontManager;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.stream.Collectors;

/**
 * 现代化雷达：毛玻璃圆盘（主题色渐变填充 + 发丝描边）、柔和光点、
 * 旋转十字准星与 N/E/S/W 方位标签（FontManager 舒窈衡水）、可选 PVP 标记。
 * 视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class Radar extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("color", 0, new String[]{"DEFAULT", "TEAMS", "HUD"});
    public final IntProperty position = new IntProperty("position", 0, 0, 4);
    public final IntProperty offsetX = new IntProperty("offset-x", 60, 0, 1000, () -> position.getValue() != 4);
    public final IntProperty offsetY = new IntProperty("offset-y", 60, 0, 1000, () -> position.getValue() != 4);
    public final IntProperty radarRadius = new IntProperty("radar-radius", 55, 10, 200);
    public final FloatProperty dotRadius = new FloatProperty("dot-radius", 1.5F, 0.1F, 5.0F);
    public final BooleanProperty showPlayers = new BooleanProperty("players", true);
    public final BooleanProperty showFriends = new BooleanProperty("friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("bots", false);
    public final BooleanProperty showPVP = new BooleanProperty("show-pvp", false);
    public final ColorProperty fillColor = new ColorProperty("fill-color", Color.GRAY.getRGB());
    public final ColorProperty outlineColor = new ColorProperty("outline-color", Color.DARK_GRAY.getRGB());
    public final ColorProperty crossColor = new ColorProperty("cross-color", Color.LIGHT_GRAY.getRGB());

    private static final int TEXT_DIM = 0xFF8A92A6;   // 规范 TEXT_DIM
    private static final int HAIRLINE = 0x2AFFFFFF;   // 规范 GLASS_OUTLINE

    public Radar() {
        super("Radar", false);
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = OpenMyau.friendManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = OpenMyau.targetManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
        } else {
            switch (this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, 1.0F);
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(teamColor | 255 << 24, true);
                case 2:
                    int color = ((HUD) OpenMyau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                    return new Color(color | 255 << 24, true);
                default:
                    return Color.WHITE;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onRender(Render2DEvent event) {
        if (!this.isEnabled()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        HUD hud = (HUD) OpenMyau.moduleManager.modules.get(HUD.class);

        double centerX, centerY;
        if (position.getValue() == 4) {
            centerX = sr.getScaledWidth() / 2.0F;
            centerY = sr.getScaledHeight() / 2.0F;
        } else {
            centerX = (position.getValue() & 0x1) == 0x1 ? Math.max(sr.getScaledWidth() - offsetX.getValue(), 0) : Math.min(offsetX.getValue(), sr.getScaledWidth());
            centerY = (position.getValue() & 0x2) == 0x2 ? Math.max(sr.getScaledHeight() - offsetY.getValue(), 0) : Math.min(offsetY.getValue(), sr.getScaledHeight());
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 1.0f);
        GlStateManager.translate(centerX, centerY, 0.0f);

        RenderUtil.enableRenderState();

        float yaw = (float) Math.toRadians(mc.thePlayer.rotationYaw);
        if (mc.gameSettings.thirdPersonView != 2) {
            yaw += (float) Math.toRadians(180.0F);
        }
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);

        Color fill = new Color(fillColor.getValue());
        int radius = radarRadius.getValue();

        // 玻璃圆盘：主题色/自定义色 3 层径向渐变 + 发丝描边 + 十字
        drawGlassDisc(0.0, 0.0, radius, fill, outlineColor.getValue());
        drawCompassCross(0.0, 0.0, radius, yaw, crossColor.getValue());

        for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
            double dx = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks()) - mc.thePlayer.posX;
            double dz = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks()) - mc.thePlayer.posZ;

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;

            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radius ? 1.0F : (double) radius / dist;
            double px = relX * scale;
            double py = relY * scale;

            Color dot = getEntityColor(player);
            int glow = (dot.getRGB() & 0xFFFFFF) | 0x28000000; // 柔和光晕层（低 alpha）
            RenderUtil.fillCircle(px, py, dotRadius.getValue() * 3.2F, 12, glow);
            RenderUtil.fillCircle(px, py, dotRadius.getValue(), 12, dot.getRGB());
        }
        if (this.showPVP.getValue()) {
            double dx = -mc.thePlayer.posX;
            double dz = -mc.thePlayer.posZ;

            double relX = dx * cos + dz * sin;
            double relY = dz * cos - dx * sin;

            double dist = Math.sqrt(relX * relX + relY * relY);
            double scale = dist < radius * 2 ? 1.0F : (double) radius * 2 / dist;
            double px = relX * scale;
            double py = relY * scale;

            GlStateManager.disableDepth();
            FontManager.drawString("PVP",
                    (float) (px - FontManager.getStringWidth("PVP") / 2.0F),
                    (float) (py - FontManager.getFontHeight() / 2.0F),
                    Color.WHITE.getRGB(), hud.shadow.getValue());
            GlStateManager.enableDepth();
        }
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    /** 玻璃圆盘：三层同心径向渐变（外淡内实）+ 1px 发丝描边。 */
    private void drawGlassDisc(double x, double y, int radius, Color base, int outline) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        // 最外：极淡的轮廓光
        if (base.getAlpha() > 0) {
            RenderUtil.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 26).getRGB());
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(x, y);
            for (int i = 0; i <= 64; i++) {
                double a = i * (Math.PI * 2 / 64);
                GL11.glVertex2d(x + Math.cos(a) * radius, y + Math.sin(a) * radius);
            }
            GL11.glEnd();
        }
        // 中层：主体半透明玻璃
        if (base.getAlpha() > 0) {
            RenderUtil.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 90).getRGB());
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(x, y);
            for (int i = 0; i <= 64; i++) {
                double a = i * (Math.PI * 2 / 64);
                GL11.glVertex2d(x + Math.cos(a) * (radius - 3), y + Math.sin(a) * (radius - 3));
            }
            GL11.glEnd();
        }
        // 内核：最实的中心圆
        if (base.getAlpha() > 0) {
            RenderUtil.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 150).getRGB());
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);
            GL11.glVertex2d(x, y);
            for (int i = 0; i <= 64; i++) {
                double a = i * (Math.PI * 2 / 64);
                GL11.glVertex2d(x + Math.cos(a) * (radius - 6), y + Math.sin(a) * (radius - 6));
            }
            GL11.glEnd();
        }
        // 发丝描边
        if ((outline >>> 24) != 0) {
            RenderUtil.setColor(outline);
            GL11.glLineWidth(1.0f);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i <= 64; i++) {
                double a = i * (Math.PI * 2 / 64);
                GL11.glVertex2d(x + Math.cos(a) * radius, y + Math.sin(a) * radius);
            }
            GL11.glEnd();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    /** 旋转十字 + N/E/S/W 方位标签（FontManager）。 */
    private void drawCompassCross(double x, double y, int radius, double angle, int crossColor) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        double dx1 = Math.sin(angle);
        double dy1 = Math.cos(angle);
        double dx2 = Math.sin(angle + Math.PI / 2);
        double dy2 = Math.cos(angle + Math.PI / 2);
        if ((crossColor >>> 24) != 0) {
            RenderUtil.setColor(crossColor);
            GL11.glLineWidth(1.5f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2d(x - dx1 * radius, y - dy1 * radius);
            GL11.glVertex2d(x + dx1 * radius, y + dy1 * radius);
            GL11.glVertex2d(x - dx2 * radius, y - dy2 * radius);
            GL11.glVertex2d(x + dx2 * radius, y + dy2 * radius);
            GL11.glEnd();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();

        HUD hud = (HUD) OpenMyau.moduleManager.modules.get(HUD.class);
        int color = hud.getColor(System.currentTimeMillis()).getRGB();
        boolean shadow = hud.shadow.getValue();
        GlStateManager.disableDepth();
        FontManager.drawString("N",
                (float) (x - dx1 * (radius + 5)) - FontManager.getStringWidth("N") / 2.0F,
                (float) (y - dy1 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                color, shadow);
        FontManager.drawString("E",
                (float) (x + dx2 * (radius + 5)) - FontManager.getStringWidth("E") / 2.0F,
                (float) (y + dy2 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                color, shadow);
        FontManager.drawString("S",
                (float) (x + dx1 * (radius + 5)) - FontManager.getStringWidth("S") / 2.0F,
                (float) (y + dy1 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                color, shadow);
        FontManager.drawString("W",
                (float) (x - dx2 * (radius + 5)) - FontManager.getStringWidth("W") / 2.0F,
                (float) (y - dy2 * (radius + 5)) - FontManager.getFontHeight() / 2.0F,
                color, shadow);
        GlStateManager.enableDepth();
    }
}
