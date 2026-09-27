package myau.events;

import myau.event.events.Event;

public class Render2DPostEvent implements Event {
    private final float partialTicks;
    public Render2DPostEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }
    public float getPartialTicks() {
        return this.partialTicks;
    }
}
