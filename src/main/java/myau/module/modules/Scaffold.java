package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.management.RotationState;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.PercentProperty;
import myau.util.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{
            0.03125,
            0.09375,
            0.15625,
            0.21875,
            0.28125,
            0.34375,
            0.40625,
            0.46875,
            0.53125,
            0.59375,
            0.65625,
            0.71875,
            0.78125,
            0.84375,
            0.90625,
            0.96875
    };
    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private EnumFacing targetFacing = null;
    private float godBridgeDiag = Float.NaN;
    private int snapHoldCounter = 0;
    private float snapLastYaw = Float.NaN;
    private float snapLastPitch = 0.0F;
    private int snapDelayCounter = 0;
    private SnapTarget snapPendingTarget = null;
    public final ModeProperty rotationMode = new ModeProperty("rotations", 2, new String[]{"NONE", "VANILLA", "BACKWARDS", "PREDICTION", "STRICT", "GODBRIDGE", "SNAP"});
    public final BooleanProperty noUpdateWhenCanPlace = new BooleanProperty("no-update-when-can-place", false, () -> this.isRotationMode(5));
    public final BooleanProperty edgeLimit = new BooleanProperty("edge-limit", false, () -> this.isRotationMode(5));
    public final FloatProperty godBridgeTolerance = new FloatProperty("godbridge-tolerance", 5.0F, 0.0F, 10.0F, () -> this.isRotationMode(5));
    public final BooleanProperty airRescue = new BooleanProperty("air-rescue", true);
    public final FloatProperty rotateSpeed = new FloatProperty("rotate-speed", 180.0F, 1.0F, 180.0F);
    public final FloatProperty edgeThreshold = new FloatProperty("edge-threshold", 0.15F, 0.01F, 0.5F, () -> this.isRotationMode(6));
    public final FloatProperty snapForwardSpeed = new FloatProperty("snap-forward-speed", 180.0F, 1.0F, 180.0F, () -> this.isRotationMode(6));
    public final FloatProperty snapBackSpeed = new FloatProperty("snap-back-speed", 180.0F, 1.0F, 180.0F, () -> this.isRotationMode(6));
    public final BooleanProperty earlySnap = new BooleanProperty("early-snap", true, () -> this.isRotationMode(6));
    public final FloatProperty snapForwardPitch = new FloatProperty("snap-forward-pitch", 80.0F, 0.0F, 90.0F, () -> this.isRotationMode(6));
    public final IntProperty snapHoldTicks = new IntProperty("snap-hold-ticks", 1, 0, 5, () -> this.isRotationMode(6));
    public final BooleanProperty delayPlacement = new BooleanProperty("delay-placement", false, () -> this.isRotationMode(6));
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0, new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final ModeProperty keepY = new ModeProperty("keep-y", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty disableWhileJumpActive = new BooleanProperty("no-keep-y-on-jump-potion", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
            return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
        }
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) OpenMyau.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) OpenMyau.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    private BlockData getBlockData() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        BlockPos targetPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        } else {
            ArrayList<BlockPos> positions = new ArrayList<>();
            for (int x = -4; x <= 4; x++) {
                for (int y = -4; y <= 0; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos pos = targetPos.add(x, y, z);
                        if (!BlockUtil.isReplaceable(pos)
                                && !BlockUtil.isInteractable(pos)
                                && !(
                                mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                        > (double) mc.playerController.getBlockReachDistance()
                        )
                                && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                            for (EnumFacing facing : EnumFacing.VALUES) {
                                if (facing != EnumFacing.DOWN) {
                                    BlockPos blockPos = pos.offset(facing);
                                    if (BlockUtil.isReplaceable(blockPos)) {
                                        positions.add(pos);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (positions.isEmpty()) {
                return null;
            } else {
                positions.sort(
                        Comparator.comparingDouble(
                                o -> o.distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                        )
                );
                BlockPos blockPos = positions.get(0);
                EnumFacing facing = this.getBestFacing(blockPos, targetPos);
                return facing == null ? null : new BlockData(blockPos, facing);
            }
        }
    }

    private boolean place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
                return true;
            }
        }
        return false;
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isRotationMode(int index) {
        return this.rotationMode.getValue() == index;
    }

    private static final double FACE_DEPTH = 0.001;
    private static final double[] SNAP_OFFSETS = new double[]{0.5, 0.35, 0.65, 0.2, 0.8, 0.05, 0.95};

    private boolean isValidHit(MovingObjectPosition mop, BlockPos blockPos, EnumFacing facing) {
        return mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                && mop.getBlockPos().equals(blockPos) && mop.sideHit == facing;
    }

    private Vec3 facePoint(BlockPos blockPos, EnumFacing facing, double a, double b) {
        double n = facing.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1.0 - FACE_DEPTH : FACE_DEPTH;
        switch (facing.getAxis()) {
            case X: return new Vec3(blockPos.getX() + n, blockPos.getY() + a, blockPos.getZ() + b);
            case Y: return new Vec3(blockPos.getX() + a, blockPos.getY() + n, blockPos.getZ() + b);
            default: return new Vec3(blockPos.getX() + a, blockPos.getY() + b, blockPos.getZ() + n);
        }
    }

    private Vec3 applyRescueRotation(BlockData blockData, UpdateEvent event) {
        BlockPos pos = blockData.blockPos();
        EnumFacing facing = blockData.facing();
        float bestYaw = -180.0F, bestPitch = 0.0F;
        double bestDist = Double.MAX_VALUE;
        Vec3 bestHit = null;
        for (double a : placeOffsets) {
            for (double b : placeOffsets) {
                Vec3 target = this.facePoint(pos, facing, a, b);
                float[] rot = RotationUtil.getRotations(target.xCoord, target.yCoord, target.zCoord);
                rot[1] = Math.max(-90.0F, Math.min(90.0F, rot[1]));
                MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                if (!this.isValidHit(mop, pos, facing)) continue;
                float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - event.getYaw()));
                float pitchDiff = rot[1] - event.getPitch();
                double dist = yawDiff * yawDiff + pitchDiff * pitchDiff;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestYaw = rot[0];
                    bestPitch = rot[1];
                    bestHit = mop.hitVec;
                }
            }
        }
        if (bestHit == null) return null;
        this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
        this.pitch = bestPitch;
        this.canRotate = true;
        return bestHit;
    }

    private float quantizeDiagonal(float yaw) {
        return 45.0F + 90.0F * Math.round((yaw - 45.0F) / 90.0F);
    }

    private List<BlockData> getPlaceOptions(BlockData primary) {
        List<BlockData> options = new ArrayList<>();
        options.add(primary);
        BlockPos cell = primary.blockPos().offset(primary.facing());
        for (EnumFacing dir : EnumFacing.VALUES) {
            EnumFacing face = dir.getOpposite();
            if (face == EnumFacing.DOWN) continue;
            BlockPos support = cell.offset(dir);
            if (support.equals(primary.blockPos())) continue;
            if (BlockUtil.isReplaceable(support) || BlockUtil.isInteractable(support)) continue;
            options.add(new BlockData(support, face));
        }
        return options;
    }

    private float faceCenterPitch(BlockData data) {
        double x = data.blockPos().getX() + 0.5 + data.facing().getDirectionVec().getX() * 0.5;
        double y = data.blockPos().getY() + 0.5 + data.facing().getDirectionVec().getY() * 0.5;
        double z = data.blockPos().getZ() + 0.5 + data.facing().getDirectionVec().getZ() * 0.5;
        return Math.max(-89.0F, Math.min(89.0F, RotationUtil.getRotations(x, y, z)[1]));
    }

    private boolean isGodBridgeOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) return true;
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < 0.15 || xOff > 0.85 || zOff < 0.15 || zOff > 0.85) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < 0.15 ? -1 : (xOff > 0.85 ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < 0.15 ? -1 : (zOff > 0.85 ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) return true;
            }
        }
        return false;
    }

    private boolean isOnEdge() {
        if (!mc.thePlayer.onGround) return true;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), MathHelper.floor_double(mc.thePlayer.posY) - 1, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) return true;
        double threshold = edgeThreshold.getValue();
        double xOff = mc.thePlayer.posX - MathHelper.floor_double(mc.thePlayer.posX);
        double zOff = mc.thePlayer.posZ - MathHelper.floor_double(mc.thePlayer.posZ);
        if (xOff < threshold || xOff > 1.0 - threshold || zOff < threshold || zOff > 1.0 - threshold) {
            int checkX = MathHelper.floor_double(mc.thePlayer.posX) + (xOff < threshold ? -1 : (xOff > 1.0 - threshold ? 1 : 0));
            int checkZ = MathHelper.floor_double(mc.thePlayer.posZ) + (zOff < threshold ? -1 : (zOff > 1.0 - threshold ? 1 : 0));
            if (checkX != MathHelper.floor_double(mc.thePlayer.posX) || checkZ != MathHelper.floor_double(mc.thePlayer.posZ)) {
                BlockPos adjacentBelow = new BlockPos(checkX, MathHelper.floor_double(mc.thePlayer.posY) - 1, checkZ);
                if (BlockUtil.isReplaceable(adjacentBelow)) return true;
            }
        }
        return false;
    }

    private BlockPos edgeCell(double x, double z, int y) {
        int bx = MathHelper.floor_double(x);
        int bz = MathHelper.floor_double(z);
        double threshold = this.edgeThreshold.getValue();
        double xOff = x - bx;
        double zOff = z - bz;
        int dx = xOff < threshold ? -1 : (xOff > 1.0 - threshold ? 1 : 0);
        int dz = zOff < threshold ? -1 : (zOff > 1.0 - threshold ? 1 : 0);
        int[][] candidates = {{dx, 0}, {0, dz}, {dx, dz}};
        for (int[] c : candidates) {
            if (c[0] == 0 && c[1] == 0) continue;
            BlockPos pos = new BlockPos(bx + c[0], y, bz + c[1]);
            if (BlockUtil.isReplaceable(pos)) return pos;
        }
        return null;
    }

    private double[] predictPosition() {
        double[] move = MoveUtil.predictMovement();
        return new double[]{mc.thePlayer.posX + mc.thePlayer.motionX + move[0], mc.thePlayer.posZ + mc.thePlayer.motionZ + move[1]};
    }

    private SnapTarget solveFace(Vec3 eye, BlockPos support, EnumFacing face) {
        SnapTarget best = null;
        double bestCenter = Double.MAX_VALUE;
        for (double a : SNAP_OFFSETS) {
            for (double b : SNAP_OFFSETS) {
                double center = (a - 0.5) * (a - 0.5) + (b - 0.5) * (b - 0.5);
                if (center >= bestCenter) continue;
                Vec3 point = this.facePoint(support, face, a, b);
                float[] rot = RotationUtil.getRotations(point.xCoord, point.yCoord, point.zCoord, eye.xCoord, eye.yCoord, eye.zCoord);
                rot[1] = MathHelper.clamp_float(rot[1], -90.0F, 90.0F);
                MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                if (!this.isValidHit(mop, support, face)) continue;
                bestCenter = center;
                best = new SnapTarget(support, face, rot[0], rot[1], eye.squareDistanceTo(mop.hitVec));
            }
        }
        return best;
    }

    private SnapTarget solveCell(Vec3 eye, BlockPos cell) {
        if (!BlockUtil.isReplaceable(cell)) return null;
        SnapTarget best = null;
        for (EnumFacing dir : EnumFacing.VALUES) {
            if (dir == EnumFacing.UP) continue;
            BlockPos support = cell.offset(dir);
            if (BlockUtil.isReplaceable(support) || BlockUtil.isInteractable(support)) continue;
            SnapTarget target = this.solveFace(eye, support, dir.getOpposite());
            if (target != null && (best == null || target.distance < best.distance)) best = target;
        }
        return best;
    }

    private SnapTarget solveBridge(Vec3 eye, BlockPos cell) {
        SnapTarget best = null;
        double bestDist = Double.MAX_VALUE;
        for (EnumFacing dir : EnumFacing.HORIZONTALS) {
            BlockPos neighbor = cell.offset(dir);
            double dist = neighbor.distanceSqToCenter(mc.thePlayer.posX, neighbor.getY() + 0.5, mc.thePlayer.posZ);
            if (dist >= bestDist) continue;
            SnapTarget target = this.solveCell(eye, neighbor);
            if (target != null) { best = target; bestDist = dist; }
        }
        return best;
    }

    private SnapTarget findSnapTarget(Vec3 eye) {
        int playerY = MathHelper.floor_double(mc.thePlayer.posY);
        int y = (this.stage != 0 && !this.shouldKeepY ? Math.min(playerY, this.startY) : playerY) - 1;
        BlockPos below = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), y, MathHelper.floor_double(mc.thePlayer.posZ));
        if (BlockUtil.isReplaceable(below)) {
            SnapTarget target = this.solveCell(eye, below);
            return target != null ? target : this.solveBridge(eye, below);
        }
        if (!this.earlySnap.getValue() || !mc.thePlayer.onGround) return null;
        double[] next = this.predictPosition();
        BlockPos edge = this.edgeCell(next[0], next[1], y);
        return edge == null ? null : this.solveCell(eye, edge);
    }

    private float[] stepRotation(float fromYaw, float fromPitch, float toYaw, float toPitch, float yawSpeed, float pitchSpeed) {
        float yawDiff = MathHelper.wrapAngleTo180_float(toYaw - fromYaw);
        float pitchDiff = toPitch - fromPitch;
        float nextYaw = fromYaw + RotationUtil.clampAngle(yawDiff, yawSpeed);
        float nextPitch = fromPitch + RotationUtil.clampAngle(pitchDiff, pitchSpeed);
        return new float[]{
                RotationUtil.quantizeAngle(nextYaw),
                RotationUtil.quantizeAngle(MathHelper.clamp_float(nextPitch, -90.0F, 90.0F))
        };
    }

    private void applyRotation(UpdateEvent event, float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.canRotate = true;
        event.setRotation(yaw, pitch, 3);
        if (this.moveFix.getValue() == 1) event.setPervRotation(yaw, 3);
    }

    private void updateSnap(UpdateEvent event, boolean allowPlace) {
        if (this.snapHoldCounter > 0) this.snapHoldCounter--;
        if (this.snapDelayCounter > 0) this.snapDelayCounter--;
        if (!this.canPlace() || !ItemUtil.isHoldingBlock()) return;

        SnapTarget target = allowPlace ? this.findSnapTarget(mc.thePlayer.getPositionEyes(1.0F)) : null;
        float targetYaw;
        float targetPitch;
        float speed;

        if (this.delayPlacement.getValue() && target != null) {
            if (this.snapPendingTarget == null || !this.snapPendingTarget.blockPos().equals(target.blockPos())) {
                this.snapPendingTarget = target;
                this.snapDelayCounter = 1;
            }
            targetYaw = RotationUtil.wrapAngleDiff(target.yaw, event.getYaw());
            targetPitch = target.pitch;
            speed = this.snapBackSpeed.getValue();
            this.snapHoldCounter = this.snapHoldTicks.getValue();
            this.snapLastYaw = target.yaw;
            this.snapLastPitch = target.pitch;
        } else if (target != null) {
            targetYaw = RotationUtil.wrapAngleDiff(target.yaw, event.getYaw());
            targetPitch = target.pitch;
            speed = this.snapBackSpeed.getValue();
            this.snapHoldCounter = this.snapHoldTicks.getValue();
            this.snapLastYaw = target.yaw;
            this.snapLastPitch = target.pitch;
        } else if (this.snapHoldCounter > 0 && !Float.isNaN(this.snapLastYaw)) {
            targetYaw = RotationUtil.wrapAngleDiff(this.snapLastYaw, event.getYaw());
            targetPitch = this.snapLastPitch;
            speed = this.snapBackSpeed.getValue();
        } else {
            targetYaw = RotationUtil.wrapAngleDiff(this.getCurrentYaw(), event.getYaw());
            targetPitch = this.snapForwardPitch.getValue();
            speed = this.snapForwardSpeed.getValue();
            this.snapLastYaw = Float.NaN;
            this.snapPendingTarget = null;
        }

        float[] next = this.stepRotation(event.getYaw(), event.getPitch(), targetYaw, targetPitch, speed, speed);
        this.applyRotation(event, next[0], next[1]);

        SnapTarget placeTarget = this.delayPlacement.getValue() && this.snapDelayCounter == 0 ? this.snapPendingTarget : target;
        if (placeTarget == null || this.rotationTick > 0) return;
        MovingObjectPosition mop = RotationUtil.rayTrace(next[0], next[1], mc.playerController.getBlockReachDistance(), 1.0F);
        if (!this.isValidHit(mop, placeTarget.blockPos(), placeTarget.facing())) return;
        if (this.place(placeTarget.blockPos(), placeTarget.facing(), mop.hitVec)) {
            this.snapPendingTarget = null;
        }
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3;
            boolean tower = this.tower.getValue() == 3;
            return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
        } else {
            return false;
        }
    }

    public Scaffold() {
        super("Scaffold", false);
    }

    public int getSlot() {
        return this.lastSlot;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0
                        && this.keepY.getValue() != 0
                        && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && (!this.disableWhileJumpActive.getValue() || !mc.thePlayer.isPotionActive(Potion.jump))
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                if (this.isRotationMode(6)) {
                    this.updateSnap(event, true);
                    return;
                }
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1:
                            this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                        case 5:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(RotationUtil.wrapAngleDiff(this.quantizeDiagonal(currentYaw + 180.0F), event.getYaw()));
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                if (blockData != null) {
                    if (this.rotationMode.getValue() == 4) {
                        double centerX = blockData.blockPos().getX() + 0.5 + blockData.facing().getDirectionVec().getX() * 0.5;
                        double centerY = blockData.blockPos().getY() + 0.5 + blockData.facing().getDirectionVec().getY() * 0.5;
                        double centerZ = blockData.blockPos().getZ() + 0.5 + blockData.facing().getDirectionVec().getZ() * 0.5;
                        float[] strictRot = RotationUtil.getRotations(centerX, centerY, centerZ);
                        MovingObjectPosition strictMop = RotationUtil.rayTrace(strictRot[0], strictRot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                        if (strictMop != null && strictMop.typeOfHit == MovingObjectType.BLOCK
                                && strictMop.getBlockPos().equals(blockData.blockPos()) && strictMop.sideHit == blockData.facing()) {
                            this.yaw = RotationUtil.wrapAngleDiff(strictRot[0], event.getYaw());
                            this.pitch = strictRot[1];
                            this.canRotate = true;
                            hitVec = strictMop.hitVec;
                        }
                    } else if (this.rotationMode.getValue() == 5) {
                        float moveBack = this.getCurrentYaw() + 180.0F;
                        if (Float.isNaN(this.godBridgeDiag)
                                || Math.abs(MathHelper.wrapAngleTo180_float(moveBack - this.godBridgeDiag)) > 60.0F) {
                            this.godBridgeDiag = this.quantizeDiagonal(moveBack);
                        }
                        float diagYaw = this.godBridgeDiag;
                        float tolerance = this.godBridgeTolerance.getValue();
                        List<BlockData> options = this.getPlaceOptions(blockData);

                        float lastOff = MathHelper.wrapAngleTo180_float(this.yaw - diagYaw);
                        if (Math.abs(lastOff) > tolerance) {
                            lastOff = (float) ((Math.random() * 2.0D - 1.0D) * tolerance * 0.8D);
                        }
                        float realOff = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - diagYaw);
                        ArrayList<Float> yawCandidates = new ArrayList<>();
                        if (Math.abs(realOff) <= tolerance) yawCandidates.add(diagYaw + realOff);
                        yawCandidates.add(diagYaw + lastOff);
                        for (float step = 1.0F; step <= tolerance * 2.0F; step += 1.0F) {
                            float up = lastOff + step;
                            float down = lastOff - step;
                            if (Math.abs(up) <= tolerance) yawCandidates.add(diagYaw + up);
                            if (Math.abs(down) <= tolerance) yawCandidates.add(diagYaw + down);
                        }

                        float bestPitch = Float.NaN;
                        float bestYaw = diagYaw;
                        Vec3 bestHitVec = null;
                        BlockData bestOption = blockData;
                        double reach = mc.playerController.getBlockReachDistance();
                        for (float candidateYaw : yawCandidates) {
                            double bestScore = Double.MAX_VALUE;
                            for (BlockData option : options) {
                                float centerPitch = this.faceCenterPitch(option);
                                float penalty = option == blockData ? 0.0F : 2.0F;
                                for (float p = centerPitch + 30.0F; p >= centerPitch - 30.0F; p -= 0.5F) {
                                    if (p > 89.0F || p < -89.0F) continue;
                                    MovingObjectPosition mop = RotationUtil.rayTrace(candidateYaw, p, reach, 1.0F);
                                    if (this.isValidHit(mop, option.blockPos(), option.facing())) {
                                        double score = Math.abs(p - centerPitch) + penalty;
                                        if (score < bestScore) {
                                            bestScore = score;
                                            bestPitch = p;
                                            bestHitVec = mop.hitVec;
                                            bestYaw = candidateYaw;
                                            bestOption = option;
                                        }
                                    }
                                }
                            }
                            if (bestHitVec != null) break;
                        }
                        if (bestHitVec != null) {
                            blockData = bestOption;
                            hitVec = bestHitVec;
                            this.canRotate = true;
                            boolean updateRotation = true;
                            float keepYaw = event.getYaw();
                            float keepPitch = event.getPitch();
                            MovingObjectPosition keepMop = RotationUtil.rayTrace(keepYaw, keepPitch, reach, 1.0F);
                            boolean keepHits = this.isValidHit(keepMop, blockData.blockPos(), blockData.facing());
                            if (this.noUpdateWhenCanPlace.getValue() && keepHits) {
                                updateRotation = false;
                            }
                            if (this.edgeLimit.getValue() && !this.isGodBridgeOnEdge()) {
                                updateRotation = false;
                            }
                            if (updateRotation) {
                                this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
                                this.pitch = bestPitch;
                            } else {
                                this.yaw = keepYaw;
                                this.pitch = keepPitch;
                                hitVec = keepHits ? keepMop.hitVec : null;
                            }
                        } else if (this.airRescue.getValue() && (!mc.thePlayer.onGround || this.isGodBridgeOnEdge())) {
                            for (BlockData option : options) {
                                Vec3 rescue = this.applyRescueRotation(option, event);
                                if (rescue != null) {
                                    blockData = option;
                                    hitVec = rescue;
                                    break;
                                }
                            }
                        }
                    } else if (this.rotationMode.getValue() == 3) {
                        double[] offsets = {0.1, 0.3, 0.5, 0.7, 0.9};
                        double[] x = offsets, y = offsets, z = offsets;
                        switch (blockData.facing()) {
                            case NORTH: z = new double[]{0.02}; break;
                            case EAST: x = new double[]{0.98}; break;
                            case SOUTH: z = new double[]{0.98}; break;
                            case WEST: x = new double[]{0.02}; break;
                            case DOWN: y = new double[]{0.02}; break;
                            case UP: y = new double[]{0.98}; break;
                        }
                        float bestYaw = -180.0F, bestPitch = 0.0F;
                        double bestDist = Double.MAX_VALUE;
                        Vec3 bestHitVec = null;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double targetX = blockData.blockPos().getX() + dx;
                                    double targetY = blockData.blockPos().getY() + dy;
                                    double targetZ = blockData.blockPos().getZ() + dz;
                                    float[] rot = RotationUtil.getRotations(targetX, targetY, targetZ);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rot[0], rot[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                                            && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing()) {
                                        float yawDiff = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - this.yaw));
                                        float pitchDiff = Math.abs(rot[1] - this.pitch);
                                        double dist = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
                                        if (dist < bestDist) {
                                            bestDist = dist;
                                            bestYaw = rot[0];
                                            bestPitch = rot[1];
                                            bestHitVec = mop.hitVec;
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            bestYaw += RandomUtil.nextFloat(-0.5F, 0.5F);
                            bestPitch += RandomUtil.nextFloat(-0.3F, 0.3F);
                            this.yaw = RotationUtil.wrapAngleDiff(bestYaw, event.getYaw());
                            this.pitch = bestPitch;
                            this.canRotate = true;
                            hitVec = bestHitVec;
                        }
                    } else {
                        double[] x = placeOffsets;
                    double[] y = placeOffsets;
                    double[] z = placeOffsets;
                    switch (blockData.facing()) {
                        case NORTH:
                            z = new double[]{0.0};
                            break;
                        case EAST:
                            x = new double[]{1.0};
                            break;
                        case SOUTH:
                            z = new double[]{1.0};
                            break;
                        case WEST:
                            x = new double[]{0.0};
                            break;
                        case DOWN:
                            y = new double[]{0.0};
                            break;
                        case UP:
                            y = new double[]{1.0};
                    }
                    float bestYaw = -180.0F;
                    float bestPitch = 0.0F;
                    float bestDiff = 0.0F;
                    for (double dx : x) {
                        for (double dy : y) {
                            for (double dz : z) {
                                double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop != null
                                        && mop.typeOfHit == MovingObjectType.BLOCK
                                        && mop.getBlockPos().equals(blockData.blockPos())
                                        && mop.sideHit == blockData.facing()) {
                                    float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                    if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                        bestYaw = rotations[0];
                                        bestPitch = rotations[1];
                                        bestDiff = totalDiff;
                                        hitVec = mop.hitVec;
                                    }
                                }
                            }
                        }
                    }
                    if (bestYaw != -180.0F || bestPitch != 0.0F) {
                        this.yaw = bestYaw;
                        this.pitch = bestPitch;
                        this.canRotate = true;
                    }
                    }
                }
                if (blockData != null && hitVec == null && !mc.thePlayer.onGround && this.airRescue.getValue()) {
                    hitVec = this.applyRescueRotation(blockData, event);
                }
                if (this.canRotate && MoveUtil.isForwardPressed() && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    if (this.rotationMode.getValue() == 2) {
                        this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                    }
                }
                if (this.rotationMode.getValue() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    boolean towerSmooth = false;
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(90.0F, 95.0F) : RandomUtil.nextFloat(30.0F, 35.0F);
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                        towerSmooth = true;
                    }
                    if (this.isTowering()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                        towerSmooth = true;
                    }
                    if (!towerSmooth) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - event.getYaw());
                        float tolerance = this.rotateSpeed.getValue();
                        if (Math.abs(yawDiff) > tolerance) {
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + RotationUtil.clampAngle(yawDiff, tolerance));
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0) {
                    this.place(blockData.blockPos(), blockData.facing(), hitVec);
                    if (this.multiplace.getValue()) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = RotationUtil.rayTrace(this.yaw, this.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
                            if (mop != null
                                    && mop.typeOfHit == MovingObjectType.BLOCK
                                    && mop.getBlockPos().equals(blockData.blockPos())
                                    && mop.sideHit == blockData.facing()) {
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            } else {
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                if (mop == null
                                        || mop.typeOfHit != MovingObjectType.BLOCK
                                        || !mop.getBlockPos().equals(blockData.blockPos())
                                        || mop.sideHit != blockData.facing()) {
                                    break;
                                }
                                this.place(blockData.blockPos(), blockData.facing(), mop.hitVec);
                            }
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = BlockUtil.getHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        this.place(belowPlayer, this.targetFacing, hitVec);
                    }
                    this.targetFacing = null;
                } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0) {
                            hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            this.place(blockData.blockPos(), blockData.facing(), hitVec);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.towerTick = 2;
                                            this.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.towerTick = 2;
                                        this.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.towerTick = 0;
                                    this.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.towerDelay >= 4) {
                                    this.towerTick = 4;
                                    this.towerDelay = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.towerTick = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.towerTick = 0;
                                this.towerDelay = 0;
                                return;
                        }
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()) {
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }




    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.lastSlot = event.setSlot(this.lastSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.rotationTick = 3;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.towering = false;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
    }

    public static class BlockData {
        private final BlockPos blockPos;
        private final EnumFacing facing;

        public BlockData(BlockPos blockPos, EnumFacing enumFacing) {
            this.blockPos = blockPos;
            this.facing = enumFacing;
        }

        public BlockPos blockPos() {
            return this.blockPos;
        }

        public EnumFacing facing() {
            return this.facing;
        }
    }

    public static class SnapTarget extends BlockData {
        public final float yaw;
        public final float pitch;
        public final double distance;

        public SnapTarget(BlockPos blockPos, EnumFacing facing, float yaw, float pitch, double distance) {
            super(blockPos, facing);
            this.yaw = yaw;
            this.pitch = pitch;
            this.distance = distance;
        }
    }
}
