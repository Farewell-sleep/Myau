package myau.util;

import myau.mixin.IAccessorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class RotationUtil {
    public static float[] serverRotations = new float[]{0.0F, 0.0F};

    public static float clampPitch(float n) {
        return MathHelper.clamp_float(n, -90.0F, 90.0F);
    }
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static float wrapAngleDiff(float angle, float target) {
        return target + MathHelper.wrapAngleTo180_float(angle - target);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            angle = maxAngle;
        } else if (angle < -maxAngle) {
            angle = -maxAngle;
        }
        return angle;
    }

    public static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtil.nextFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % (double) 0.0096f);
    }

    public static float[] getRotationsToBox(AxisAlignedBB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.xCoord;
        double deltaY = eyePos.yCoord >= maxTargetY ? maxTargetY - eyePos.yCoord : (eyePos.yCoord <= minTargetY ? minTargetY - eyePos.yCoord : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.zCoord;
        return RotationUtil.getRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    public static float[] getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return RotationUtil.getRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    /** Absolute rotations from the player's eye position to a world point. */
    public static float[] getRotations(double posX, double posY, double posZ) {
        return getRotations(posX, posY, posZ, mc.thePlayer.posX, mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    /** Absolute rotations from an arbitrary start point to a world point. */
    public static float[] getRotations(double rotX, double rotY, double rotZ, double startX, double startY, double startZ) {
        double x = rotX - startX;
        double y = rotY - startY;
        double z = rotZ - startZ;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    public static float[] getRotations(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = MathHelper.wrapAngleTo180_float((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : RotationUtil.smoothAngle(RotationUtil.clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new float[]{RotationUtil.quantizeAngle(currentYaw + yawDelta), RotationUtil.quantizeAngle(currentPitch + pitchDelta)};
    }

    public static Vec3 clampVecToBox(Vec3 vector, AxisAlignedBB boundingBox) {
        double[] coords = new double[]{vector.xCoord, vector.yCoord, vector.zCoord};
        double[] minCoords = new double[]{boundingBox.minX, boundingBox.minY, boundingBox.minZ};
        double[] maxCoords = new double[]{boundingBox.maxX, boundingBox.maxY, boundingBox.maxZ};
        for (int i = 0; i < 3; ++i) {
            if (coords[i] > maxCoords[i]) {
                coords[i] = maxCoords[i];
                continue;
            }
            if (!(coords[i] < minCoords[i])) continue;
            coords[i] = minCoords[i];
        }
        return new Vec3(coords[0], coords[1], coords[2]);
    }

    public static double distanceToEntity(Entity entity) {
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        return RotationUtil.distanceToBox(boundingBox);
    }

    public static double distanceToBox(Entity entity, Vec3 point) {
        float borderSize = entity.getCollisionBorderSize();
        return RotationUtil.clampVecToBox(entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize), point);
    }

    public static double distanceToBox(AxisAlignedBB boundingBox) {
        return RotationUtil.clampVecToBox(boundingBox, RotationUtil.mc.thePlayer.getPositionEyes(1.0f));
    }

    public static double clampVecToBox(AxisAlignedBB boundingBox, Vec3 point) {
        if (boundingBox.isVecInside(point)) {
            return 0.0;
        }
        Vec3 clampedPoint = RotationUtil.clampVecToBox(point, boundingBox);
        double deltaX = clampedPoint.xCoord - point.xCoord;
        double deltaY = clampedPoint.yCoord - point.yCoord;
        double deltaZ = clampedPoint.zCoord - point.zCoord;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static float angleToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        return Math.abs(MathHelper.wrapAngleTo180_float((float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw)) * 2.0f;
    }

    public static float pitchToEntity(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        AxisAlignedBB boundingBox = entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
        if (boundingBox.isVecInside(eyePos)) {
            return 0.0f;
        }
        double deltaX = entity.posX - eyePos.xCoord;
        double deltaY = entity.posY - eyePos.yCoord;
        double deltaZ = entity.posZ - eyePos.zCoord;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float pitch = (float) (-Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return Math.abs(MathHelper.wrapAngleTo180_float(pitch - RotationUtil.mc.thePlayer.rotationPitch));
    }

    public static float getYawBetween(double x1, double z1, double x2, double z2) {
        return MathHelper.wrapAngleTo180_float((float) (Math.atan2(z2 - z1, x2 - x1) * 180.0 / Math.PI) - 90.0f - RotationUtil.mc.thePlayer.rotationYaw);
    }

    public static MovingObjectPosition rayTrace(float yaw, float pitch, double distance, float partialTicks) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(Entity entity) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        float borderSize = entity.getCollisionBorderSize();
        Vec3 targetPos = RotationUtil.clampVecToBox(eyePos, entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize));
        return RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPos);
    }

    public static MovingObjectPosition rayTrace(AxisAlignedBB boundingBox, float yaw, float pitch, double distance) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        Vec3 lookVec = ((IAccessorEntity) RotationUtil.mc.thePlayer).callGetVectorForRotation(pitch, yaw);
        Vec3 targetPos = eyePos.addVector(lookVec.xCoord * distance, lookVec.yCoord * distance, lookVec.zCoord * distance);
        return boundingBox.calculateIntercept(eyePos, targetPos);
    }
    public static boolean hasVisiblePoint(AxisAlignedBB boundingBox) {
        Vec3 eyePos = RotationUtil.mc.thePlayer.getPositionEyes(1.0f);
        double centerX = (boundingBox.minX + boundingBox.maxX) / 2.0;
        double centerZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0;
        double height = boundingBox.maxY - boundingBox.minY;
        double[] yRatios = new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9};

        for (double ratio : yRatios) {
            double targetY = boundingBox.minY + ratio * height;
            Vec3 targetPoint = new Vec3(centerX, targetY, centerZ);
            if (RotationUtil.mc.theWorld.rayTraceBlocks(eyePos, targetPoint) == null) {
                return true;
            }
        }
        return false;
    }

    public static float[] nearestRotation(final AxisAlignedBB box, float currentYaw, float currentPitch,
                                   float maxAngle, float smoothFactor) {
        if (mc.thePlayer == null) return null;

        Vec3 targetPoint = getNearestPointBB(box);
        if (targetPoint == null) return null;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        double diffX = targetPoint.xCoord - eyePos.xCoord;
        double diffY = targetPoint.yCoord - eyePos.yCoord;
        double diffZ = targetPoint.zCoord - eyePos.zCoord;

        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float yawDelta = MathHelper.wrapAngleTo180_float(
                (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = MathHelper.wrapAngleTo180_float(
                (float) (-Math.atan2(diffY, horizontalDist) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f :
                smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f :
                smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);

        return new float[]{
                quantizeAngle(currentYaw + yawDelta),
                quantizeAngle(currentPitch + pitchDelta)
        };
    }

    public static Vec3 getNearestPointBB(AxisAlignedBB box) {
        if (mc.thePlayer == null) return null;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        double x = MathHelper.clamp_double(eyePos.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyePos.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyePos.zCoord, box.minZ, box.maxZ);
        Vec3 nearestPoint = new Vec3(x, y, z);

        if (isVisible(nearestPoint)) {
            return nearestPoint;
        }

        AxisAlignedBB scanBox = box.expand(-0.002, -0.002, -0.002);

        double stepX = scanBox.maxX - scanBox.minX;
        double stepY = scanBox.maxY - scanBox.minY;
        double stepZ = scanBox.maxZ - scanBox.minZ;

        Vec3 bestPoint = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (double sX = scanBox.minX; sX <= scanBox.maxX; sX += stepX / 2.0) {
            for (double sY = scanBox.minY; sY <= scanBox.maxY; sY += stepY / 2.0) {
                for (double sZ = scanBox.minZ; sZ <= scanBox.maxZ; sZ += stepZ / 2.0) {
                    Vec3 currentPoint = new Vec3(sX, sY, sZ);

                    if (isVisible(currentPoint)) {
                        double distSq = eyePos.squareDistanceTo(currentPoint);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            bestPoint = currentPoint;
                        }
                    }
                }
            }
        }

        return (bestPoint != null) ? bestPoint : nearestPoint;
    }

    public static boolean isVisible(Vec3 targetVec) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;

        Vec3 eyePos = new Vec3(mc.thePlayer.posX,
                mc.thePlayer.posY + (double) mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);

        if (eyePos.squareDistanceTo(targetVec) > 4096.0) return false;

        Vec3 lookVec = mc.thePlayer.getLookVec();
        Vec3 toTarget = targetVec.subtract(eyePos).normalize();
        if (lookVec.dotProduct(toTarget) < 0) return false;
        MovingObjectPosition result = mc.theWorld.rayTraceBlocks(eyePos, targetVec, false, true, false);
        return result == null;
    }
}
