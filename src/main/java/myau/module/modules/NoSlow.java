package myau.module.modules;

import myau.OpenMyau;
import myau.enums.FloatModules;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LivingUpdateEvent;
import myau.events.LoadWorldEvent;
import myau.events.PlayerUpdateEvent;
import myau.events.RightClickMouseEvent;
import myau.events.TickEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.ItemUtil;
import myau.util.PacketUtil;
import myau.util.PlayerUtil;
import myau.util.TeamUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.BlockPos;

public class NoSlow extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastSlot = -1;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"CLASSIC", "HYPIXEL", "ONYX"});
    public final ModeProperty swordMode = new ModeProperty("sword-mode", 1, new String[]{"NONE", "VANILLA"});
    public final PercentProperty swordMotion = new PercentProperty("sword-motion", 100, () -> this.swordMode.getValue() != 0);
    public final BooleanProperty swordSprint = new BooleanProperty("sword-sprint", true, () -> this.swordMode.getValue() != 0);
    public final ModeProperty foodMode = new ModeProperty("food-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT"});
    public final PercentProperty foodMotion = new PercentProperty("food-motion", 100, () -> this.foodMode.getValue() != 0);
    public final BooleanProperty foodSprint = new BooleanProperty("food-sprint", true, () -> this.foodMode.getValue() != 0);
    public final ModeProperty bowMode = new ModeProperty("bow-mode", 0, new String[]{"NONE", "VANILLA", "FLOAT"});
    public final PercentProperty bowMotion = new PercentProperty("bow-motion", 100, () -> this.bowMode.getValue() != 0);
    public final BooleanProperty bowSprint = new BooleanProperty("bow-sprint", true, () -> this.bowMode.getValue() != 0);

    // ---- ONYX mode (skidded from Onyx NoSlowModule) ----
    // While using an item the player is silently turned to the input-opposite
    // angle, input is kept at 0.2x, jumping is suppressed and sprint is forced —
    // the classic "turn-around NoSlow" that keeps full speed on servers that
    // punish item-use movement.
    public final BooleanProperty pauseOnKillAura = new BooleanProperty("pause-on-killaura", true);

    private boolean onyxActive = false;
    private boolean onyxNoJump = false;
    private float savedYaw = 0.0F;
    private float savedPitch = 0.0F;

    public NoSlow() {
        super("NoSlow", false);
    }

    // ---- ONYX helpers ----

    private boolean onyxMode() {
        return this.mode.getValue() == 2;
    }

    /** Onyx isEnabled97: module on && !(pause-on-killaura && killaura on). */
    private boolean onyxShouldRun() {
        if (!this.isEnabled() || !this.onyxMode()) {
            return false;
        }
        if (this.pauseOnKillAura.getValue()) {
            KillAura killAura = (KillAura) OpenMyau.moduleManager.modules.get(KillAura.class);
            if (killAura != null && killAura.isEnabled()) {
                return false;
            }
        }
        return true;
    }

    /** Onyx isEnabled96: KillAura is enabled and has a target. */
    private boolean onyxHasTarget() {
        KillAura killAura = (KillAura) OpenMyau.moduleManager.modules.get(KillAura.class);
        return killAura != null && killAura.isEnabled() && killAura.getTarget() != null;
    }

    /** Onyx isEnabled100: held item exists and is not a bow. */
    private boolean onyxHeldNotBow() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() != Items.bow;
    }

    /** Onyx isEnabled98: any movement key pressed. */
    private static boolean onyxKeysDown() {
        GameSettings settings = mc.gameSettings;
        return settings.keyBindRight.isKeyDown()
                || settings.keyBindLeft.isKeyDown()
                || settings.keyBindForward.isKeyDown()
                || settings.keyBindBack.isKeyDown();
    }

    /** Onyx getFloat89: angle of the current movement input (0-360). */
    private float onyxInputAngle() {
        GameSettings settings = mc.gameSettings;
        float angle = 180.0F;
        if (settings.keyBindBack.isKeyDown()) {
            angle = 0.0F;
            if (settings.keyBindRight.isKeyDown()) {
                angle -= 45.0F;
            }
            if (settings.keyBindLeft.isKeyDown()) {
                angle += 45.0F;
            }
        } else if (settings.keyBindForward.isKeyDown()) {
            if (settings.keyBindRight.isKeyDown()) {
                angle = 225.0F;
            }
            if (settings.keyBindLeft.isKeyDown()) {
                angle -= 45.0F;
            }
        } else {
            if (settings.keyBindRight.isKeyDown()) {
                angle = 270.0F;
            }
            if (settings.keyBindLeft.isKeyDown()) {
                angle -= 90.0F;
            }
        }
        return (MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) + angle % 360.0F + 360.0F) % 360.0F;
    }

    private void onyxApplyRotation(float yaw, float pitch) {
        if (!this.onyxActive) {
            this.savedYaw = mc.thePlayer.rotationYaw;
            this.savedPitch = mc.thePlayer.rotationPitch;
        }
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
    }

    private void onyxStop() {
        if (this.onyxActive) {
            mc.thePlayer.rotationYaw = this.savedYaw;
            mc.thePlayer.rotationPitch = this.savedPitch;
        }
        this.onyxActive = false;
    }

    /** Onyx getFloat88: movement friction while the turn-around is active. */
    public float getOnyxFriction() {
        if (mc.thePlayer != null && this.onyxActive && mc.thePlayer.isUsingItem()
                && !mc.thePlayer.isRiding() && this.onyxShouldRun()) {
            return 0.1F;
        }
        return 0.8F;
    }

    /** Onyx handleEventSub10: per-tick rotation activation. */
    @EventTarget
    public void onTickOnyx(TickEvent event) {
        if (event.getType() != EventType.POST) {
            return;
        }
        this.onyxNoJump = false;
        if (mc.thePlayer == null || mc.theWorld == null || !this.onyxShouldRun()) {
            this.onyxStop();
        } else if (this.onyxHasTarget()) {
            this.onyxActive = false;
        } else if (mc.thePlayer.isUsingItem() && this.onyxHeldNotBow()) {
            float angle = this.onyxInputAngle() + 180.0F;
            if (!mc.gameSettings.keyBindJump.isKeyDown() || !mc.thePlayer.onGround) {
                angle -= 45.0F;
                this.onyxNoJump = true;
            }
            this.onyxApplyRotation(angle, mc.thePlayer.rotationPitch);
            this.onyxActive = true;
        } else {
            this.onyxStop();
        }
    }

    /** Onyx handleEventSub69 -> run125: reset on world change. */
    @EventTarget
    public void onLoadWorldOnyx(LoadWorldEvent event) {
        this.onyxStop();
    }

    /** Onyx run126 + run127: input dampening, jump suppression, forced sprint. */
    @EventTarget
    public void onLivingUpdateOnyx(LivingUpdateEvent event) {
        if (!this.onyxShouldRun() || mc.thePlayer == null || mc.thePlayer.movementInput == null) {
            return;
        }
        MovementInput input = mc.thePlayer.movementInput;
        if (this.onyxActive && mc.thePlayer.isUsingItem() && !mc.thePlayer.isRiding() && onyxKeysDown()) {
            input.moveForward *= 0.2F;
            input.moveStrafe *= 0.2F;
        }
        if (this.onyxNoJump) {
            input.jump = false;
        }
        if (this.onyxActive && !mc.thePlayer.isCollidedHorizontally && input.moveForward > 0.0F) {
            mc.thePlayer.setSprinting(true);
        }
    }

    @Override
    public void onDisabled() {
        this.onyxStop();
    }

    public boolean isSwordActive() {
        return this.swordMode.getValue() != 0 && ItemUtil.isHoldingSword();
    }

    public boolean isFoodActive() {
        return this.foodMode.getValue() != 0 && ItemUtil.isEating();
    }

    public boolean isBowActive() {
        return this.bowMode.getValue() != 0 && ItemUtil.isUsingBow();
    }

    public boolean isFloatMode() {
        return this.foodMode.getValue() == 2 && ItemUtil.isEating()
                || this.bowMode.getValue() == 2 && ItemUtil.isUsingBow();
    }

    public boolean isAnyActive() {
        return mc.thePlayer.isUsingItem() && (this.isSwordActive() || this.isFoodActive() || this.isBowActive());
    }

    public boolean canSprint() {
        return this.isSwordActive() && this.swordSprint.getValue()
                || this.isFoodActive() && this.foodSprint.getValue()
                || this.isBowActive() && this.bowSprint.getValue();
    }

    public int getMotionMultiplier() {
        if (ItemUtil.isHoldingSword()) {
            return this.swordMotion.getValue();
        } else if (ItemUtil.isEating()) {
            return this.foodMotion.getValue();
        } else {
            return ItemUtil.isUsingBow() ? this.bowMotion.getValue() : 100;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() == 1 || this.onyxMode()) {
            return;
        }
        if (this.isAnyActive()) {
            float multiplier = (float) this.getMotionMultiplier() / 100.0F;
            mc.thePlayer.movementInput.moveForward *= multiplier;
            mc.thePlayer.movementInput.moveStrafe *= multiplier;
            if (!this.canSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget(Priority.LOW)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!this.isEnabled() || this.onyxMode()) {
            return;
        }
        if (this.mode.getValue() == 1) {
            // Hypixel: drop the use state right before the position packet is sent.
            // Server then computes the motion tick without slow-down (Rise NCPNoSlow
            // heart-beat); the client keeps using the item, so eating/bowing/blocking
            // continue and the client-side no-slow is handled by the mixin isUsingItem
            // redirect (no input multiplication here).
            if (this.isAnyActive()) {
                PacketUtil.sendPacket(new C07PacketPlayerDigging(
                        C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                        BlockPos.ORIGIN,
                        EnumFacing.DOWN
                ));
            }
            return;
        }
        if (this.isFloatMode()) {
            int item = mc.thePlayer.inventory.currentItem;
            if (this.lastSlot != item && PlayerUtil.isUsingItem()) {
                this.lastSlot = item;
                OpenMyau.floatManager.setFloatState(true, FloatModules.NO_SLOW);
            }
        } else {
            this.lastSlot = -1;
            OpenMyau.floatManager.setFloatState(false, FloatModules.NO_SLOW);
        }
    }

    @EventTarget(Priority.LOW)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 1 && event.getType() == EventType.POST) {
            // Restore the use state after the motion packet so the item is still
            // in use on the server for the next ticks (heart-beat).
            if (this.isAnyActive() && mc.thePlayer.getHeldItem() != null) {
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && !this.onyxMode()) {
            if (mc.objectMouseOver != null) {
                switch (mc.objectMouseOver.typeOfHit) {
                    case BLOCK:
                        BlockPos blockPos = mc.objectMouseOver.getBlockPos();
                        if (BlockUtil.isInteractable(blockPos) && !PlayerUtil.isSneaking()) {
                            return;
                        }
                        break;
                    case ENTITY:
                        Entity entityHit = mc.objectMouseOver.entityHit;
                        if (entityHit instanceof EntityVillager) {
                            return;
                        }
                        if (entityHit instanceof EntityLivingBase && TeamUtil.isShop((EntityLivingBase) entityHit)) {
                            return;
                        }
                }
            }
            if (this.mode.getValue() == 0 && this.isFloatMode() && !OpenMyau.floatManager.isPredicted() && mc.thePlayer.onGround) {
                event.setCancelled(true);
                mc.thePlayer.motionY = 0.42F;
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}