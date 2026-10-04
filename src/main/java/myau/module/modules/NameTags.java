package myau.module.modules;

import myau.OpenMyau;
import myau.enums.ChatColors;
import myau.event.EventTarget;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorRenderManager;
import myau.module.Module;
import myau.util.ColorUtil;
import myau.util.RenderUtil;
import myau.util.TeamUtil;
import myau.property.properties.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NameTags extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormatter = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 2.0F);
    public final BooleanProperty autoScale = new BooleanProperty("auto-scale", true);
    public final PercentProperty backgroundOpacity = new PercentProperty("background", 25);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);
    public final ModeProperty distanceMode = new ModeProperty("distance", 0, new String[]{"NONE", "DEFAULT", "VAPE"});
    public final ModeProperty healthMode = new ModeProperty("health", 2, new String[]{"NONE", "HP", "HEARTS", "TAB"});
    public final BooleanProperty armor = new BooleanProperty("armor", true);
    public final BooleanProperty effects = new BooleanProperty("effects", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty friends = new BooleanProperty("friends", true);
    public final BooleanProperty enemies = new BooleanProperty("enemies", true);
    public final BooleanProperty bossees = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty creepers = new BooleanProperty("creepers", false);
    public final BooleanProperty endermans = new BooleanProperty("endermen", false);
    public final BooleanProperty blazes = new BooleanProperty("blazes", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty self = new BooleanProperty("self", false);
    public final BooleanProperty bots = new BooleanProperty("bots", false);

    public NameTags() {
        super("NameTags", false);
    }

    public boolean shouldRenderTags(EntityLivingBase entityLivingBase) {
        if (entityLivingBase.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityLivingBase) > 512.0F) {
            return false;
        } else if (entityLivingBase instanceof EntityPlayer) {
            if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.getRenderViewEntity()) {
                if (TeamUtil.isBot((EntityPlayer) entityLivingBase)) {
                    return this.bots.getValue();
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return this.friends.getValue();
                } else {
                    return TeamUtil.isTarget((EntityPlayer) entityLivingBase) ? this.enemies.getValue() : this.players.getValue();
                }
            } else {
                return this.self.getValue() && mc.gameSettings.thirdPersonView != 0;
            }
        } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
            return !entityLivingBase.isInvisible() && this.bossees.getValue();
        } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
            return (entityLivingBase instanceof EntityAnimal
                    || entityLivingBase instanceof EntityBat
                    || entityLivingBase instanceof EntitySquid
                    || entityLivingBase instanceof EntityVillager) && this.animals.getValue();
        } else if (entityLivingBase instanceof EntityCreeper) {
            return this.creepers.getValue();
        } else if (entityLivingBase instanceof EntityEnderman) {
            return this.endermans.getValue();
        } else {
            return entityLivingBase instanceof EntityBlaze ? this.blazes.getValue() : this.mobs.getValue();
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            for (Entity entity : TeamUtil.getLoadedEntitiesSorted()) {
                if (entity instanceof EntityLivingBase
                        && this.shouldRenderTags((EntityLivingBase) entity)
                        && (entity.ignoreFrustumCheck || RenderUtil.isInViewFrustum(entity.getEntityBoundingBox(), 10.0))) {
                    String teamName = TeamUtil.stripName(entity);
                    if (!StringUtils.isBlank(EnumChatFormatting.getTextWithoutFormattingCodes(teamName))) {
                        double x = RenderUtil.lerpDouble(entity.posX, entity.lastTickPosX, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                        double y = RenderUtil.lerpDouble(entity.posY, entity.lastTickPosY, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()
                                + (double) entity.getEyeHeight();
                        double z = RenderUtil.lerpDouble(entity.posZ, entity.lastTickPosZ, event.getPartialTicks())
                                - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                        double distance = mc.getRenderViewEntity().getDistanceToEntity(entity);
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(x, y + (entity.isSneaking() ? 0.225 : 0.4), z);
                        GlStateManager.rotate(mc.getRenderManager().playerViewY * -1.0F, 0.0F, 1.0F, 0.0F);
                        float view = mc.gameSettings.thirdPersonView == 2 ? -1.0F : 1.0F;
                        GlStateManager.rotate(mc.getRenderManager().playerViewX, view, 0.0F, 0.0F);
                        double scale = Math.pow(Math.min(Math.max(this.autoScale.getValue() ? distance : 0.0, 6.0), 128.0), 0.75) * 0.0075;
                        GlStateManager.scale(-scale * (double) this.scale.getValue(), -scale * (double) this.scale.getValue(), 1.0);
                        // 现代化名牌：玻璃卡片（半透明深色底 + 发丝描边 + 圆角）+ FontManager 舒窈衡水分段文本。
                        final float FONT = 8.0F;
                        final int TEXT_MAIN = 0xFFF2F4F8;
                        final int TEXT_DIM = 0xFF8A92A6;
                        final int HAIRLINE = 0x2AFFFFFF;

                        String distanceText = "";
                        switch (this.distanceMode.getValue()) {
                            case 1:
                                distanceText = (int) distance + "m ";
                                break;
                            case 2:
                                distanceText = "[" + (int) distance + "] ";
                        }
                        float health = ((EntityLivingBase) entity).getHealth();
                        float absorption = ((EntityLivingBase) entity).getAbsorptionAmount();
                        float max = ((EntityLivingBase) entity).getMaxHealth();
                        float percent = Math.min(Math.max((health + absorption) / max, 0.0F), 1.0F);
                        String healText = "";
                        switch (this.healthMode.getValue()) {
                            case 1:
                                healText = " " + (int) health + (absorption > 0.0F ? " " + (int) absorption : "");
                                break;
                            case 2:
                                healText = " " + healthFormatter.format((double) health / 2.0)
                                        + (absorption > 0.0F ? " " + healthFormatter.format((double) absorption / 2.0) : "");
                                break;
                            case 3:
                                if (entity instanceof EntityPlayer) {
                                    Scoreboard scoreboard = mc.theWorld.getScoreboard();
                                    if (scoreboard != null) {
                                        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(2);
                                        if (objective != null) {
                                            Score score = scoreboard.getValueFromObjective(entity.getName(), objective);
                                            if (score != null) {
                                                healText = " " + score.getScorePoints();
                                            }
                                        }
                                    }
                                }
                        }
                        String namePlain = EnumChatFormatting.getTextWithoutFormattingCodes(teamName);

                        // 分段：距离(弱色) / 名字(血量色) / 血量(主色)
                        List<String> segText = new ArrayList<>();
                        List<Integer> segColor = new ArrayList<>();
                        if (!distanceText.isEmpty()) {
                            segText.add(distanceText);
                            segColor.add(TEXT_DIM);
                        }
                        segText.add(namePlain);
                        segColor.add(ColorUtil.getHealthBlend(percent).getRGB());
                        if (!healText.isEmpty()) {
                            segText.add(healText);
                            segColor.add(TEXT_MAIN);
                        }

                        float totalW = 0.0F;
                        for (String s : segText) {
                            totalW += mc.fontRendererObj.getStringWidth(s);
                        }

                        // 玻璃卡片（GL 原语内联，不经过 myau RenderUtil，避免污染实体渲染）
                        float pad = 2.0F;
                        float cardL = -totalW / 2.0F - pad;
                        float cardT = -9.0F - pad;
                        float cardR = totalW / 2.0F + pad;
                        float cardB = pad;
                        if (this.backgroundOpacity.getValue() > 0) {
                            int bgA = (int) (this.backgroundOpacity.getValue() / 100.0 * 0xB9);
                            GlStateManager.enableBlend();
                            GlStateManager.disableTexture2D();
                            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                            GL11.glColor4f(
                                    (0x14 & 0xFF) / 255.0F, (0x20 & 0xFF) / 255.0F, (0x2B & 0xFF) / 255.0F,
                                    (bgA & 0xFF) / 255.0F);
                            GL11.glBegin(GL11.GL_QUADS);
                            GL11.glVertex2f(cardL, cardT);
                            GL11.glVertex2f(cardR, cardT);
                            GL11.glVertex2f(cardR, cardB);
                            GL11.glVertex2f(cardL, cardB);
                            GL11.glEnd();
                            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                            GL11.glLineWidth(1.0F);
                            GL11.glBegin(GL11.GL_LINE_LOOP);
                            GL11.glVertex2f(cardL, cardT);
                            GL11.glVertex2f(cardR, cardT);
                            GL11.glVertex2f(cardR, cardB);
                            GL11.glVertex2f(cardL, cardB);
                            GL11.glEnd();
                            GlStateManager.enableTexture2D();
                            GlStateManager.disableBlend();
                        }

                        GlStateManager.disableDepth();
                        float cursorX = -totalW / 2.0F;
                        for (int i = 0; i < segText.size(); i++) {
                            String s = segText.get(i);
                            mc.fontRendererObj.drawString(s, (int) cursorX, -9, segColor.get(i), this.shadow.getValue());
                            cursorX += mc.fontRendererObj.getStringWidth(s);
                        }
                        GlStateManager.enableDepth();

                        if (entity instanceof EntityPlayer) {
                            int height = (int) FONT + 2;
                            if (this.armor.getValue()) {
                                ArrayList<ItemStack> renderingItems = new ArrayList<>();
                                for (int i = 4; i >= 0; i--) {
                                    ItemStack itemStack;
                                    if (i == 0) {
                                        itemStack = ((EntityPlayer) entity).getHeldItem();
                                    } else {
                                        itemStack = ((EntityPlayer) entity).inventory.armorInventory[i - 1];
                                    }
                                    if (itemStack != null) {
                                        renderingItems.add(itemStack);
                                    }
                                }
                                if (!renderingItems.isEmpty()) {
                                    int offset = renderingItems.size() * -8;
                                    for (int i = 0; i < renderingItems.size(); i++) {
                                        RenderUtil.renderItemInGUI(renderingItems.get(i), offset + i * 16, -height - 16);
                                    }
                                    height += 16;
                                }
                            }
                            if (this.effects.getValue()) {
                                List<PotionEffect> effects = ((EntityPlayer) entity)
                                        .getActivePotionEffects()
                                        .stream()
                                        .filter(potionEffect -> Potion.potionTypes[potionEffect.getPotionID()].hasStatusIcon())
                                        .collect(Collectors.toList());
                                if (!effects.isEmpty()) {
                                    GlStateManager.pushMatrix();
                                    GlStateManager.scale(0.5F, 0.5F, 1.0F);
                                    int offset = effects.size() * -9;
                                    for (int i = 0; i < effects.size(); i++) {
                                        RenderUtil.renderPotionEffect(effects.get(i), offset + i * 18, -(height * 2) - 18);
                                    }
                                    GlStateManager.popMatrix();
                                }
                            }
                            if (TeamUtil.isFriend((EntityPlayer) entity)) {
                                int friendColor = OpenMyau.friendManager.getColor().getRGB();
                                RenderUtil.drawRoundedOutline(cardL, cardT, cardR, cardB, 3.0F, 1.0F, friendColor | 0xFF000000);
                            } else if (TeamUtil.isTarget((EntityPlayer) entity)) {
                                int targetColor = OpenMyau.targetManager.getColor().getRGB();
                                RenderUtil.drawRoundedOutline(cardL, cardT, cardR, cardB, 3.0F, 1.0F, targetColor | 0xFF000000);
                            }
                        }
                        GlStateManager.popMatrix();
                    }
                }
            }
        }
    }
}
