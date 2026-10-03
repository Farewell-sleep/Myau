package myau.module.modules;

import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.RenderUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ColorProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Trajectories extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"ORIGINAL", "ONYX"});
    public final PercentProperty opacity = new PercentProperty("opacity", 100);
    public final BooleanProperty bow = new BooleanProperty("bow", true);
    public final BooleanProperty projectiles = new BooleanProperty("projectiles", false);
    public final BooleanProperty pearls = new BooleanProperty("pearls", true);

    // === ONYX (skid Onxy TrajectoriesModule) ===
    public final IntProperty onyxMaxTicks = new IntProperty("onyx-max-ticks", 120, 20, 300, () -> this.mode.getValue() == 1);
    public final FloatProperty onyxLineWidth = new FloatProperty("onyx-line-width", 1.5F, 0.5F, 5.0F, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxThroughWalls = new BooleanProperty("onyx-through-walls", false, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxImpactMarker = new BooleanProperty("onyx-impact-marker", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxPearls = new BooleanProperty("onyx-pearls", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxSnowballs = new BooleanProperty("onyx-snowballs", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxPotions = new BooleanProperty("onyx-potions", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxBows = new BooleanProperty("onyx-bows", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty onyxStopAtEntities = new BooleanProperty("onyx-stop-at-entities", true, () -> this.mode.getValue() == 1);
    public final ColorProperty onyxColor = new ColorProperty("onyx-color", 0xFF7744, () -> this.mode.getValue() == 1);
    public final ColorProperty onyxImpactColor = new ColorProperty("onyx-impact-color", 0xFFFF22, () -> this.mode.getValue() == 1);

    public Trajectories() {
        super("Trajectories", false, true);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null
                || mc.thePlayer.getHeldItem() == null || mc.gameSettings.thirdPersonView != 0) {
            return;
        }
        if (this.mode.getValue() == 1) {
            this.renderOnyx(event.getPartialTicks());
        } else {
            this.renderOriginal();
        }
    }
    private void renderOriginal() {
            Item item = mc.thePlayer.getHeldItem().getItem();
            RenderManager renderManager = mc.getRenderManager();
            boolean isBow = false;
            float velocityMultiplier = 1.5F;
            float drag = 0.99F;
            float gravity;
            float hitboxExpand;
            if (item instanceof ItemBow && this.bow.getValue()) {
                if (!mc.thePlayer.isUsingItem()) {
                    return;
                }
                isBow = true;
                gravity = 0.05F;
                hitboxExpand = 0.3F;
                float charge = (float) mc.thePlayer.getItemInUseDuration() / 20.0F;
                charge = (charge * charge + charge * 2.0F) / 3.0F;
                if (charge < 0.1F) {
                    return;
                }
                if (charge > 1.0F) {
                    charge = 1.0F;
                }
                velocityMultiplier = charge * 3.0F;
            } else if (item instanceof ItemFishingRod && this.projectiles.getValue()) {
                gravity = 0.04F;
                hitboxExpand = 0.25F;
                drag = 0.92F;
            } else if ((item instanceof ItemSnowball || item instanceof ItemEgg) && this.projectiles.getValue()) {
                gravity = 0.03F;
                hitboxExpand = 0.25F;
            } else {
                if (!(item instanceof ItemEnderPearl) || !this.pearls.getValue()) {
                    return;
                }
                gravity = 0.03F;
                hitboxExpand = 0.25F;
            }
            float yaw = mc.thePlayer.rotationYaw;
            float pitch = mc.thePlayer.rotationPitch;
            double x = ((IAccessorRenderManager) renderManager).getRenderPosX() - (double) MathHelper.cos(yaw / 180.0F * (float) Math.PI) * 0.16;
            double y = ((IAccessorRenderManager) renderManager).getRenderPosY() + (double) mc.thePlayer.getEyeHeight() - 0.1F;
            double z = ((IAccessorRenderManager) renderManager).getRenderPosZ() - (double) MathHelper.sin(yaw / 180.0F * (float) Math.PI) * 0.16;
            double mx = (double) (MathHelper.sin(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI))
                    * (isBow ? 1.0 : 0.4)
                    * -1.0;
            double my = (double) MathHelper.sin(pitch / 180.0F * (float) Math.PI) * (isBow ? 1.0 : 0.4) * -1.0;
            double mz = (double) (MathHelper.cos(yaw / 180.0F * (float) Math.PI) * MathHelper.cos(pitch / 180.0F * (float) Math.PI)) * (isBow ? 1.0 : 0.4);
            float mag = MathHelper.sqrt_double(mx * mx + my * my + mz * mz);
            mx /= mag;
            my /= mag;
            mz /= mag;
            mx *= velocityMultiplier;
            my *= velocityMultiplier;
            mz *= velocityMultiplier;
            MovingObjectPosition mop = null;
            boolean hasHitBlock = false;
            boolean hasHitEntity = false;
            WorldRenderer worldRenderer = Tessellator.getInstance().getWorldRenderer();
            ArrayList<Vec3> trajectoryPoints = new ArrayList<>();
            while (!hasHitBlock && y > 0.0) {
                Vec3 start = new Vec3(x, y, z);
                Vec3 end = new Vec3(x + mx, y + my, z + mz);
                mop = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
                start = new Vec3(x, y, z);
                end = new Vec3(x + mx, y + my, z + mz);
                if (mop != null) {
                    hasHitBlock = true;
                    end = new Vec3(mop.hitVec.xCoord, mop.hitVec.yCoord, mop.hitVec.zCoord);
                }
                AxisAlignedBB aabb = new AxisAlignedBB(
                        x - (double) hitboxExpand,
                        y - (double) hitboxExpand,
                        z - (double) hitboxExpand,
                        x + (double) hitboxExpand,
                        y + (double) hitboxExpand,
                        z + (double) hitboxExpand
                )
                        .addCoord(mx, my, mz)
                        .expand(1.0, 1.0, 1.0);
                int minChunkX = MathHelper.floor_double((aabb.minX - 2.0) / 16.0);
                int maxChunkX = MathHelper.floor_double((aabb.maxX + 2.0) / 16.0);
                int minChunkZ = MathHelper.floor_double((aabb.minZ - 2.0) / 16.0);
                int maxChunkZ = MathHelper.floor_double((aabb.maxZ + 2.0) / 16.0);
                ArrayList<Entity> possibleEntities = new ArrayList<>();
                for (int x1 = minChunkX; x1 <= maxChunkX; ++x1) {
                    for (int z1 = minChunkZ; z1 <= maxChunkZ; ++z1) {
                        mc.theWorld.getChunkFromChunkCoords(x1, z1).getEntitiesWithinAABBForEntity(mc.thePlayer, aabb, possibleEntities, null);
                    }
                }
                for (Entity entity : possibleEntities) {
                    if (entity.canBeCollidedWith() && entity != mc.thePlayer) {
                        AxisAlignedBB entityBox = entity.getEntityBoundingBox().expand(hitboxExpand, hitboxExpand, hitboxExpand);
                        MovingObjectPosition intercept = entityBox.calculateIntercept(start, end);
                        if (intercept != null) {
                            hasHitEntity = true;
                            hasHitBlock = true;
                            mop = intercept;
                        }
                    }
                }
                x += mx;
                y += my;
                z += mz;
                if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial() == Material.water) {
                    mx *= 0.6;
                    my *= 0.6;
                    mz *= 0.6;
                } else {
                    mx *= drag;
                    my *= drag;
                    mz *= drag;
                }
                my -= gravity;
                trajectoryPoints.add(
                        new Vec3(
                                x - ((IAccessorRenderManager) renderManager).getRenderPosX(),
                                y - ((IAccessorRenderManager) renderManager).getRenderPosY(),
                                z - ((IAccessorRenderManager) renderManager).getRenderPosZ()
                        )
                );
            }
            if (trajectoryPoints.size() > 1) {
                RenderUtil.enableRenderState();
                RenderUtil.setColor(new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB());
                GL11.glLineWidth(1.5F);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
                trajectoryPoints.forEach(vec3 -> worldRenderer.pos(vec3.xCoord, vec3.yCoord, vec3.zCoord).endVertex());
                Tessellator.getInstance().draw();
                GlStateManager.pushMatrix();
                GlStateManager.translate(
                        x - ((IAccessorRenderManager) renderManager).getRenderPosX(),
                        y - ((IAccessorRenderManager) renderManager).getRenderPosY(),
                        z - ((IAccessorRenderManager) renderManager).getRenderPosZ()
                );
                if (mop != null) {
                    switch (mop.sideHit.getAxis().ordinal()) {
                        case 0:
                            GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);
                            break;
                        case 1:
                            GlStateManager.rotate(90.0F, 1.0F, 0.0F, 0.0F);
                    }
                    RenderUtil.drawLine(
                            -0.25F,
                            -0.25F,
                            0.25F,
                            0.25F,
                            1.5F,
                            new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB()
                    );
                    RenderUtil.drawLine(
                            -0.25F,
                            0.25F,
                            0.25F,
                            -0.25F,
                            1.5F,
                            new Color(hasHitEntity ? 85 : 255, 255, hasHitEntity ? 85 : 255, (int) (this.opacity.getValue().floatValue() / 100.0F * 255.0F)).getRGB()
                    );
                }
                GlStateManager.popMatrix();
                GL11.glDisable(GL11.GL_LINE_SMOOTH);
                GL11.glLineWidth(2.0F);
                GlStateManager.resetColor();
                RenderUtil.disableRenderState();
            }
    }

    // === ONYX MODE (skid Onxy TrajectoriesModule) ===

    private enum ProjType {
        PEARL(1.5F, 0.03, 0.0F),
        SNOWBALL(1.5F, 0.03, 0.0F),
        POTION(0.5F, 0.05, -20.0F),
        ARROW(0.0F, 0.05, 0.0F);

        final float velocity;
        final double gravity;
        final float pitchOffset;

        ProjType(float velocity, double gravity, float pitchOffset) {
            this.velocity = velocity;
            this.gravity = gravity;
            this.pitchOffset = pitchOffset;
        }
    }

    private ProjType getProjType(ItemStack held) {
        Item item = held.getItem();
        if (item instanceof ItemEnderPearl) {
            return this.onyxPearls.getValue() ? ProjType.PEARL : null;
        } else if (item instanceof ItemSnowball || item instanceof ItemEgg) {
            return this.onyxSnowballs.getValue() ? ProjType.SNOWBALL : null;
        } else if (item instanceof ItemPotion) {
            return this.onyxPotions.getValue() && ItemPotion.isSplash(held.getMetadata()) ? ProjType.POTION : null;
        } else if (item instanceof ItemBow) {
            return this.onyxBows.getValue() ? ProjType.ARROW : null;
        }
        return null;
    }

    private float getVelocity(ProjType type) {
        if (type != ProjType.ARROW) {
            return type.velocity;
        }
        if (!mc.thePlayer.isUsingItem()) {
            return 0.0F;
        }
        float v = mc.thePlayer.getItemInUseDuration() / 20.0F;
        float out = (v * v + v * 2.0F) / 3.0F;
        if (out < 0.1F) {
            return 0.0F;
        }
        if (out > 1.0F) {
            out = 1.0F;
        }
        return out * 2.0F * 1.5F;
    }

    private Vec3 getEntityHit(Vec3 start, Vec3 end) {
        if (!this.onyxStopAtEntities.getValue()) {
            return null;
        }
        AxisAlignedBB box = AxisAlignedBB.fromBounds(
                Math.min(start.xCoord, end.xCoord),
                Math.min(start.yCoord, end.yCoord),
                Math.min(start.zCoord, end.zCoord),
                Math.max(start.xCoord, end.xCoord),
                Math.max(start.yCoord, end.yCoord),
                Math.max(start.zCoord, end.zCoord)
        ).expand(1.0, 1.0, 1.0);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, box)) {
            if (!entity.canBeCollidedWith() || entity == mc.thePlayer) {
                continue;
            }
            MovingObjectPosition mop = entity.getEntityBoundingBox().expand(0.3, 0.3, 0.3).calculateIntercept(start, end);
            if (mop != null && mop.hitVec != null) {
                double dist = start.squareDistanceTo(mop.hitVec);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = mop.hitVec;
                }
            }
        }
        return best;
    }

    private void renderOnyx(float partialTicks) {
        ItemStack held = mc.thePlayer.getHeldItem();
        ProjType type = getProjType(held);
        if (type == null) {
            return;
        }
        float velocity = getVelocity(type);
        if (velocity <= 0.0F) {
            return;
        }
        double yaw = mc.thePlayer.rotationYaw / 180.0 * Math.PI;
        double pitch = mc.thePlayer.rotationPitch / 180.0 * Math.PI;
        double pitch2 = (mc.thePlayer.rotationPitch + type.pitchOffset) / 180.0 * Math.PI;
        double x = mc.thePlayer.posX - Math.cos(yaw) * 0.16;
        double y = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1;
        double z = mc.thePlayer.posZ - Math.sin(yaw) * 0.16;
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch2);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        double mag = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (mag < 1.0E-6) {
            return;
        }
        dx = dx / mag * velocity;
        dy = dy / mag * velocity;
        dz = dz / mag * velocity;
        double gravity = type.gravity;
        ArrayList<Vec3> points = new ArrayList<>();
        Vec3 hit = null;
        points.add(new Vec3(x, y, z));
        int maxTicks = this.onyxMaxTicks.getValue();
        for (int i = 0; i < maxTicks; i++) {
            Vec3 start = new Vec3(x, y, z);
            x += dx;
            y += dy;
            z += dz;
            Vec3 end = new Vec3(x, y, z);
            MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(start, end, false, true, false);
            if (mop != null && mop.hitVec != null) {
                points.add(mop.hitVec);
                hit = mop.hitVec;
                break;
            }
            Vec3 entityHit = getEntityHit(start, end);
            if (entityHit != null) {
                points.add(entityHit);
                hit = entityHit;
                break;
            }
            points.add(end);
            if (y < 0.0) {
                break;
            }
            dx *= 0.99;
            dy *= 0.99;
            dz *= 0.99;
            dy -= gravity;
        }
        if (points.size() < 2) {
            return;
        }
        RenderManager rm = mc.getRenderManager();
        double rx = ((IAccessorRenderManager) rm).getRenderPosX();
        double ry = ((IAccessorRenderManager) rm).getRenderPosY();
        double rz = ((IAccessorRenderManager) rm).getRenderPosZ();

        int lineColor = this.onyxColor.getValue() | 0xFF000000;
        RenderUtil.enableRenderState();
        RenderUtil.setColor(new Color(lineColor).getRGB());
        GL11.glLineWidth(this.onyxLineWidth.getValue());
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        WorldRenderer worldRenderer = Tessellator.getInstance().getWorldRenderer();
        worldRenderer.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION);
        for (Vec3 point : points) {
            worldRenderer.pos(point.xCoord - rx, point.yCoord - ry, point.zCoord - rz).endVertex();
        }
        Tessellator.getInstance().draw();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(2.0F);
        GlStateManager.resetColor();
        RenderUtil.disableRenderState();

        if (hit != null && this.onyxImpactMarker.getValue()) {
            double s = 0.12;
            AxisAlignedBB box = AxisAlignedBB.fromBounds(
                    hit.xCoord - s, hit.yCoord - s, hit.zCoord - s,
                    hit.xCoord + s, hit.yCoord + s, hit.zCoord + s
            ).offset(-rx, -ry, -rz);
            Color impact = new Color(this.onyxImpactColor.getValue() | 0xFF000000);
            RenderUtil.enableRenderState();
            RenderUtil.drawBoundingBox(box, impact.getRed(), impact.getGreen(), impact.getBlue(), 200, this.onyxLineWidth.getValue());
            RenderUtil.disableRenderState();
        }
    }
}
