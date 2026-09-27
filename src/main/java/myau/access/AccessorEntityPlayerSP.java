package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.client.entity.EntityPlayerSP;

import java.lang.reflect.Field;

public final class AccessorEntityPlayerSP {
    private static final String OWNER = "net.minecraft.client.entity.EntityPlayerSP";
    private static final Field F_LASTREPORTEDYAW =
            MappingBridge.field(OWNER, "lastReportedYaw", float.class);
    private static final Field F_LASTREPORTEDPITCH =
            MappingBridge.field(OWNER, "lastReportedPitch", float.class);
    private AccessorEntityPlayerSP() {
    }
    public static float getLastReportedYaw(EntityPlayerSP owner) {
        try {
            return F_LASTREPORTEDYAW.getFloat(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "lastReportedYaw", t);
            return 0.0F;
        }
    }
    public static void setLastReportedYaw(EntityPlayerSP owner, float value) {
        try {
            F_LASTREPORTEDYAW.setFloat(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "lastReportedYaw", t);
        }
    }
    public static float getLastReportedPitch(EntityPlayerSP owner) {
        try {
            return F_LASTREPORTEDPITCH.getFloat(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "lastReportedPitch", t);
            return 0.0F;
        }
    }
    public static void setLastReportedPitch(EntityPlayerSP owner, float value) {
        try {
            F_LASTREPORTEDPITCH.setFloat(owner, value);
        } catch (Throwable t) {
            Access.report(OWNER, "lastReportedPitch", t);
        }
    }
}
