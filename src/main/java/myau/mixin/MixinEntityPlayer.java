package myau.mixin;

import myau.OpenMyau;
import myau.module.modules.Capes;
import myau.module.modules.KeepSprint;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {EntityPlayer.class}, priority = 9999)
public abstract class MixinEntityPlayer extends MixinEntityLivingBase {
    @ModifyConstant(
            method = {"attackTargetEntityWithCurrentItem"},
            constant = {@Constant(
                    doubleValue = 0.6
            )}
    )
    private double attackTargetEntityWithCurrentItem(double speed) {
        if (OpenMyau.moduleManager == null) {
            return speed;
        } else {
            KeepSprint keepSprint = (KeepSprint) OpenMyau.moduleManager.modules.get(KeepSprint.class);
            return keepSprint.isEnabled() && keepSprint.shouldKeepSprint()
                    ? speed + (1.0 - speed) * (1.0 - keepSprint.slowdown.getValue().doubleValue() / 100.0)
                    : speed;
        }
    }

    @Redirect(
            method = {"attackTargetEntityWithCurrentItem"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"
            )
    )
    private void setSprinnt(EntityPlayer entityPlayer, boolean boolean2) {
        if (OpenMyau.moduleManager != null) {
            KeepSprint keepSprint = (KeepSprint) OpenMyau.moduleManager.modules.get(KeepSprint.class);
            if (!keepSprint.isEnabled() || !keepSprint.shouldKeepSprint()) {
                entityPlayer.setSprinting(boolean2);
            }
        }
    }

    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void darkheartCape(CallbackInfoReturnable<ResourceLocation> cir) {
        if (OpenMyau.moduleManager == null) {
            return;
        }
        Capes capes = (Capes) OpenMyau.moduleManager.modules.get(Capes.class);
        if (capes != null && capes.isEnabled()) {
            ResourceLocation cape = capes.getCape();
            if (cape != null) {
                boolean self = ((EntityPlayer) (Object) this) == Minecraft.getMinecraft().thePlayer;
                if (capes.allPlayer.getValue() || self) {
                    cir.setReturnValue(cape);
                }
            }
        }
    }
}
