package myau.mixin;

import myau.OpenMyau;
import myau.event.EventManager;
import myau.event.types.EventType;
import myau.events.RenderLivingEvent;
import myau.module.modules.Chams;
import myau.module.modules.ESP;
import myau.module.modules.NameTags;
import myau.module.modules.SeeInvisibles;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Color;

@SideOnly(Side.CLIENT)
@Mixin(
        value = {RendererLivingEntity.class},
        priority = 9991
)
public abstract class MixinRendererLivingEntity<T extends EntityLivingBase> extends Render<T> {
    protected MixinRendererLivingEntity(RenderManager renderManager) {
        super(renderManager);
    }

    @Inject(
            method = {"doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"},
            at = {@At("HEAD")}
    )
    private void doRender(T entityLivingBase, double double2, double double3, double double4, float float5, float float6, CallbackInfo callbackInfo) {
        EventManager.call(new RenderLivingEvent(EventType.PRE, entityLivingBase));
    }

    @Inject(
            method = {"doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"},
            at = {@At("RETURN")}
    )
    private void postRender(T entityLivingBase, double double2, double double3, double double4, float float5, float float6, CallbackInfo callbackInfo) {
        EventManager.call(new RenderLivingEvent(EventType.POST, entityLivingBase));
    }

    @Inject(
            method = {"canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void canRenderName(T entityLivingBase, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (OpenMyau.moduleManager != null) {
            NameTags nameTags = (NameTags) OpenMyau.moduleManager.modules.get(NameTags.class);
            if (nameTags.isEnabled() && nameTags.shouldRenderTags(entityLivingBase)) {
                callbackInfoReturnable.setReturnValue(false);
            } else {
                ESP esp = (ESP) OpenMyau.moduleManager.modules.get(ESP.class);
                if (esp.isEnabled() && !esp.isOutlineEnabled()) {
                    callbackInfoReturnable.setReturnValue(false);
                }
            }
        }
    }

    // === SEE INVISIBLES (skidded from Onxy) ===

    @Redirect(
            method = {"doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;isInvisible()Z"
            )
    )
    private boolean seeInvisiblesRenderBody(T entityLivingBase) {
        if (OpenMyau.moduleManager != null) {
            SeeInvisibles seeInvisibles = (SeeInvisibles) OpenMyau.moduleManager.modules.get(SeeInvisibles.class);
            if (seeInvisibles != null && seeInvisibles.isEnabled() && seeInvisibles.shouldShow(entityLivingBase)) {
                return false;
            }
        }
        return entityLivingBase.isInvisible();
    }

    // === CHAMS ONYX tint (skid Onxy ChamsModule) ===

    @Inject(
            method = {"renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V"},
            at = {@At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
                    shift = At.Shift.BEFORE
            )}
    )
    private void chamsOnyxPre(T entityLivingBase, float float2, float float3, float float4, float float5, float float6, float float7, CallbackInfo callbackInfo) {
        if (OpenMyau.moduleManager == null) {
            return;
        }
        Chams chams = (Chams) OpenMyau.moduleManager.modules.get(Chams.class);
        if (chams != null && chams.isEnabled() && chams.mode.getValue() == 1 && chams.shouldRenderChams(entityLivingBase)) {
            Color c = new Color(chams.onyxColor.getValue());
            float strength = chams.onyxStrength.getValue() / 100.0F;
            float alpha = chams.onyxOpacity.getValue() / 100.0F;
            float r = 1.0F + (c.getRed() / 255.0F - 1.0F) * strength;
            float g = 1.0F + (c.getGreen() / 255.0F - 1.0F) * strength;
            float b = 1.0F + (c.getBlue() / 255.0F - 1.0F) * strength;
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(r, g, b, alpha);
        }
    }

    @Inject(
            method = {"renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V"},
            at = {@At("RETURN")}
    )
    private void chamsOnyxPost(T entityLivingBase, float float2, float float3, float float4, float float5, float float6, float float7, CallbackInfo callbackInfo) {
        if (OpenMyau.moduleManager == null) {
            return;
        }
        Chams chams = (Chams) OpenMyau.moduleManager.modules.get(Chams.class);
        if (chams != null && chams.isEnabled() && chams.mode.getValue() == 1 && chams.shouldRenderChams(entityLivingBase)) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
        }
    }
}
