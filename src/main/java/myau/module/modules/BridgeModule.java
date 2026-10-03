package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.Render2DEvent;
import myau.events.Render3DEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.util.ChatUtil;
import myau.util.FontManager;
import myau.util.ItemUtil;
import myau.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Shared Telly-style trigger base for the three named bridging styles:
 * sneak on a block edge -> look down until the anchor lights up -> "Activate?"
 * prompt turns green -> release sneak while holding right-click to start.
 * Each subclass implements its own bridge cadence via {@link #onBridgeTick()}.
 */
public abstract class BridgeModule extends Module {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty autoSwap = new BooleanProperty("auto-swap", true);
    public final BooleanProperty disableSafeWalk = new BooleanProperty("disable-safewalk", true);
    public final BooleanProperty showActivationHitbox = new BooleanProperty("show-activation-hitbox", false);
    public final BooleanProperty print = new BooleanProperty("print", false);
    public final myau.property.properties.FloatProperty pitch = new myau.property.properties.FloatProperty("pitch", 74.5F, 70.0F, 85.0F);

    protected static final double ACTIVATION_ACROSS_MIN = 0.38;
    protected static final double ACTIVATION_ACROSS_MAX = 0.65;
    protected static final double ACTIVATION_HEIGHT_MIN = 0.25;
    protected static final double ACTIVATION_HEIGHT_MAX = 0.75;
    protected static final float ACTIVATION_YAW_TOLERANCE = 2.0f;

    // Trigger state
    protected boolean armed = false;
    protected boolean running = false;
    protected long activatePromptAt = 0L;
    protected long promptBrokeAt = 0L;
    protected int promptFadeRgb = 0xFF5555;
    protected float promptAlpha = 0.0f;
    protected long promptFadeLastAt = 0L;
    protected int[] activationAnchorPos = null;
    protected int activationAnchorFace = -1;
    protected int[] hitboxLastPos = null;
    protected int hitboxLastFace = -1;
    protected boolean eagleWasDisabledByBridge = false;
    protected boolean safeWalkWasEnabled = false;
    protected boolean safeWalkStateCaptured = false;

    // Run state
    protected float baseYaw = 0.0f;
    protected float bridgePitch = 77.0f;
    protected int setupTick = 0;
    protected int cycleTick = 0;
    protected float stagedForward = 0.0f;
    protected float stagedStrafe = 0.0f;
    protected boolean stagedJump = false;
    protected boolean stagedSprint = false;
    protected int placeCooldown = 0;

    public BridgeModule(String name) {
        super(name, false);
    }

    // ------------------------------------------------------------------ subclass hooks

    protected abstract String bridgeTag();

    /** Called once right before running starts (after baseYaw/pitch are set). */
    protected void onBridgeStart() {
    }

    /** Called every PRE tick while running. Set staged* fields and call tryPlaceBlock(). */
    protected abstract void onBridgeTick();

    /** Called when automation stops. */
    protected void onBridgeStop() {
    }

    protected float bridgePitch() {
        return this.pitch.getValue();
    }

    protected float activationPitch() {
        return 75.0f;
    }

    // ------------------------------------------------------------------ events

    @EventTarget(Priority.HIGHEST)
    public void handleUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (this.running) {
            this.setupTick++;
            this.cycleTick++;
            this.onBridgeTick();
            this.holdScriptedRotation();
            this.checkStopConditions();
        } else {
            this.updateActivationPrompt();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || !this.running) {
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.movementInput == null) {
            return;
        }
        player.movementInput.moveForward = this.stagedForward;
        player.movementInput.moveStrafe = this.stagedStrafe;
        player.movementInput.jump = this.stagedJump;
        player.movementInput.sneak = false; // suppress sneak so bridge can walk backwards freely
        if (this.stagedSprint) {
            player.setSprinting(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleRender2D(Render2DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        this.updateActivatePromptFade();
        if (!this.running && this.armed && this.activatePromptAt != 0L) {
            this.drawActivatePrompt();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.showActivationHitbox.getValue()) {
            return;
        }
        if (!this.armed || this.running || this.promptAlpha < 0.05f) {
            return;
        }
        if (this.activatePromptAt != 0L) {
            Object[] hit = this.raycastBlock(4.5, mc.thePlayer == null ? 0.0f : mc.thePlayer.rotationYaw,
                    mc.thePlayer == null ? 0.0f : Math.max(mc.thePlayer.rotationPitch, this.activationPitch()));
            if (hit != null && hit.length >= 3 && hit[0] != null && hit[2] != null) {
                int face = this.faceFromName((String) hit[2]);
                if (face >= 2) {
                    this.hitboxLastPos = this.posFromVec((Vec3) hit[0]);
                    this.hitboxLastFace = face;
                }
            }
        }
        if (this.hitboxLastPos == null || this.hitboxLastFace < 2) {
            return;
        }
        this.drawActivationFaceRegion(this.hitboxLastPos, this.hitboxLastFace);
    }

    // ------------------------------------------------------------------ trigger state machine

    protected boolean activationPromptReady() {
        return this.activatePromptAt != 0L && System.currentTimeMillis() - this.activatePromptAt >= 1000L;
    }

    protected boolean activationSuppressUse() {
        return this.activatePromptAt != 0L && System.currentTimeMillis() - this.activatePromptAt >= 850L;
    }

    protected void updateActivationPrompt() {
        EntityPlayer player = mc.thePlayer;
        if (player == null || (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat))) {
            this.clearActivationPrompt();
            return;
        }

        boolean lookingDown = player.rotationPitch >= this.activationPitch();
        boolean atEdge = lookingDown && this.isLookingAtEdge(player);

        if (this.isSneak() && atEdge) {
            if (this.activatePromptAt == 0L) {
                this.activatePromptAt = System.currentTimeMillis();
            }
            this.promptBrokeAt = 0L;
            this.captureActivationAnchor(player);
            if (this.activationPromptReady()) {
                this.disableEagleForBridge();
            }
            return;
        }

        if (this.activatePromptAt == 0L) {
            return;
        }
        if (!this.activationPromptReady()) {
            this.clearActivationPrompt();
            return;
        }

        if (this.promptBrokeAt == 0L) {
            this.rememberActivationPromptColor();
            this.promptBrokeAt = System.currentTimeMillis();
        }

        if (!this.isSneak() && Mouse.isButtonDown(1) && this.isActivationYawAligned(player.rotationYaw)) {
            this.rememberActivationPromptColor();
            this.activatePromptAt = 0L;
            this.promptBrokeAt = 0L;
            this.beginAutomation();
            return;
        }

        if (System.currentTimeMillis() - this.promptBrokeAt > 300L) {
            this.clearActivationPrompt();
        }
    }

    protected void clearActivationPrompt() {
        this.rememberActivationPromptColor();
        this.activatePromptAt = 0L;
        this.promptBrokeAt = 0L;
        this.activationAnchorPos = null;
        this.activationAnchorFace = -1;
    }

    protected void rememberActivationPromptColor() {
        if (this.activatePromptAt != 0L) {
            this.promptFadeRgb = this.activationPromptReady() ? 0x55FF55 : 0xFF5555;
        }
    }

    protected void updateActivatePromptFade() {
        boolean show = this.armed && !this.running && this.activatePromptAt != 0L;
        if (show) {
            this.rememberActivationPromptColor();
        }
        long now = System.currentTimeMillis();
        long elapsed = this.promptFadeLastAt == 0L ? 0L : Math.min(100L, now - this.promptFadeLastAt);
        this.promptFadeLastAt = now;
        float step = elapsed / 200.0f;
        this.promptAlpha += show ? step : -step;
        if (this.promptAlpha < 0.0f) {
            this.promptAlpha = 0.0f;
        }
        if (this.promptAlpha > 1.0f) {
            this.promptAlpha = 1.0f;
        }
    }

    protected void drawActivatePrompt() {
        if (this.promptAlpha < 0.05f || mc.currentScreen == null || mc.displayWidth <= 0) {
            return;
        }
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        String text = "Activate?";
        int alpha = (int) (this.promptAlpha * 255.0f);
        if (alpha < 16) {
            alpha = 16;
        }
        int color = (alpha << 24) | this.promptFadeRgb;
        float x = sr.getScaledWidth() / 2.0f - FontManager.getStringWidth(text) / 2.0f;
        float y = sr.getScaledHeight() / 2.0f + 10.0f;
        FontManager.drawString(text, x, y, color, true);
    }

    // ------------------------------------------------------------------ edge detection

    protected int[] travelDirectionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);
        if (Math.abs(rawX) >= Math.abs(rawZ)) {
            return new int[]{rawX >= 0.0 ? 1 : -1, 0};
        }
        return new int[]{0, rawZ >= 0.0 ? 1 : -1};
    }

    protected boolean isLookingAtEdge(EntityPlayer player) {
        if (!this.isActivationYawAligned(player.rotationYaw)) {
            return false;
        }
        Object[] hit = this.raycastBlock(4.5, player.rotationYaw, Math.max(player.rotationPitch, this.activationPitch()));
        if (hit == null || hit.length < 3 || hit[0] == null || hit[1] == null || hit[2] == null) {
            return false;
        }
        int face = this.faceFromName((String) hit[2]);
        if (face < 2) {
            return false;
        }
        if (!this.isInActivationFaceCenter(face, (Vec3) hit[1])) {
            return false;
        }
        int[] travel = this.travelDirectionFromYaw(player.rotationYaw);
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) {
            return false;
        }
        int[] pos = this.posFromVec((Vec3) hit[0]);
        if (!this.isPlayerOnActivationBlock(player, pos)) {
            return false;
        }
        int aheadX = pos[0] + travel[0];
        int aheadZ = pos[2] + travel[1];
        if (!this.isReplaceableName(this.blockNameAt(aheadX, pos[1] + 1, aheadZ), false)) {
            return false;
        }
        double lipDistance;
        if (face == 5) {
            lipDistance = (pos[0] + 1) - player.posX;
        } else if (face == 4) {
            lipDistance = player.posX - pos[0];
        } else if (face == 3) {
            lipDistance = (pos[2] + 1) - player.posZ;
        } else {
            lipDistance = player.posZ - pos[2];
        }
        if (lipDistance > 0.65) {
            return false;
        }
        this.hitboxLastPos = new int[]{pos[0], pos[1], pos[2]};
        this.hitboxLastFace = face;
        return true;
    }

    protected void captureActivationAnchor(EntityPlayer player) {
        if (player == null) {
            return;
        }
        Object[] hit = this.raycastBlock(4.5, player.rotationYaw, Math.max(player.rotationPitch, this.activationPitch()));
        if (hit == null || hit.length < 3 || hit[0] == null || hit[2] == null) {
            if (this.hitboxLastPos != null && this.hitboxLastFace >= 2) {
                this.activationAnchorPos = new int[]{this.hitboxLastPos[0], this.hitboxLastPos[1], this.hitboxLastPos[2]};
                this.activationAnchorFace = this.hitboxLastFace;
            }
            return;
        }
        int face = this.faceFromName((String) hit[2]);
        if (face < 2) {
            return;
        }
        int[] pos = this.posFromVec((Vec3) hit[0]);
        if (!this.isPlayerOnActivationBlock(player, pos)) {
            return;
        }
        if (!this.isInActivationFaceCenter(face, (Vec3) hit[1])) {
            return;
        }
        int[] travel = this.travelDirectionFromYaw(player.rotationYaw);
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) {
            return;
        }
        this.activationAnchorPos = new int[]{pos[0], pos[1], pos[2]};
        this.activationAnchorFace = face;
        this.hitboxLastPos = new int[]{pos[0], pos[1], pos[2]};
        this.hitboxLastFace = face;
    }

    protected boolean isActivationYawAligned(float yaw) {
        float nearestDiagonal = Math.round((yaw - 45.0f) / 90.0f) * 90.0f + 45.0f;
        return Math.abs(this.wrapAngle(yaw - nearestDiagonal)) <= ACTIVATION_YAW_TOLERANCE;
    }

    protected boolean isPlayerOnActivationBlock(EntityPlayer player, int[] pos) {
        if (pos == null) {
            return false;
        }
        if (pos[1] != MathHelper.floor_double(player.posY - 0.01)) {
            return false;
        }
        return Math.abs(player.posX - (pos[0] + 0.5)) <= 0.85
                && Math.abs(player.posZ - (pos[2] + 0.5)) <= 0.85;
    }

    protected boolean isInActivationFaceCenter(int face, Vec3 localHit) {
        if (localHit == null) {
            return false;
        }
        double acrossFace = (face == 4 || face == 5) ? localHit.zCoord : localHit.xCoord;
        if (face == 3 || face == 4) {
            acrossFace = 1.0 - acrossFace;
        }
        return acrossFace >= ACTIVATION_ACROSS_MIN && acrossFace <= ACTIVATION_ACROSS_MAX
                && localHit.yCoord >= ACTIVATION_HEIGHT_MIN && localHit.yCoord <= ACTIVATION_HEIGHT_MAX;
    }

    protected float wrapAngle(float angle) {
        while (angle <= -180.0f) {
            angle += 360.0f;
        }
        while (angle > 180.0f) {
            angle -= 360.0f;
        }
        return angle;
    }

    // ------------------------------------------------------------------ run helpers

    protected void beginAutomation() {
        EntityPlayer player = mc.thePlayer;
        if (player == null || !ItemUtil.isHoldingBlock()) {
            this.printStatus("&cHold blocks before starting");
            return;
        }
        if (!this.isActivationYawAligned(player.rotationYaw)) {
            return;
        }
        this.disableSafeWalkForRun();
        this.baseYaw = Math.round((player.rotationYaw - 45.0f) / 90.0f) * 90.0f + 45.0f;
        this.bridgePitch = this.bridgePitch();
        this.armed = false;
        this.running = true;
        this.setupTick = 0;
        this.cycleTick = 0;
        this.stagedForward = 0.0f;
        this.stagedStrafe = 0.0f;
        this.stagedJump = false;
        this.stagedSprint = false;
        this.placeCooldown = 0;
        this.activationAnchorPos = null;
        this.activationAnchorFace = -1;
        this.disableEagleForBridge();
        this.onBridgeStart();
        this.holdScriptedRotation();
        this.printStatus("&aStarted");
    }

    protected void stopAutomation(boolean printMsg) {
        boolean restoreEagle = this.eagleWasDisabledByBridge;
        this.armed = false;
        this.running = false;
        this.setupTick = 0;
        this.cycleTick = 0;
        this.stagedForward = 0.0f;
        this.stagedStrafe = 0.0f;
        this.stagedJump = false;
        this.stagedSprint = false;
        this.activationAnchorPos = null;
        this.activationAnchorFace = -1;
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.moveForward = 0.0f;
            mc.thePlayer.movementInput.moveStrafe = 0.0f;
            mc.thePlayer.movementInput.jump = false;
            mc.thePlayer.movementInput.sneak = false;
        }
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
        }
        this.restoreSafeWalkState();
        if (restoreEagle) {
            this.restoreEagleAfterBridge();
        }
        this.onBridgeStop();
        this.armed = true;
        this.activatePromptAt = 0L;
        this.promptBrokeAt = 0L;
        if (printMsg) {
            this.printStatus("&eStopped. Sneak looking down to arm again");
        }
    }

    protected void checkStopConditions() {
        if (!Mouse.isButtonDown(1)) {
            this.stopAutomation(true);
            return;
        }
        if (mc.gameSettings == null) {
            return;
        }
        // Any real keyboard movement key (hardware state, unaffected by scripted input) takes over.
        int[] keys = {
                mc.gameSettings.keyBindForward.getKeyCode(), mc.gameSettings.keyBindBack.getKeyCode(),
                mc.gameSettings.keyBindLeft.getKeyCode(), mc.gameSettings.keyBindRight.getKeyCode(),
                mc.gameSettings.keyBindJump.getKeyCode(), mc.gameSettings.keyBindSneak.getKeyCode(),
                mc.gameSettings.keyBindSprint.getKeyCode()
        };
        for (int k : keys) {
            if (k >= 0 && Keyboard.isKeyDown(k)) {
                this.stopAutomation(true);
                return;
            }
        }
    }

    protected void holdScriptedRotation() {
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return;
        }
        player.rotationYaw = this.baseYaw;
        player.rotationPitch = this.bridgePitch;
    }

    protected boolean tryPlaceBlock() {
        if (this.placeCooldown > 0) {
            this.placeCooldown--;
            return false;
        }
        Object[] hit = this.raycastBlock(4.5, this.baseYaw, this.bridgePitch);
        if (hit == null || hit.length < 3 || hit[0] == null || hit[1] == null || hit[2] == null) {
            return false;
        }
        Vec3 pos = (Vec3) hit[0];
        Vec3 offset = (Vec3) hit[1];
        String side = (String) hit[2];
        // Only place onto the side of the support block (never up/down) so the bridge
        // never builds upward.
        if (this.faceFromName(side) < 2) {
            return false;
        }
        boolean placed = this.placeBlock(pos, side, offset);
        if (placed) {
            this.placeCooldown = 1;
        }
        return placed;
    }

    protected Object[] raycastBlock(double distance, float yaw, float pitch) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, distance, 1.0f);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.getBlockPos() == null) {
            return null;
        }
        Vec3 pos = new Vec3(mop.getBlockPos());
        Vec3 offset = new Vec3(mop.hitVec.xCoord - pos.xCoord, mop.hitVec.yCoord - pos.yCoord, mop.hitVec.zCoord - pos.zCoord);
        return new Object[]{pos, offset, mop.sideHit.name()};
    }

    protected boolean placeBlock(Vec3 targetPos, String side, Vec3 hitOffset) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || targetPos == null || side == null || hitOffset == null) {
            return false;
        }
        EnumFacing facing = this.enumFacing(side, EnumFacing.UP);
        BlockPos pos = new BlockPos(targetPos.xCoord, targetPos.yCoord, targetPos.zCoord);
        Vec3 hit = new Vec3(targetPos.xCoord + hitOffset.xCoord, targetPos.yCoord + hitOffset.yCoord, targetPos.zCoord + hitOffset.zCoord);
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), pos, facing, hit)) {
            mc.thePlayer.swingItem();
            return true;
        }
        return false;
    }

    protected void handleAutoSwap() {
        if (!this.autoSwap.getValue() || mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return;
        }
        ItemStack held = mc.thePlayer.getHeldItem();
        int heldCount = held != null && ItemUtil.isBlock(held) ? held.stackSize : 0;
        if (heldCount > 5) {
            return;
        }
        int bestSlot = -1;
        int bestSize = heldCount;
        for (int slot = 0; slot <= 8; slot++) {
            if (slot == mc.thePlayer.inventory.currentItem) {
                continue;
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!ItemUtil.isBlock(stack)) {
                continue;
            }
            if (stack.stackSize > bestSize) {
                bestSize = stack.stackSize;
                bestSlot = slot;
            }
        }
        if (bestSlot != -1) {
            mc.thePlayer.inventory.currentItem = bestSlot;
        }
    }

    // ------------------------------------------------------------------ SafeWalk / Eagle

    protected void disableSafeWalkForRun() {
        if (this.safeWalkStateCaptured) {
            this.enforceSafeWalkDisabledForRun();
            return;
        }
        if (!this.disableSafeWalk.getValue()) {
            return;
        }
        try {
            Module safeWalk = OpenMyau.moduleManager.getModule("SafeWalk");
            this.safeWalkWasEnabled = safeWalk != null && safeWalk.isEnabled();
            this.safeWalkStateCaptured = true;
            if (this.safeWalkWasEnabled) {
                safeWalk.setEnabled(false);
            }
        } catch (Exception ignored) {
            this.safeWalkStateCaptured = false;
        }
    }

    protected void enforceSafeWalkDisabledForRun() {
        if (!this.safeWalkStateCaptured) {
            return;
        }
        try {
            Module safeWalk = OpenMyau.moduleManager.getModule("SafeWalk");
            if (safeWalk != null && safeWalk.isEnabled()) {
                safeWalk.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
    }

    protected void restoreSafeWalkState() {
        if (!this.safeWalkStateCaptured) {
            return;
        }
        boolean restoreEnabled = this.safeWalkWasEnabled;
        this.safeWalkStateCaptured = false;
        try {
            Module safeWalk = OpenMyau.moduleManager.getModule("SafeWalk");
            if (safeWalk == null) {
                return;
            }
            boolean currentlyEnabled = safeWalk.isEnabled();
            if (restoreEnabled && !currentlyEnabled) {
                safeWalk.setEnabled(true);
            }
            if (!restoreEnabled && currentlyEnabled) {
                safeWalk.setEnabled(false);
            }
        } catch (Exception ignored) {
        }
    }

    protected void disableEagleForBridge() {
        try {
            if (OpenMyau.moduleManager == null) {
                return;
            }
            Module eagle = OpenMyau.moduleManager.getModule("Eagle");
            if (eagle != null && eagle.isEnabled()) {
                eagle.setEnabled(false);
                this.eagleWasDisabledByBridge = true;
            }
        } catch (Throwable ignored) {
        }
    }

    protected void restoreEagleAfterBridge() {
        try {
            if (OpenMyau.moduleManager == null) {
                return;
            }
            Module eagle = OpenMyau.moduleManager.getModule("Eagle");
            if (eagle != null && !eagle.isEnabled()) {
                eagle.setEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------ misc helpers

    protected boolean isSneak() {
        return mc.thePlayer != null && mc.thePlayer.movementInput != null && mc.thePlayer.movementInput.sneak;
    }

    protected int faceFromName(String name) {
        if (name == null) {
            return -1;
        }
        String upper = name.toUpperCase();
        if (upper.equals("DOWN")) {
            return 0;
        }
        if (upper.equals("UP")) {
            return 1;
        }
        if (upper.equals("NORTH")) {
            return 2;
        }
        if (upper.equals("SOUTH")) {
            return 3;
        }
        if (upper.equals("WEST")) {
            return 4;
        }
        if (upper.equals("EAST")) {
            return 5;
        }
        return -1;
    }

    protected EnumFacing enumFacing(String side, EnumFacing fallback) {
        String upper = side == null ? "" : side.toUpperCase();
        switch (upper) {
            case "DOWN":
                return EnumFacing.DOWN;
            case "UP":
                return EnumFacing.UP;
            case "NORTH":
                return EnumFacing.NORTH;
            case "SOUTH":
                return EnumFacing.SOUTH;
            case "WEST":
                return EnumFacing.WEST;
            case "EAST":
                return EnumFacing.EAST;
            default:
                return fallback;
        }
    }

    protected int[] posFromVec(Vec3 vec) {
        return new int[]{MathHelper.floor_double(vec.xCoord), MathHelper.floor_double(vec.yCoord), MathHelper.floor_double(vec.zCoord)};
    }

    protected String blockNameAt(int x, int y, int z) {
        if (mc.theWorld == null) {
            return "air";
        }
        net.minecraft.block.Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        return block == null ? "air" : block.getUnlocalizedName().toLowerCase();
    }

    protected boolean isReplaceableName(String name, boolean airOnly) {
        if (airOnly) {
            return name.equals("tile.air") || name.equals("air");
        }
        if (name.equals("tile.air") || name.equals("air") || name.equals("tile.water") || name.equals("tile.lava")
                || name.equals("tile.tallgrass") || name.equals("tile.snow") || name.equals("tile.fire")
                || name.equals("tile.reeds") || name.equals("tile.vine")) {
            return true;
        }
        if (mc.theWorld == null) {
            return false;
        }
        return false;
    }

    protected void printStatus(String message) {
        if (!this.print.getValue()) {
            return;
        }
        ChatUtil.sendFormatted("&b" + this.bridgeTag() + " &7| " + message);
    }

    protected void drawActivationFaceRegion(int[] pos, int face) {
        RenderManager rm = mc.getRenderManager();
        if (rm == null) {
            return;
        }
        double camX = rm.viewerPosX;
        double camY = rm.viewerPosY;
        double camZ = rm.viewerPosZ;

        double yMin = pos[1] + ACTIVATION_HEIGHT_MIN;
        double yMax = pos[1] + ACTIVATION_HEIGHT_MAX;
        double x1, z1, x2, z2;

        if (face == 5) {
            x1 = pos[0] + 1.005;
            x2 = x1;
            z1 = pos[2] + ACTIVATION_ACROSS_MIN;
            z2 = pos[2] + ACTIVATION_ACROSS_MAX;
        } else if (face == 4) {
            x1 = pos[0] - 0.005;
            x2 = x1;
            z1 = pos[2] + (1.0 - ACTIVATION_ACROSS_MAX);
            z2 = pos[2] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else if (face == 3) {
            z1 = pos[2] + 1.005;
            z2 = z1;
            x1 = pos[0] + (1.0 - ACTIVATION_ACROSS_MAX);
            x2 = pos[0] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else {
            z1 = pos[2] - 0.005;
            z2 = z1;
            x1 = pos[0] + ACTIVATION_ACROSS_MIN;
            x2 = pos[0] + ACTIVATION_ACROSS_MAX;
        }

        int r = (this.promptFadeRgb >> 16) & 0xFF;
        int g = (this.promptFadeRgb >> 8) & 0xFF;
        int b = this.promptFadeRgb & 0xFF;
        int fillAlpha = (int) (60.0f * this.promptAlpha);
        int lineAlpha = (int) (220.0f * this.promptAlpha);
        if (fillAlpha < 4) {
            fillAlpha = 4;
        }
        if (lineAlpha < 16) {
            lineAlpha = 16;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.translate(-camX, -camY, -camZ);

        GL11.glColor4f(r / 255.0f, g / 255.0f, b / 255.0f, fillAlpha / 255.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, yMin, z1);
        GL11.glVertex3d(x2, yMin, z2);
        GL11.glVertex3d(x2, yMax, z2);
        GL11.glVertex3d(x1, yMax, z1);
        GL11.glEnd();

        GL11.glLineWidth(2.0f);
        GL11.glColor4f(r / 255.0f, g / 255.0f, b / 255.0f, lineAlpha / 255.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x1, yMin, z1);
        GL11.glVertex3d(x2, yMin, z2);
        GL11.glVertex3d(x2, yMax, z2);
        GL11.glVertex3d(x1, yMax, z1);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    @Override
    public void onEnabled() {
        this.armed = true;
        this.promptAlpha = 0.0f;
        this.activatePromptAt = 0L;
    }

    @Override
    public void onDisabled() {
        if (this.running) {
            this.stopAutomation(false);
        }
        this.armed = false;
        this.promptAlpha = 0.0f;
        this.activatePromptAt = 0L;
        this.activationAnchorPos = null;
        this.activationAnchorFace = -1;
        if (this.eagleWasDisabledByBridge) {
            this.restoreEagleAfterBridge();
        }
    }
}
