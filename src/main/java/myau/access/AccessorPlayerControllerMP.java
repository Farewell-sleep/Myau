package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.multiplayer.PlayerControllerMP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AccessorPlayerControllerMP {
    private static final String OWNER = "net.minecraft.client.multiplayer.PlayerControllerMP";
    private static final Field F_CURBLOCKDAMAGEMP =
            MappingBridge.field(OWNER, "curBlockDamageMP", float.class);
    private static final Field F_BLOCKHITDELAY =
            MappingBridge.field(OWNER, "blockHitDelay", int.class);
    private static final Field F_ISHITTINGBLOCK =
            MappingBridge.field(OWNER, "isHittingBlock", boolean.class);
    private static final Field F_CURRENTPLAYERITEM =
            MappingBridge.field(OWNER, "currentPlayerItem", int.class);
    private static final Method M_SYNCCURRENTPLAYITEM =
            MappingBridge.method(OWNER, "syncCurrentPlayItem");

    private AccessorPlayerControllerMP() {
    }
    public static float getCurBlockDamageMP(PlayerControllerMP owner) {
        try {
            return F_CURBLOCKDAMAGEMP.getFloat(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "curBlockDamageMP", t);
            return 0.0F;
        }
    }
    public static void setCurBlockDamageMP(PlayerControllerMP owner, float value) {
        try {
            F_CURBLOCKDAMAGEMP.setFloat(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "curBlockDamageMP", t);
        }
    }
    public static int getBlockHitDelay(PlayerControllerMP owner) {
        try {
            return F_BLOCKHITDELAY.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "blockHitDelay", t);
            return 0;
        }
    }
    public static void setBlockHitDelay(PlayerControllerMP owner, int value) {
        try {
            F_BLOCKHITDELAY.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "blockHitDelay", t);
        }
    }
    public static boolean getIsHittingBlock(PlayerControllerMP owner) {
        try {
            return F_ISHITTINGBLOCK.getBoolean(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "isHittingBlock", t);
            return false;
        }
    }
    public static int getCurrentPlayerItem(PlayerControllerMP owner) {
        try {
            return F_CURRENTPLAYERITEM.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "currentPlayerItem", t);
            return 0;
        }
    }
    public static void setCurrentPlayerItem(PlayerControllerMP owner, int value) {
        try {
            F_CURRENTPLAYERITEM.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "currentPlayerItem", t);
        }
    }
    public static void callSyncCurrentPlayItem(PlayerControllerMP owner) {
        try {
            M_SYNCCURRENTPLAYITEM.invoke(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "syncCurrentPlayItem", t);
        }
    }
}
