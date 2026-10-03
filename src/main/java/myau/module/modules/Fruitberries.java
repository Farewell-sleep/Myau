package myau.module.modules;

/**
 * Fruitberries bridge ("果梅搭"): hold jump while tapping S, placing two blocks per
 * jump cycle (one at takeoff, one mid-air) in a perfectly straight line without
 * building upwards. Trigger: sneak on a block edge -> "Activate?" -> hold right-click.
 */
public class Fruitberries extends BridgeModule {
    private boolean sTapped = false;

    public Fruitberries() {
        super("Fruitberries");
    }

    @Override
    protected String bridgeTag() {
        return "Fruitberries";
    }

    @Override
    protected void onBridgeTick() {
        this.handleAutoSwap();
        int phase = this.cycleTick % 10;
        if (phase == 0) {
            this.sTapped = false;
        }
        // Space is held the whole run (auto re-jump on landing).
        this.stagedJump = true;
        if (phase < 2) {
            // Takeoff: brief S tap + first block of the pair.
            this.stagedForward = -1.0f;
            if (!this.sTapped) {
                this.sTapped = true;
                this.tryPlaceBlock();
            }
        } else {
            this.stagedForward = 0.0f;
            if (phase == 3) {
                // Mid-air: second block of the pair.
                this.tryPlaceBlock();
            }
        }
    }
}
