package myau.module.modules;

import myau.module.Module;
import myau.util.ChatUtil;
import net.minecraft.client.Minecraft;

/**
 * MODULE TOGGLE NOTIFY — client-mod core-layer module.
 * Always-on, hidden from every module list (not registered in ModuleManager,
 * so it never reaches the click gui / HUD), and cannot be toggled off.
 *
 * When any visible module is toggled it prints, in chat:
 *   [Myau] <modulename> : ON   (green, bold)
 *   [Myau] <modulename> : OFF  (red, bold)
 */
public final class ModuleToggleNotify extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static ModuleToggleNotify instance;
    private static boolean ready;

    public ModuleToggleNotify() {
        super("ModuleToggleNotify", true, true, true);
    }

    /** Invoked by OpenMyau.init() after all modules are registered. */
    public static void init() {
        instance = new ModuleToggleNotify();
        ready = true;
    }

    public static boolean isReady() {
        return ready;
    }

    public static void notifyToggle(Module module) {
        if (!ready || module == null || module.isHidden() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        boolean enabled = module.isEnabled();
        // chat message: prefix + name + arrow separator + bold colored state
        String state = enabled ? "&a&lON" : "&c&lOFF";
        ChatUtil.sendFormatted("&7[&bMyau&7] &f" + module.getName() + " &8» " + state);
    }
}
