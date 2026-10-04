package myau.module.modules;

import net.minecraft.entity.EntityLivingBase;
import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.LeftClickMouseEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.OpenMyau;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;

import static myau.config.Config.mc;

public class SmartAttack extends Module {
    public SmartAttack(){super("SmartAttack",false,false);}
    private final BooleanProperty onGround = new BooleanProperty("CancelGroundAttack",true);
    private final BooleanProperty onRising = new BooleanProperty("CancelRisingAttack",true);
    private final IntProperty stopHurtTime = new IntProperty("StopHurtTime",7,0,9);
    private final IntProperty targetHurtTime = new IntProperty("TargetHurtTime",0,0,9);
    public static final BooleanProperty onKillAura = new BooleanProperty("OnKillAura",true);
    public static final BooleanProperty cancelAuraBlocking = new BooleanProperty("CancelAuraBlocking",true,onKillAura::getValue);
    public static boolean shouldCancel;
    public final BooleanProperty hitSelect = new BooleanProperty("HitSelect", false);
    public final IntProperty hitSelectTicks = new IntProperty("HitSelectTicks", 5, 1, 10, hitSelect::getValue);
    public final IntProperty hitSelectTimeoutTicks = new IntProperty("HitSelectTimeoutTicks", 3, 1, 20, hitSelect::getValue);
    private EntityLivingBase target;
    private EntityLivingBase hitSelectTarget;
    private int hitSelectTimer;
    private boolean hitSelectWaiting;
    private boolean hitSelectHurt;
    @EventTarget
    public void onAttack(AttackEvent event){
        if (isEnabled()){
            target = (EntityLivingBase) event.getTarget();
        }
    }
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (isEnabled()){
            KillAura killAura = (KillAura) OpenMyau.moduleManager.getModule(KillAura.class);
            EntityLivingBase current = killAura != null && killAura.isEnabled() ? killAura.getTarget() : null;
            if (current != null) {
                target = current;
            }

            if (target != null && mc.thePlayer.getDistanceToEntity(target) > 6) {
                target = null;
                hitSelectTarget = null;
                hitSelectWaiting = false;
                hitSelectHurt = false;
                hitSelectTimer = 0;
            }
            if (target == null) {
                shouldCancel = false;
                return;
            }

            if (mc.thePlayer.onGround && onGround.getValue()) shouldCancel = true;
            if (mc.thePlayer.motionY >= 0 && onRising.getValue()) shouldCancel = true;
            if (target.hurtTime <= targetHurtTime.getValue()) shouldCancel = false;
            if (target.isBurning()) shouldCancel = false;
            if (mc.thePlayer.hurtTime > stopHurtTime.getValue()) shouldCancel = false;

            if (hitSelect.getValue()) {
                if (mc.thePlayer.hurtTime == 0 && target != hitSelectTarget) {
                    hitSelectTarget = target;
                    hitSelectWaiting = true;
                    hitSelectTimer = 0;
                    hitSelectHurt = false;
                }
                if (hitSelectWaiting) {
                    shouldCancel = true;
                    if (mc.thePlayer.hurtTime > 0 && !hitSelectHurt) {
                        hitSelectHurt = true;
                        hitSelectTimer = 0;
                    }
                    hitSelectTimer++;
                    int limit = hitSelectHurt ? hitSelectTicks.getValue() : hitSelectTimeoutTicks.getValue();
                    if (hitSelectTimer >= limit) {
                        hitSelectWaiting = false;
                        hitSelectHurt = false;
                        shouldCancel = false;
                    }
                }
            }
        }
    }
    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (shouldCancel) {
            event.setCancelled(true);
        }
    }
}
