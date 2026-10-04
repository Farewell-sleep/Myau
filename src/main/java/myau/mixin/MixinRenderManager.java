package myau.mixin;

import myau.management.RotationState;
import myau.module.modules.Freelook;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {RenderManager.class}, priority = 9999)
public abstract class MixinRenderManager {
    @Shadow
    private float playerViewX;
    @Shadow
    private float playerViewY;

    @Unique
    private float _prevRenderYawOffset;
    @Unique
    private float _renderYawOffset;
    @Unique
    private float _prevRotationYawHead;
    @Unique
    private float _rotationYawHead;
    @Unique
    private float _prevRotationPitch;
    @Unique
    private float _rotationPitch;

    @Inject(
            method = {"renderEntityStatic"},
            at = {@At("HEAD")}
    )
    private void renderEntityStatic(Entity entity, float float2, boolean boolean3, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof EntityPlayerSP && RotationState.isRotated(1)) {
            EntityPlayerSP entityPlayerSP = (EntityPlayerSP) entity;
            float yawOffset = RotationState.getRenderYawOffset();
            float yawHead = RotationState.getRotationYawHead();
            float pitch = RotationState.getRotationPitch();
            if (!Float.isFinite(yawOffset) || !Float.isFinite(yawHead) || !Float.isFinite(pitch)) {
                return;
            }
            this._prevRenderYawOffset = entityPlayerSP.prevRenderYawOffset;
            this._renderYawOffset = entityPlayerSP.renderYawOffset;
            this._prevRotationYawHead = entityPlayerSP.prevRotationYawHead;
            this._rotationYawHead = entityPlayerSP.rotationYawHead;
            this._prevRotationPitch = entityPlayerSP.prevRotationPitch;
            this._rotationPitch = entityPlayerSP.rotationPitch;
            entityPlayerSP.prevRenderYawOffset = RotationState.getPrevRenderYawOffset();
            entityPlayerSP.renderYawOffset = yawOffset;
            entityPlayerSP.prevRotationYawHead = RotationState.getPrevRotationYawHead();
            entityPlayerSP.rotationYawHead = yawHead;
            entityPlayerSP.prevRotationPitch = RotationState.getPrevRotationPitch();
            entityPlayerSP.rotationPitch = pitch;
        }
    }

    @Inject(
            method = {"renderEntityStatic"},
            at = {@At("RETURN")}
    )
    private void renderEntityStaticPost(Entity entity, float float2, boolean boolean3, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (entity instanceof EntityPlayerSP && RotationState.isRotated(1)) {
            EntityPlayerSP entityPlayerSP = (EntityPlayerSP) entity;
            entityPlayerSP.prevRenderYawOffset = this._prevRenderYawOffset;
            entityPlayerSP.renderYawOffset = this._renderYawOffset;
            entityPlayerSP.prevRotationYawHead = this._prevRotationYawHead;
            entityPlayerSP.rotationYawHead = this._rotationYawHead;
            entityPlayerSP.prevRotationPitch = this._prevRotationPitch;
            entityPlayerSP.rotationPitch = this._rotationPitch;
        }
    }

    // === FREELOOK (skidded from Raven B4 MixinRenderManager) ===

    @Redirect(
            method = {"cacheActiveRenderInfo"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewX:F", opcode = Opcodes.PUTFIELD)
    )
    private void freelookRedirectPlayerViewX(RenderManager rm, float value) {
        this.playerViewX = (Freelook.instance != null && Freelook.instance.isEnabled() && Freelook.perspectiveToggled) ? Freelook.cameraPitch : value;
    }

    @Redirect(
            method = {"cacheActiveRenderInfo"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/RenderManager;playerViewY:F", opcode = Opcodes.PUTFIELD)
    )
    private void freelookRedirectPlayerViewY(RenderManager rm, float value) {
        this.playerViewY = (Freelook.instance != null && Freelook.instance.isEnabled() && Freelook.perspectiveToggled) ? Freelook.cameraYaw : value;
    }
}
