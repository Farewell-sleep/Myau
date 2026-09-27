package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.network.play.client.C0DPacketCloseWindow;

import java.lang.reflect.Field;

public final class AccessorC0DPacketCloseWindow {
    private static final String OWNER = "net.minecraft.network.play.client.C0DPacketCloseWindow";
    private static final Field F_WINDOWID =
            MappingBridge.field(OWNER, "windowId", int.class);
    private AccessorC0DPacketCloseWindow() {
    }
    public static int getWindowId(C0DPacketCloseWindow owner) {
        try {
            return F_WINDOWID.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "windowId", t);
            return 0;
        }
    }
}
