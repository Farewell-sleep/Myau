package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.network.play.client.C03PacketPlayer;

import java.lang.reflect.Field;

public final class AccessorC03PacketPlayer {
    private static final String OWNER = "net.minecraft.network.play.client.C03PacketPlayer";
    private static final Field F_ONGROUND =
            MappingBridge.field(OWNER, "onGround", boolean.class);
    private AccessorC03PacketPlayer() {
    }
    public static void setOnGround(C03PacketPlayer owner, boolean value) {
        try {
            F_ONGROUND.setBoolean(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "onGround", t);
        }
    }
}
