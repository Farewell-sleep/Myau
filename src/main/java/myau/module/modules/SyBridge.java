package myau.module.modules;

/**
 * Sy bridge ("sy搭"): hold right-click to keep placing while S is held and the player
 * rhythmically re-jumps — the Telly-family cadence popularised by Sy_south.
 * Trigger: sneak on a block edge -> "Activate?" -> hold right-click.
 */
public class SyBridge extends BridgeModule {
    public SyBridge() {
        super("SyBridge");
    }

    @Override
    protected String bridgeTag() {
        return "SyBridge";
    }

    @Override
    protected void onBridgeTick() {
        this.handleAutoSwap();
        this.stagedForward = -1.0f;
        this.stagedJump = true;
        this.tryPlaceBlock();
    }
}
