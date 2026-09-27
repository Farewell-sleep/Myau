package myau.events;

import myau.event.events.Event;

public class KeyEvent implements Event {
    private final int keyCode;
    private final boolean guiOpen;

    public KeyEvent(int key) {
        this(key, false);
    }

    public KeyEvent(int key, boolean guiOpen) {
        this.keyCode = key;
        this.guiOpen = guiOpen;
    }

    public int getKey() {
        return this.keyCode;
    }

    public boolean isGuiOpen() {
        return this.guiOpen;
    }
}
