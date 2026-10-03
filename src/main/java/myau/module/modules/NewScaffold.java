package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.*;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

/**
 * Onxy-style scaffold (Normal / GodBridge / Breezily / KeepY), rewritten with Myau's
 * native placement & rotation pipeline. Includes clutch, auto block switching, safe walk
 * and automatic jumping for bridge modes.
 */
public class NewScaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"NORMAL", "GODBRIDGE", "BREEZILY", "KEEPY"});
    public final ModeProperty rotation = new ModeProperty("rotation", 1, new String[]{"NONE", "SILENT"});
    public final BooleanProperty clutch = new BooleanProperty("clutch", true);
    public final BooleanProperty autoSwitch = new BooleanProperty("auto-switch", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty jump = new BooleanProperty("jump", true);
    public final BooleanProperty sprint = new BooleanProperty("sprint", false);

    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private boolean canRotate = false;
    private boolean breezilyFlip = false;
    private int placeCooldown = 0;

    public NewScaffold() {
        super("NewScaffold", false);
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    private boolean isBridgeMode() {
        int m = this.mode.getValue();
        return m == 1 || m == 2;
    }

    private int getMoveDirectionYaw() {
        return MathHelper.floor_double((double) (mc.thePlayer.rotationYaw * 4.0F / 360.0F) + 0.5) & 3;
    }

    private Vec3i getMoveDirectionVec() {
        switch (this.getMoveDirectionYaw()) {
            case 0:
                return new Vec3i(0, 0, -1);
            case 1:
                return new Vec3i(-1, 0, 0);
            case 2:
                return new Vec3i(0, 0, 1);
            default:
                return new Vec3i(1, 0, 0);
        }
    }

    private BlockData getTarget() {
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos below = new BlockPos(px, py - 1, pz);

        if (this.isBridgeMode()) {
            Vec3i dir = this.getMoveDirectionVec();
            BlockPos frontBelow = below.add(dir.getX(), 0, dir.getZ());
            if (BlockUtil.isReplaceable(frontBelow) && this.hasSupport(frontBelow)) {
                return new BlockData(frontBelow);
            }
        }
        if (BlockUtil.isReplaceable(below) && this.hasSupport(below)) {
            return new BlockData(below);
        }
        return null;
    }

    private boolean hasSupport(BlockPos pos) {
        BlockPos support = pos.down();
        if (support.getY() < 0) {
            return false;
        }
        return !BlockUtil.isReplaceable(support) && !BlockUtil.isInteractable(support);
    }

    private EnumFacing getBestFacing(BlockPos target) {
        BlockPos center = new BlockPos(MathHelper.floor_double(mc.thePlayer.posX), target.getY(), MathHelper.floor_double(mc.thePlayer.posZ));
        double bestDist = Double.MAX_VALUE;
        EnumFacing best = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing == EnumFacing.DOWN) {
                continue;
            }
            BlockPos offset = target.offset(facing);
            if (!BlockUtil.isReplaceable(offset)) {
                continue;
            }
            double dist = offset.distanceSqToCenter(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = facing;
            }
        }
        return best;
    }

    private void placeBlock(BlockPos pos, EnumFacing facing) {
        if (!ItemUtil.isHoldingBlock() || this.blockCount <= 0) {
            return;
        }
        Vec3 hitVec = BlockUtil.getClickVec(pos, facing);
        if (hitVec == null) {
            hitVec = new Vec3((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5);
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), pos, facing, hitVec)) {
            if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                this.blockCount--;
            }
            if (this.swing.getValue()) {
                mc.thePlayer.swingItem();
            } else {
                PacketUtil.sendPacket(new C0APacketAnimation());
            }
        }
    }

    private void selectBlock() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        this.blockCount = Math.min(this.blockCount, ItemUtil.isBlock(stack) ? stack.stackSize : 0);
        if (this.blockCount > 0) {
            return;
        }
        if (!this.autoSwitch.getValue()) {
            return;
        }
        int slot = mc.thePlayer.inventory.currentItem;
        if (this.blockCount == 0) {
            slot--;
        }
        for (int i = slot; i > slot - 9; i--) {
            int hotbar = (i % 9 + 9) % 9;
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbar);
            if (ItemUtil.isBlock(candidate)) {
                mc.thePlayer.inventory.currentItem = hotbar;
                this.blockCount = candidate.stackSize;
                return;
            }
        }
    }

    private float[] getTargetRotations(BlockData data) {
        BlockPos pos = data.pos();
        if (this.isBridgeMode()) {
            float moveYaw = MoveUtil.adjustYaw(mc.thePlayer.rotationYaw, 1.0F, 0.0F);
            float targetYaw = moveYaw + (this.mode.getValue() == 2 && this.breezilyFlip ? -45.0F : 45.0F);
            return new float[]{MathHelper.wrapAngleTo180_float(targetYaw), 75.7F};
        }
        double dx = (double) pos.getX() + 0.5 - mc.thePlayer.posX;
        double dy = (double) pos.getY() + 0.5 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
        double dz = (double) pos.getZ() + 0.5 - mc.thePlayer.posZ;
        return RotationUtil.getRotationsTo(dx, dy, dz, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    private boolean canSee(BlockData data, float yaw, float pitch) {
        BlockPos pos = data.pos();
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        return mop != null && mop.typeOfHit == MovingObjectType.BLOCK && mop.getBlockPos().equals(pos);
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.placeCooldown > 0) {
            this.placeCooldown--;
        }
        this.selectBlock();
        if (!ItemUtil.isHoldingBlock()) {
            return;
        }

        // Clutch: falling fast with nothing below -> place instantly, no rotation needed.
        if (this.clutch.getValue() && mc.thePlayer.motionY < -0.5) {
            BlockPos below = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY) - 1,
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );
            if (BlockUtil.isReplaceable(below) && this.hasSupport(below) && this.placeCooldown <= 0) {
                EnumFacing facing = this.getBestFacing(below);
                if (facing != null) {
                    this.placeBlock(below, facing);
                    this.placeCooldown = 2;
                }
            }
        }

        BlockData data = this.getTarget();
        if (data == null) {
            return;
        }
        float[] target = this.getTargetRotations(data);
        this.yaw = target[0];
        this.pitch = target[1];
        this.canRotate = true;

        if (this.rotation.getValue() == 1 && this.canSee(data, this.yaw, this.pitch)) {
            event.setRotation(this.yaw, this.pitch, 3);
            if (this.placeCooldown <= 0) {
                EnumFacing facing = this.getBestFacing(data.pos());
                if (facing != null) {
                    this.placeBlock(data.pos(), facing);
                    this.placeCooldown = 1;
                    if (this.mode.getValue() == 2) {
                        this.breezilyFlip = !this.breezilyFlip;
                    }
                }
            }
        } else if (this.rotation.getValue() == 0 && this.placeCooldown <= 0) {
            EnumFacing facing = this.getBestFacing(data.pos());
            if (facing != null) {
                this.placeBlock(data.pos(), facing);
                this.placeCooldown = 1;
                if (this.mode.getValue() == 2) {
                    this.breezilyFlip = !this.breezilyFlip;
                }
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.jump.getValue() && this.isBridgeMode() && mc.thePlayer.onGround && MoveUtil.isForwardPressed()) {
            mc.thePlayer.movementInput.jump = true;
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
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && !this.sprint.getValue()) {
            mc.thePlayer.setSprinting(false);
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

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.blockCount = -1;
        this.placeCooldown = 0;
        this.breezilyFlip = false;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
    }

    private static final class BlockData {
        private final BlockPos pos;

        private BlockData(BlockPos pos) {
            this.pos = pos;
        }

        public BlockPos pos() {
            return this.pos;
        }
    }
}
