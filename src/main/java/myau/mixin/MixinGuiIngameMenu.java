package myau.mixin;

import myau.ui.DarkheartPauseMenu;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameMenu.class)
public abstract class MixinGuiIngameMenu extends GuiScreen {

    @Inject(method = "initGui", at = @At("HEAD"), cancellable = true)
    private void darkheartPauseMenu(CallbackInfo ci) {
        this.mc.displayGuiScreen(new DarkheartPauseMenu());
        ci.cancel();
    }
}
