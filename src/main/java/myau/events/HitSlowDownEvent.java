package myau.events;

import myau.event.events.Event;

/**
 * Fired when the player's attack applies the vanilla hit slow-down
 * (motionX/Z *= 0.6 and setSprinting(false)). Listeners may change the
 * slow-down factor and whether the player keeps sprinting.
 */
public class HitSlowDownEvent implements Event {
    private double slowDown;
    private boolean sprint;

    public HitSlowDownEvent() {
        this.slowDown = 0.6;
        this.sprint = false;
    }

    public double getSlowDown() {
        return this.slowDown;
    }

    public void setSlowDown(double slowDown) {
        this.slowDown = slowDown;
    }

    public boolean getSprint() {
        return this.sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
