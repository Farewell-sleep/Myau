package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.risefont.RiseFontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Solid 3D Duck Orbiting Player — skidded from user script (duck.java).
 * A duck made of shaded boxes orbits the player while bobbing, waddling and
 * flapping its wings. GL state is fully isolated (pushAttrib/popAttrib +
 * shader/framebuffer reset) so it can never corrupt world / player skin
 * rendering.
 */
public class Duck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 3.0F);
    public final FloatProperty orbitSpeed = new FloatProperty("orbit-speed", 2.0F, 0.5F, 5.0F);
    public final FloatProperty radius = new FloatProperty("orbit-radius", 2.2F, 1.0F, 6.0F);
    public final FloatProperty height = new FloatProperty("height", 1.4F, 0.5F, 4.0F);
    public final BooleanProperty hudLabel = new BooleanProperty("hud-label", true);

    public Duck() {
        super("Duck", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        double scale = this.scale.getValue();
        double orbitSpeed = this.orbitSpeed.getValue();
        double radius = this.radius.getValue();
        double height = this.height.getValue();
        long time = System.currentTimeMillis();

        double orbitAngle = (time * 0.0015 * orbitSpeed) % (Math.PI * 2.0);
        double bob = Math.sin(time * 0.005) * 0.15;
        double waddle = Math.sin(time * 0.008) * 8.0;
        double wingFlap = Math.sin(time * 0.012) * 16.0;

        double duckX = mc.thePlayer.posX + Math.cos(orbitAngle) * radius;
        double duckY = mc.thePlayer.posY + height + bob;
        double duckZ = mc.thePlayer.posZ + Math.sin(orbitAngle) * radius;

        double renderX = duckX - mc.getRenderManager().viewerPosX;
        double renderY = duckY - mc.getRenderManager().viewerPosY;
        double renderZ = duckZ - mc.getRenderManager().viewerPosZ;

        double faceAngle = Math.toDegrees(-orbitAngle) + 90.0;

        GlStateManager.pushAttrib();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(renderX, renderY, renderZ);
            GlStateManager.rotate((float) faceAngle, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate((float) waddle, 0.0F, 0.0F, 1.0F);
            GlStateManager.scale((float) scale, (float) scale, (float) scale);

            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.enableCull();
            GlStateManager.disableBlend();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();

            // body
            drawBoxShaded(-0.3, -0.2, -0.4, 0.3, 0.2, 0.3, 1.0F, 0.9F, 0.15F, 1.0F);
            // tail
            drawBoxShaded(-0.12, 0.05, -0.52, 0.12, 0.22, -0.38, 1.0F, 0.85F, 0.1F, 1.0F);
            // head / neck
            drawBoxShaded(-0.22, 0.2, -0.05, 0.22, 0.55, 0.32, 1.0F, 0.92F, 0.2F, 1.0F);
            // beak
            drawBoxShaded(-0.14, 0.28, 0.32, 0.14, 0.38, 0.55, 1.0F, 0.5F, 0.0F, 1.0F);

            // wings
            drawBoxShaded(0.221, 0.25, 0.1, 0.225, 0.33, 0.24, 1.0F, 0.4F, 0.6F, 1.0F);
            drawBoxShaded(-0.225, 0.25, 0.1, -0.221, 0.33, 0.24, 1.0F, 0.4F, 0.6F, 1.0F);

            // wing tips
            drawBoxShaded(0.221, 0.38, 0.14, 0.225, 0.48, 0.24, 0.05F, 0.05F, 0.05F, 1.0F);
            drawBoxShaded(-0.225, 0.38, 0.14, -0.221, 0.48, 0.24, 0.05F, 0.05F, 0.05F, 1.0F);

            // eyes
            drawBoxShaded(0.226, 0.44, 0.20, 0.228, 0.47, 0.23, 1.0F, 1.0F, 1.0F, 1.0F);
            drawBoxShaded(-0.228, 0.44, 0.20, -0.226, 0.47, 0.23, 1.0F, 1.0F, 1.0F, 1.0F);

            // wing flap (right)
            GlStateManager.pushMatrix();
            GlStateManager.translate(0.3F, 0.0F, 0.0F);
            GlStateManager.rotate((float) wingFlap, 0.0F, 0.0F, 1.0F);
            drawBoxShaded(0.0, -0.1, -0.25, 0.1, 0.14, 0.25, 1.0F, 0.95F, 0.3F, 1.0F);
            GlStateManager.popMatrix();

            // wing flap (left)
            GlStateManager.pushMatrix();
            GlStateManager.translate(-0.3F, 0.0F, 0.0F);
            GlStateManager.rotate((float) -wingFlap, 0.0F, 0.0F, 1.0F);
            drawBoxShaded(-0.1, -0.1, -0.25, 0.0, 0.14, 0.25, 1.0F, 0.95F, 0.3F, 1.0F);
            GlStateManager.popMatrix();

            // feet
            drawBoxShaded(0.08, -0.32, -0.15, 0.22, -0.2, 0.2, 1.0F, 0.5F, 0.0F, 1.0F);
            drawBoxShaded(-0.22, -0.32, -0.15, -0.08, -0.2, 0.2, 1.0F, 0.5F, 0.0F, 1.0F);
        } finally {
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
            org.lwjgl.opengl.GL20.glUseProgram(0);
            mc.getFramebuffer().bindFramebuffer(true);
        }
    }

    /** Six-sided shaded box (GL_QUADS, per-face brightness like the script).
     *  Colors go through GlStateManager so its cache stays in sync — raw
     *  GL11.glColor4f used to leave the last color behind and tinted the
     *  player skin in first person. */
    private static void drawBoxShaded(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_QUADS);
        // top
        GlStateManager.color(r, g, b, a);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        // back (0.9)
        GlStateManager.color(r * 0.9F, g * 0.9F, b * 0.9F, a);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        // front (0.85)
        GlStateManager.color(r * 0.85F, g * 0.85F, b * 0.85F, a);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        // bottom (0.6)
        GlStateManager.color(r * 0.6F, g * 0.6F, b * 0.6F, a);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        // right (0.75)
        GlStateManager.color(r * 0.75F, g * 0.75F, b * 0.75F, a);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        // left (0.75)
        GlStateManager.color(r * 0.75F, g * 0.75F, b * 0.75F, a);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glEnd();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || !this.hudLabel.getValue() || mc.thePlayer == null) {
            return;
        }
        RenderUtil.drawRect(10.0F, 10.0F, 170.0F, 40.0F, 0x90000000);
        RiseFontManager.drawString("Solid Duck Orbit Active", 15.0F, 18.0F, 0xFFFFE033, true);
    }
}
