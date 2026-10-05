package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.events.PlayerUpdateEvent;
import myau.events.Render3DEvent;
import myau.mixin.IAccessorPlayerControllerMP;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.module.modules.LeaderFontManager;
import myau.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * BREAK PROGRESS — 破坏进度：在准星所指方块上方画圆角玻璃进度条
 * （背景玻璃底 + 主题色渐变填充），数值用 FontManager。模式 Percentage / Time / Decimal。
 * 视觉风格遵循 RENDER_SPEC 第 2 节令牌。
 */
public class BreakProgress extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Percentage", "Time", "Decimal"});
    public final BooleanProperty manual = new BooleanProperty("Show manual", true);
    public final BooleanProperty bedAura = new BooleanProperty("Show bedAura", true);
    public final BooleanProperty fadeIn = new BooleanProperty("Fade in", false);

    private static final int GLASS_BODY = 0xB91A2028;
    private static final float FS = 8.0F;

    private BlockPos block;
    private float progress;
    private String progressStr = "";

    public BreakProgress() {
        super("BreakProgress", false);
    }

    @EventTarget
    public void onUpdate(PlayerUpdateEvent event) {
        if (!isEnabled()) {
            return;
        }
        if (mc.thePlayer.capabilities.isCreativeMode || !mc.thePlayer.capabilities.allowEdit) {
            this.resetVariables();
            return;
        }

        if (bedAura.getValue() && OpenMyau.moduleManager != null) {
            BedNuker bedNuker = (BedNuker) OpenMyau.moduleManager.modules.get(BedNuker.class);
            if (bedNuker != null && bedNuker.isEnabled()) {
                BlockPos auraTarget = bedNuker.getAuraTargetPos();
                float auraProgress = bedNuker.getAuraBreakProgress();
                if (auraTarget != null && auraProgress > 0.0F) {
                    this.progress = Math.min(1.0F, auraProgress);
                    this.block = auraTarget;
                    this.setProgress();
                    return;
                }
            }
        }

        if (!manual.getValue() || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectType.BLOCK) {
            this.resetVariables();
            return;
        }

        this.progress = ((IAccessorPlayerControllerMP) mc.playerController).getCurBlockDamageMP();
        if (this.progress == 0.0F) {
            this.resetVariables();
            return;
        }

        this.block = mc.objectMouseOver.getBlockPos();
        this.setProgress();
    }

    private void setProgress() {
        switch (this.mode.getValue()) {
            case 0:
                this.progressStr = (int) (100.0 * (this.progress / 1.0)) + "%";
                break;
            case 1: {
                double timeLeft = round((1.0F - this.progress) / getBlockHardness(getBlock(this.block), mc.thePlayer.getHeldItem()) / 20.0, 1);
                this.progressStr = timeLeft == 0 ? "0" : timeLeft + "s";
                break;
            }
            case 2:
                this.progressStr = String.valueOf(round(this.progress, 2));
                break;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.progress == 0.0F || this.block == null || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        double x = this.block.getX() + 0.5 - mc.getRenderManager().viewerPosX;
        double y = this.block.getY() + 0.5 - mc.getRenderManager().viewerPosY;
        double z = this.block.getZ() + 0.5 - mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate((mc.gameSettings.thirdPersonView == 2 ? -1 : 1) * mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-0.02266667F, -0.02266667F, -0.02266667F);
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GL11.glEnable(GL11.GL_BLEND);

        HUD hud = (HUD) OpenMyau.moduleManager.modules.get(HUD.class);
        int accent = hud.getColor(System.currentTimeMillis()).getRGB();

        int alpha = this.fadeIn.getValue() ? Math.max(26, (int) (255 * progress)) : 255;
        int textColor = 0x00FFFFFF | (alpha << 24);
        int glass = GLASS_BODY | (alpha << 24);
        int fill = accent | (alpha << 24);

        float barW = 42.0F;
        float barH = 4.0F;
        float barY = -2.0F;

        // 圆角玻璃底 + 主题色前景填充
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRect(-barW / 2.0F, barY, barW, barH, 2.0F, glass);
        float fillW = Math.max(barW * this.progress, 0.0F);
        if (fillW > 0.5F) {
            RenderUtil.drawRoundedRect(-barW / 2.0F, barY, fillW, barH, 2.0F, fill);
        }
        RenderUtil.disableRenderState();

        float textW = (float) LeaderFontManager.getStringWidth(this.progressStr, FS);
        LeaderFontManager.drawString(this.progressStr, -textW / 2.0F, -12.0F, textColor, true, FS);

        GL11.glDisable(GL11.GL_BLEND);
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private void resetVariables() {
        this.block = null;
        this.progress = 0.0F;
        this.progressStr = "";
    }

    private static double round(double value, int places) {
        double pow = Math.pow(10.0, places);
        return Math.round(value * pow) / pow;
    }

    /** Raven BlockUtils.getBlock(BlockPos) — inlined. */
    private static Block getBlock(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    /** Raven BlockUtils.getBlockHardness(b, stack, false, false) — inlined. */
    private static float getBlockHardness(Block b, ItemStack stack) {
        if (b == null) {
            return 1.0F;
        }
        float hardness = b.getBlockHardness(mc.theWorld, mc.thePlayer.getPosition());
        if (hardness < 0.0F) {
            return 0.0F;
        }
        float digSpeed = 1.0F;
        if (stack != null) {
            digSpeed = stack.getStrVsBlock(b);
            if (digSpeed > 1.0F) {
                int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
                if (efficiency > 0) {
                    digSpeed += efficiency * efficiency + 1;
                }
            }
        }
        return digSpeed / hardness / 100.0F;
    }
}
