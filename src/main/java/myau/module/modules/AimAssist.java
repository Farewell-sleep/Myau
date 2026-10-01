package myau.module.modules;

import myau.OpenMyau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.ModeProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.PercentProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class AimAssist extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 3.0F, 0.0F, 10.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 0.0F, 0.0F, 10.0F);
    public final PercentProperty smoothing = new PercentProperty("smoothing", 50);
    public final FloatProperty range = new FloatProperty("range", 4.5F, 3.0F, 8.0F);
    public final IntProperty fov = new IntProperty("fov", 90, 30, 360);
    public final BooleanProperty weaponOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponOnly::getValue);
    public final BooleanProperty botChecks = new BooleanProperty("bot-check", true);
    public final BooleanProperty team = new BooleanProperty("teams", true);
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"NORMAL", "LOCK-ON"});
    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "ANGLE", "HEALTH"});
    public final BooleanProperty aimInvis = new BooleanProperty("aim-invis", false);

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else if (RotationUtil.distanceToEntity(entityPlayer) > (double) this.range.getValue()) {
                return false;
            } else if (RotationUtil.angleToEntity(entityPlayer) > (float) this.fov.getValue()) {
                return false;
            } else if (RotationUtil.rayTrace(entityPlayer) != null) {
                return false;
            } else if (!this.aimInvis.getValue() && entityPlayer.isInvisible()) {
                return false;
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return false;
            } else {
                return (!this.team.getValue() || !TeamUtil.isSameTeam(entityPlayer)) && (!this.botChecks.getValue() || !TeamUtil.isBot(entityPlayer));
            }
        } else {
            return false;
        }
    }

    private boolean isInReach(EntityPlayer entityPlayer) {
        Reach reach = (Reach) OpenMyau.moduleManager.modules.get(Reach.class);
        double distance = reach.isEnabled() ? (double) reach.range.getValue() : 3.0;
        return RotationUtil.distanceToEntity(entityPlayer) <= distance;
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    public AimAssist() {
        super("AimAssist", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.POST && mc.currentScreen == null) {
            if (!(Boolean) this.weaponOnly.getValue()
                    || ItemUtil.hasRawUnbreakingEnchant()
                    || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
                boolean attacking = PlayerUtil.isAttacking();
                if (!attacking || !this.isLookingAtBlock()) {
                    if (attacking || !this.timer.hasTimeElapsed(350L)) {
                        List<EntityPlayer> inRange = this.collectTargets();
                        if (!inRange.isEmpty()) {
                            if (inRange.stream().anyMatch(this::isInReach)) {
                                inRange.removeIf(entityPlayer -> !this.isInReach(entityPlayer));
                            }
                            EntityPlayer player = inRange.get(0);
                            if (!(RotationUtil.distanceToEntity(player) <= 0.0)) {
                                AxisAlignedBB axisAlignedBB = player.getEntityBoundingBox();
                                double collisionBorderSize = player.getCollisionBorderSize();
                                float[] rotation = RotationUtil.getRotationsToBox(
                                        axisAlignedBB.expand(collisionBorderSize, collisionBorderSize, collisionBorderSize),
                                        mc.thePlayer.rotationYaw,
                                        mc.thePlayer.rotationPitch,
                                        180.0F,
                                        this.mode.getValue() == 1 ? 1.0F : (float) this.smoothing.getValue() / 100.0F
                                );
                                if (this.mode.getValue() == 1) {
                                    // LOCK-ON: skid of Raven AimAssist normal mode - snap directly onto the target
                                    mc.thePlayer.rotationYaw = rotation[0];
                                    mc.thePlayer.rotationPitch = rotation[1];
                                } else {
                                    float yaw = Math.min(Math.abs(this.hSpeed.getValue()), 10.0F);
                                    float pitch = Math.min(Math.abs(this.vSpeed.getValue()), 10.0F);
                                    OpenMyau.rotationManager
                                            .setRotation(
                                                    mc.thePlayer.rotationYaw + (rotation[0] - mc.thePlayer.rotationYaw) * 0.1F * yaw,
                                                    mc.thePlayer.rotationPitch + (rotation[1] - mc.thePlayer.rotationPitch) * 0.1F * pitch,
                                                    0,
                                                    false
                                            );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private List<EntityPlayer> collectTargets() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(entity -> entity instanceof EntityPlayer)
                .map(entity -> (EntityPlayer) entity)
                .filter(this::isValidTarget)
                .sorted(this.targetComparator())
                .collect(Collectors.toList());
    }

    private Comparator<EntityPlayer> targetComparator() {
        switch (this.sort.getValue()) {
            case 0:
                return Comparator.comparingDouble(RotationUtil::distanceToEntity);
            case 1:
                return Comparator.comparingDouble(this::aimDelta);
            default:
                return Comparator.comparingDouble(p -> p.getHealth() + p.getAbsorptionAmount());
        }
    }

    private double aimDelta(EntityPlayer entityPlayer) {
        double yawDelta = Math.abs(RotationUtil.angleToEntity(entityPlayer));
        double pitchDelta = Math.abs(RotationUtil.pitchToEntity(entityPlayer));
        return yawDelta + pitchDelta;
    }

    @EventTarget
    public void onPress(KeyEvent event) {
        if (event.getKey() == mc.gameSettings.keyBindAttack.getKeyCode() && !OpenMyau.moduleManager.modules.get(AutoClicker.class).isEnabled()) {
            this.timer.reset();
        }
    }
}
