package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;

public final class AccessorEntityPlayer {
    private static final String OWNER = "net.minecraft.entity.player.EntityPlayer";
    private static final Field F_ITEMINUSE =
            MappingBridge.field(OWNER, "itemInUse", ItemStack.class);
    private static final Field F_ITEMINUSECOUNT =
            MappingBridge.field(OWNER, "itemInUseCount", int.class);
    private AccessorEntityPlayer() {
    }
    public static ItemStack getItemInUse(EntityPlayer owner) {
        try {
            return (ItemStack) F_ITEMINUSE.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "itemInUse", t);
            return null;
        }
    }
    public static void setItemInUse(EntityPlayer owner, ItemStack value) {
        try {
            F_ITEMINUSE.set(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "itemInUse", t);
        }
    }
    public static int getItemInUseCount(EntityPlayer owner) {
        try {
            return F_ITEMINUSECOUNT.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "itemInUseCount", t);
            return 0;
        }
    }
    public static void setItemInUseCount(EntityPlayer owner, int value) {
        try {
            F_ITEMINUSECOUNT.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "itemInUseCount", t);
        }
    }
}
