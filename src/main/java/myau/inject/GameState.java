package myau.inject;

import java.lang.reflect.Field;

final class GameState {
    private static Field instance;
    private static Field world;
    private static Field player;
    private static volatile boolean resolved;
    private static boolean usable;
    private GameState() {
    }
    static boolean inGame() {
        if (!resolved) {
            resolve();
        }
        if (!usable) {
            return false;
        }
        try {
            Object mc = instance.get(null);
            return mc != null && world.get(mc) != null && player.get(mc) != null;
        } catch (Throwable t) {
            return false;
        }
    }
    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        String owner = "net.minecraft.client.Minecraft";
        instance = MappingBridge.field(owner, "theMinecraft", null);
        world = MappingBridge.field(owner, "theWorld", null);
        player = MappingBridge.field(owner, "thePlayer", null);
        usable = instance != null && world != null && player != null;
        resolved = true;
        if (!usable) {
            System.out.println("[myau-inject] cannot resolve Minecraft's world/player"
                    + " fields -- tick events will not fire");
        }
    }
}
