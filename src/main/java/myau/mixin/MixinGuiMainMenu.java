package myau.mixin;

import myau.ui.MainMenuScreen;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Inject(method = "initGui", at = @At("HEAD"), cancellable = true)
    private void myauMainMenu(CallbackInfo ci) {
        this.mc.displayGuiScreen(new MainMenuScreen());
        ci.cancel();
    }
}
