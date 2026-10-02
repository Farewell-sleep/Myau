package myau.module.modules;

import myau.event.EventTarget;
import myau.events.AttackEvent;
import myau.events.TickEvent;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.IntProperty;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * BlockHit — block right after every KillAura hit.
 *
 * The attack itself is handled by KillAura (which releases a manual block for
 * the attack frame). This module re-presses use for a short window right after
 * the hit so incoming knockback / damage is reduced, then releases again.
 */
public class BlockHit extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** How many ticks the block window stays up after a hit. */
    public final IntProperty blockTime = new IntProperty("BlockTicks", 3, 1, 10);
    /** Ticks to wait after the hit before pressing use again. */
    public final IntProperty delay = new IntProperty("Delay", 1, 0, 5);

    private boolean shouldBlock;
    private int tick;

    public BlockHit() {
        super("BlockHit", false);
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null) {
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        shouldBlock = false;
        tick = 0;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (ItemUtil.isHoldingSword() && event.getTarget() instanceof EntityLivingBase) {
            shouldBlock = true;
            tick = 0;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE || !this.isEnabled()
                || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (!shouldBlock) {
            return;
        }
        tick++;
        if (tick > this.delay.getValue()) {
            // block window: hold use
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        if (tick > this.delay.getValue() + this.blockTime.getValue()) {
            // window over: restore physical key state
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
            shouldBlock = false;
            tick = 0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.blockTime.getValue() + "t"};
    }
}
