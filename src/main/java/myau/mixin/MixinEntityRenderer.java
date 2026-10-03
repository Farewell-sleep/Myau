package myau.mixin;

import myau.OpenMyau;
import myau.data.Box;
import myau.event.EventManager;
import myau.events.PickEvent;
import myau.events.RaytraceEvent;
import myau.events.Render3DEvent;
import myau.module.modules.*;
import myau.util.ReflectionUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;


@SideOnly(Side.CLIENT)
@Mixin(value = {EntityRenderer.class}, priority = 9999)
public abstract class MixinEntityRenderer {
    @Unique
    private Box<Integer> slot = null;
    @Unique
    private Box<ItemStack> using = null;
    @Unique
    private Box<Integer> useCount = null;
    @Shadow
    private Minecraft mc;
    @Shadow
    private float thirdPersonDistance;

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("HEAD")}
    )
    private void updateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.mc.thePlayer != null) {
            Scaffold scaffold = (Scaffold) OpenMyau.moduleManager.modules.get(Scaffold.class);
            if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
                int slot = scaffold.getSlot();
                if (slot >= 0) {
                    this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                    this.mc.thePlayer.inventory.currentItem = slot;
                }
            }
            KillAura killAura = (KillAura) OpenMyau.moduleManager.modules.get(KillAura.class);
            if (killAura.isEnabled() && killAura.isBlocking()) {
                this.using = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUse());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.mc.thePlayer.inventory.getCurrentItem());
                this.useCount = new Box<>(((IAccessorEntityPlayer) this.mc.thePlayer).getItemInUseCount());
                ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(69000);
            }
        }
    }

    @Inject(
            method = {"updateCameraAndRender"},
            at = {@At("RETURN")}
    )
    private void postUpdateCameraAndRender(float float1, long long2, CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
        if (this.using != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUse(this.using.value);
            this.using = null;
        }
        if (this.useCount != null) {
            ((IAccessorEntityPlayer) this.mc.thePlayer).setItemInUseCount(this.useCount.value);
            this.useCount = null;
        }
    }

    @Inject(
            method = {"updateRenderer"},
            at = {@At("HEAD")}
    )
    private void updateRenderer(CallbackInfo callbackInfo) {
        Scaffold scaffold = (Scaffold) OpenMyau.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled() && scaffold.itemSpoof.getValue()) {
            int slot = scaffold.getSlot();
            if (slot >= 0) {
                this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                this.mc.thePlayer.inventory.currentItem = slot;
            }
        }

        AutoBlockIn autoBlockIn = (AutoBlockIn) OpenMyau.moduleManager.modules.get(AutoBlockIn.class);
        if (autoBlockIn.isEnabled() && autoBlockIn.itemSpoof.getValue()) {
            int slot = autoBlockIn.getSlot();
            if (slot >= 0) {
                this.slot = new Box<>(this.mc.thePlayer.inventory.currentItem);
                this.mc.thePlayer.inventory.currentItem = slot;
            }
        }
    }

    @Inject(
            method = {"updateRenderer"},
            at = {@At("RETURN")}
    )
    private void postUpdateRenderer(CallbackInfo callbackInfo) {
        if (this.slot != null) {
            this.mc.thePlayer.inventory.currentItem = this.slot.value;
            this.slot = null;
        }
    }

    @Inject(
            method = {"renderWorldPass"},
            at = {@At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/EntityRenderer;renderHand:Z",
                    shift = At.Shift.BEFORE
            )}
    )
    private void renderWorldPass(int integer, float float2, long long3, CallbackInfo callbackInfo) {
        EventManager.call(new Render3DEvent(float2));
    }

    @ModifyConstant(
            method = {"hurtCameraEffect"},
            constant = {@Constant(
                    floatValue = 14.0F,
                    ordinal = 0
            )}
    )
    private float hurtCameraEffect(float float1) {
        if (OpenMyau.moduleManager == null) {
            return float1;
        } else {
            NoHurtCam noHurtCam = (NoHurtCam) OpenMyau.moduleManager.modules.get(NoHurtCam.class);
            return noHurtCam.isEnabled() ? float1 * (float) noHurtCam.multiplier.getValue().intValue() / 100.0F : float1;
        }
    }

    @ModifyConstant(
            method = {"getMouseOver"},
            constant = {@Constant(
                    doubleValue = 3.0,
                    ordinal = 1
            )}
    )
    private double getMouseOver(double range) {
        PickEvent event = new PickEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @ModifyVariable(
            method = {"getMouseOver"},
            at = @At("STORE"),
            name = {"d0"}
    )
    private double storeMouseOver(double range) {
        RaytraceEvent event = new RaytraceEvent(range);
        EventManager.call(event);
        return event.getRange();
    }

    @Inject(
            method = {"getMouseOver"},
            at = {@At(
                    value = "INVOKE",
                    target = "Ljava/util/List;size()I",
                    ordinal = 0
            )},
            locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void a(
            float float1,
            CallbackInfo callbackInfo,
            Entity entity,
            double double4,
            double double5,
            Vec3 vec36,
            boolean boolean7,
            int integer8,
            Vec3 vec39,
            Vec3 vec310,
            Vec3 vec311,
            float float12,
            List<Entity> list,
            double double14,
            int integer15
    ) {
        if (OpenMyau.moduleManager != null) {
            GhostHand event = (GhostHand) OpenMyau.moduleManager.modules.get(GhostHand.class);
            if (event.isEnabled()) {
                list.removeIf(event::shouldSkip);
            }
        }
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Vec3;distanceTo(Lnet/minecraft/util/Vec3;)D"
            )
    )
    private double v(Vec3 vec31, Vec3 vec32) {
        if (OpenMyau.moduleManager == null) {
            return vec31.distanceTo(vec32);
        } else {
            return OpenMyau.moduleManager.modules.get(ViewClip.class).isEnabled() ? (double) this.thirdPersonDistance : vec31.distanceTo(vec32);
        }
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/Block;getMaterial()Lnet/minecraft/block/material/Material;"
            )
    )
    private Material x(Block block) {
        if (OpenMyau.moduleManager == null) {
            return block.getMaterial();
        } else {
            return OpenMyau.moduleManager.modules.get(ViewClip.class).isEnabled() ? Material.air : block.getMaterial();
        }
    }

    @Redirect(
            method = {"updateFogColor"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean y(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && OpenMyau.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) OpenMyau.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupFog"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean q(EntityLivingBase entityLivingBase, Potion potion) {
        if (potion == Potion.blindness && OpenMyau.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) OpenMyau.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.blindness.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityLivingBase).getActivePotionsMap().containsKey(potion.id);
    }

    @Redirect(
            method = {"setupCameraTransform"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/entity/EntityPlayerSP;isPotionActive(Lnet/minecraft/potion/Potion;)Z"
            )
    )
    private boolean c(EntityPlayerSP entityPlayerSP, Potion potion) {
        if (potion == Potion.confusion && OpenMyau.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) OpenMyau.moduleManager.modules.get(AntiDebuff.class);
            if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }
        return ((IAccessorEntityLivingBase) entityPlayerSP).getActivePotionsMap().containsKey(potion.id);
    }

    // === FREELOOK (skidded from Raven B4 MixinEntityRenderer) ===

    private boolean freelookActive() {
        return Freelook.instance != null && Freelook.instance.isEnabled() && Freelook.perspectiveToggled;
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F")
    )
    private float freelookRotationYaw(Entity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (freelookActive()) {
            return Freelook.cameraYaw;
        }
        return entity.rotationYaw;
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F")
    )
    private float freelookPrevRotationYaw(Entity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (freelookActive()) {
            return Freelook.cameraYaw;
        }
        return entity.prevRotationYaw;
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F")
    )
    private float freelookRotationPitch(Entity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (freelookActive()) {
            return Freelook.cameraPitch;
        }
        return entity.rotationPitch;
    }

    @Redirect(
            method = {"orientCamera"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F")
    )
    private float freelookPrevRotationPitch(Entity entity) {
        if (entity == null) {
            return 0.0F;
        }
        if (freelookActive()) {
            return Freelook.cameraPitch;
        }
        return entity.prevRotationPitch;
    }

    @Redirect(
            method = {"updateCameraAndRender"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;inGameHasFocus:Z")
    )
    private boolean freelookOverrideMouse(Minecraft mc) {
        return Freelook.overrideMouse(mc);
    }

    // === FOG REMOVE (skidded from Onxy) ===

    @Inject(
            method = {"setupFog"},
            at = {@At("RETURN")}
    )
    private void fogRemoveSetupFog(int startCoords, float partialTicks, CallbackInfo callbackInfo) {
        if (OpenMyau.moduleManager != null) {
            FogRemove fogRemove = (FogRemove) OpenMyau.moduleManager.modules.get(FogRemove.class);
            if (fogRemove != null && fogRemove.isEnabled()) {
                GlStateManager.setFog(9729);
                GlStateManager.setFogDensity(0.0F);
                GlStateManager.setFogStart(8.5070587E37F);
                GlStateManager.setFogEnd(1.7014117E38F);
            }
        }
    }

    // === SNOWFOG: vanilla weather driven (SnowFog module) ===

    /** Force the vanilla renderRainSnow to take the snow branch everywhere. */
    @Redirect(
            method = {"renderRainSnow"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/biome/BiomeGenBase;getEnableSnow()Z"
            )
    )
    private boolean snowfogRenderRainSnow(BiomeGenBase biome) {
        if (OpenMyau.moduleManager != null) {
            SnowFog snowFog = (SnowFog) OpenMyau.moduleManager.modules.get(SnowFog.class);
            if (snowFog.isEnabled() && snowFog.snowDensity.getValue() > 0) {
                return true;
            }
        }
        return biome.getEnableSnow();
    }

    /**
     * Vanilla renderRainSnow decides snow vs rain by temperature:
     * (temperatureAtHeight < 0.15F) renders snow, otherwise rain.
     * Force a sub-zero temperature so the vanilla renderer draws snow
     * (same approach as Rise's Ambience weather module).
     */
    @Redirect(
            method = {"renderRainSnow"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/biome/BiomeGenBase;getFloatTemperature(Lnet/minecraft/util/BlockPos;)F"
            )
    )
    private float snowfogBiomeTemperature(BiomeGenBase biome, BlockPos pos) {
        if (OpenMyau.moduleManager != null) {
            SnowFog snowFog = (SnowFog) OpenMyau.moduleManager.modules.get(SnowFog.class);
            if (snowFog.isEnabled() && snowFog.snowDensity.getValue() > 0) {
                return 0.1F;
            }
        }
        return biome.getFloatTemperature(pos);
    }

}
