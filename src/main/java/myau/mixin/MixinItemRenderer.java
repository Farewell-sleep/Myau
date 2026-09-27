package myau.mixin;

import myau.util.ReflectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();

    /**
     * Port of InjectMyau RenderCallbacks.itemInUseCountForRender: while the
     * Autoblock module wants a block pose shown (ReflectionUtils flag set),
     * spoof itemInUseCount as 1 for the held BLOCK-action item so the vanilla
     * renderItemInFirstPerson draws the blocking pose.
     */
    @Inject(
            method = {"renderItemInFirstPerson(F)V"},
            at = {@At("HEAD")}
    )
    private void autoblockRenderSpoof(CallbackInfo callbackInfo) {
        if (this.mc.thePlayer instanceof IAccessorEntityPlayer) {
            IAccessorEntityPlayer accessor = (IAccessorEntityPlayer) this.mc.thePlayer;
            ItemStack held = this.mc.thePlayer.getHeldItem();
            boolean wantsPose = ReflectionUtils.isItemInUse()
                    && held != null && held.getItemUseAction() == EnumAction.BLOCK;
            if (wantsPose) {
                if (accessor.getItemInUse() == null) {
                    accessor.setItemInUseCount(1);
                }
            } else if (accessor.getItemInUse() == null && accessor.getItemInUseCount() == 1) {
                accessor.setItemInUseCount(0);
            }
        }
    }
}