package myau.module.modules;

/**
 * Great Wall bridge ("长城搭"): S held the whole run with rhythmic jumping, an
 * alternating A/D nudge per jump cycle to keep the line straight, right-click held
 * to keep placing. Trigger: sneak on a block edge -> "Activate?" -> hold right-click.
 */
public class GreatWall extends BridgeModule {
    private boolean flip = false;

    public GreatWall() {
        super("GreatWall");
    }

    @Override
    protected String bridgeTag() {
        return "GreatWall";
    }

    @Override
    protected void onBridgeTick() {
        this.handleAutoSwap();
        int phase = this.cycleTick % 10;
        // S never released, space held for rhythmic jumps, right-click held (kept placing).
        this.stagedForward = -1.0f;
        this.stagedJump = true;
        if (phase == 0) {
            this.flip = !this.flip;
        }
        // Alternate A / D nudge (jump once, tap A; next jump, tap D).
        this.stagedStrafe = (phase >= 2 && phase <= 3) ? (this.flip ? 1.0f : -1.0f) : 0.0f;
        this.tryPlaceBlock();
    }
}
