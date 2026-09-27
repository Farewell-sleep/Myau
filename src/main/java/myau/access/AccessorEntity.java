package myau.access;

import myau.inject.MappingBridge;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AccessorEntity {
    private static final String OWNER = "net.minecraft.entity.Entity";
    private static final Field F_ISINWEB =
            MappingBridge.field(OWNER, "isInWeb", boolean.class);
    private static final Method M_GETVECTORFORROTATION =
            MappingBridge.method(OWNER, "getVectorForRotation", float.class, float.class);
    private AccessorEntity() {
    }
    public static boolean getIsInWeb(Entity owner) {
        try {
            return F_ISINWEB.getBoolean(owner);
        } catch (Throwable t) {
            Access.report(OWNER, "isInWeb", t);
            return false;
        }
    }
    public static Vec3 callGetVectorForRotation(Entity owner, float pitch, float yaw) {
        try {
            return (Vec3) M_GETVECTORFORROTATION.invoke(owner, pitch, yaw);
        } catch (Throwable t) {
            Access.report(OWNER, "getVectorForRotation", t);
            return null;
        }
    }
}
