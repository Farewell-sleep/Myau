package myau.module.modules;

import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import myau.events.Render3DEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.risefont.RiseFontManager;
import myau.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * ITEM ESP — skidded from Raven B4 (keystrokesmod) into OpenMyau.
 * Renders 3D boxes + stack-count billboards around nearby item entities
 * (iron ingot / gold ingot / diamond / emerald), grouped by position.
 */
public class ItemESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty renderIron = new BooleanProperty("Render iron", true);
    public final BooleanProperty renderGold = new BooleanProperty("Render gold", true);
    public final FloatProperty maxDistance = new FloatProperty("Max distance", 128.0F, 32.0F, 256.0F);

    private final ArrayList<ItemRenderState> renderStates = new ArrayList<>();
    private final HashMap<Double, Integer> stackCounts = new HashMap<>();
    private int renderStateCount = 0;

    public ItemESP() {
        super("ItemESP", false);
    }

    @EventTarget
    public void onTick(PlayerUpdateEvent event) {
        updateRenderStates();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!nullCheck() || renderStateCount == 0) {
            return;
        }

        float partialTicks = event.getPartialTicks();
        EntityPlayer self = mc.thePlayer;
        if (self == null) {
            return;
        }

        for (int i = 0; i < renderStateCount; i++) {
            ItemRenderState renderState = renderStates.get(i);
            EntityItem entityItem = renderState.entityItem;
            if (entityItem == null || entityItem.isDead || entityItem.getEntityItem() == null || entityItem.getEntityItem().stackSize == 0) {
                continue;
            }

            Integer stackCount = stackCounts.get(renderState.groupKey);
            if (stackCount == null) {
                continue;
            }

            double interpolatedX = entityItem.lastTickPosX + (entityItem.posX - entityItem.lastTickPosX) * partialTicks;
            double interpolatedY = entityItem.lastTickPosY + (entityItem.posY - entityItem.lastTickPosY) * partialTicks;
            double interpolatedZ = entityItem.lastTickPosZ + (entityItem.posZ - entityItem.lastTickPosZ) * partialTicks;

            double diffX = self.lastTickPosX + (self.posX - self.lastTickPosX) * partialTicks - interpolatedX;
            double diffY = self.lastTickPosY + (self.posY - self.lastTickPosY) * partialTicks - interpolatedY;
            double diffZ = self.lastTickPosZ + (self.posZ - self.lastTickPosZ) * partialTicks - interpolatedZ;
            double dist = MathHelper.sqrt_double(diffX * diffX + diffY * diffY + diffZ * diffZ);

            GlStateManager.pushMatrix();
            drawBox(renderState.boxColor, renderState.textColor, stackCount, interpolatedX, interpolatedY, interpolatedZ, dist);
            GlStateManager.popMatrix();
        }
    }

    private void updateRenderStates() {
        renderStateCount = 0;
        stackCounts.clear();
        if (!nullCheck() || mc.theWorld == null) {
            return;
        }

        double maxDistSq = maxDistance.getValue() * maxDistance.getValue();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem)) {
                continue;
            }
            if (!isWithinDistanceSqToRenderView(entity, maxDistSq)) {
                continue;
            }
            if (entity.ticksExisted < 3) {
                continue;
            }

            EntityItem entityItem = (EntityItem) entity;
            if (entityItem.getEntityItem() == null || entityItem.getEntityItem().stackSize == 0) {
                continue;
            }

            Item item = entityItem.getEntityItem().getItem();
            if (item == null) {
                continue;
            }

            int boxColor;
            int textColor;
            if (item == Items.iron_ingot && renderIron.getValue()) {
                boxColor = -1;
                textColor = -1;
            } else if (item == Items.gold_ingot && renderGold.getValue()) {
                boxColor = -331703;
                textColor = -152;
            } else if (item == Items.diamond) {
                boxColor = -10362113;
                textColor = -7667713;
            } else if (item == Items.emerald) {
                boxColor = -15216030;
                textColor = -14614644;
            } else {
                continue;
            }

            double groupKey = getColorForItem(item, entity.posX, entity.posY, entity.posZ);
            Integer existingStackCount = stackCounts.get(groupKey);
            stackCounts.put(groupKey, (existingStackCount == null ? 0 : existingStackCount) + entityItem.getEntityItem().stackSize);

            if (renderStateCount >= renderStates.size()) {
                renderStates.add(new ItemRenderState());
            }
            renderStates.get(renderStateCount++).set(entityItem, boxColor, textColor, groupKey);
        }
    }

    private boolean isWithinDistanceSqToRenderView(Entity entity, double maxDistSq) {
        Entity view = mc.getRenderViewEntity();
        return view != null && entity.getDistanceSqToEntity(view) <= maxDistSq;
    }

    private boolean nullCheck() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public double getColor(double x, double y, double z) {
        if (x == 0.0) {
            x = 1.0;
        }
        if (y == 0.0) {
            y = 1.0;
        }
        if (z == 0.0) {
            z = 1.0;
        }
        return Math.round((x + 1.0) * Math.floor(y) * (z + 2.0));
    }

    private double getColorForItem(Item item, double x, double y, double z) {
        double color = getColor(x, y, z);
        if (item == Items.iron_ingot) {
            color += 0.155;
        } else if (item == Items.gold_ingot) {
            color += 0.255;
        } else if (item == Items.diamond) {
            color += 0.355;
        } else if (item == Items.emerald) {
            color += 0.455;
        }
        return color;
    }

    public void drawBox(int boxColor, int textColor, int size, double posY, double posX, double posZ, double dist) {
        posY -= mc.getRenderManager().viewerPosX;
        posX -= mc.getRenderManager().viewerPosY;
        posZ -= mc.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        try {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        float r = (boxColor >> 16 & 0xFF) / 255.0f;
        float g = (boxColor >> 8 & 0xFF) / 255.0f;
        float b = (boxColor & 0xFF) / 255.0f;

        float radius = Math.min(Math.max(0.2f, (float) (0.009999999776482582 * dist)), 0.4f);
        AxisAlignedBB box = new AxisAlignedBB(posY - radius, posX, posZ - radius, posY + radius, posX + radius * 2.0f, posZ + radius);
        // 现代化：半透明玻璃填充 + 1px 发丝描边
        RenderUtil.drawFilledBoundingBox(box, r, g, b, 0.15f);
        RenderUtil.drawBoundingBox(box, (int)(r*255f), (int)(g*255f), (int)(b*255f), (int) (0.45f * 255.0f), 1.0f);
        } finally {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) posY, (float) posX + 0.3, (float) posZ);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        float scale = Math.min(Math.max(0.02266667f, (float) (0.001500000013038516 * dist)), 0.07f);
        GlStateManager.scale(-scale, -scale, -scale);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        String value = String.valueOf(size);
        RiseFontManager.drawString(value, -RiseFontManager.getStringWidth(value, 8.0F) / 2.0F + scale * 3.5f, -(123.805f * scale - 2.47494f), textColor, true, 8.0F);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.popMatrix();
    }

    private static void drawBoundingBox(AxisAlignedBB aabb, float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        GL11.glVertex3d(aabb.minX, aabb.minY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.minY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.minY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.minY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.minY, aabb.minZ);
        GL11.glVertex3d(aabb.minX, aabb.maxY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.maxY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.maxY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.maxY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.maxY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.maxY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.minY, aabb.minZ);
        GL11.glVertex3d(aabb.maxX, aabb.maxY, aabb.maxZ);
        GL11.glVertex3d(aabb.maxX, aabb.minY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.maxY, aabb.maxZ);
        GL11.glVertex3d(aabb.minX, aabb.minY, aabb.maxZ);
        GL11.glEnd();
    }

    private static final class ItemRenderState {
        private EntityItem entityItem;
        private int boxColor;
        private int textColor;
        private double groupKey;

        private void set(EntityItem entityItem, int boxColor, int textColor, double groupKey) {
            this.entityItem = entityItem;
            this.boxColor = boxColor;
            this.textColor = textColor;
            this.groupKey = groupKey;
        }
    }
}
