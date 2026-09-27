package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;

import java.lang.reflect.Field;

public final class AccessorEntityLivingBase {
    private static final String OWNER = "net.minecraft.entity.EntityLivingBase";
    private static final Field F_SPRINTINGSPEEDBOOSTMODIFIER =
            MappingBridge.field(OWNER, "sprintingSpeedBoostModifier", AttributeModifier.class);
    private static final Field F_JUMPTICKS =
            MappingBridge.field(OWNER, "jumpTicks", int.class);
    private static final Field F_ISJUMPING =
            MappingBridge.field(OWNER, "isJumping", boolean.class);
    private static final Field F_ACTIVEPOTIONSMAP =
            MappingBridge.field(OWNER, "activePotionsMap", java.util.Map.class);
    private AccessorEntityLivingBase() {
    }

    public static java.util.Map getActivePotionsMap(EntityLivingBase owner) {
        try {
            return (java.util.Map) F_ACTIVEPOTIONSMAP.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "activePotionsMap", t);
            return null;
        }
    }
    public static AttributeModifier getSprintingSpeedBoostModifier(EntityLivingBase owner) {
        try {
            return (AttributeModifier) F_SPRINTINGSPEEDBOOSTMODIFIER.get(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "sprintingSpeedBoostModifier", t);
            return null;
        }
    }
    public static boolean isJumping(EntityLivingBase owner) {
        try {
            return F_ISJUMPING.getBoolean(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "isJumping", t);
            return false;
        }
    }
    public static int getJumpTicks(EntityLivingBase owner) {
        try {
            return F_JUMPTICKS.getInt(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "jumpTicks", t);
            return 0;
        }
    }
    public static void setJumpTicks(EntityLivingBase owner, int value) {
        try {
            F_JUMPTICKS.setInt(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "jumpTicks", t);
        }
    }
}
