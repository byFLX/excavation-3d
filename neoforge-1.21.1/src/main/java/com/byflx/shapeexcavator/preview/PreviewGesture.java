package com.byflx.shapeexcavator.preview;

/** Button-owned preview gesture. Release ends ownership before any edit callback runs. */
public final class PreviewGesture {
    public enum Release { NONE, CLICK, RESET, DRAG }
    public record Motion(boolean consumed, double dx, double dy) {
        private static final Motion NONE = new Motion(false, 0, 0);
    }

    private int button = -1;
    private double pressX, pressY;
    private boolean dragged;
    private long lastClick = -1;
    private double lastX, lastY;

    public int button() { return button; }

    public boolean press(int pressedButton, double x, double y, boolean inside) {
        cancel();
        if (!inside || (pressedButton != 0 && pressedButton != 1)) { lastClick = -1; return false; }
        button = pressedButton;
        pressX = x;
        pressY = y;
        return true;
    }

    public Motion drag(int eventButton, double x, double y, double dx, double dy, boolean held) {
        if (button < 0 || eventButton != button) return Motion.NONE;
        if (!held) { cancel(); return Motion.NONE; }
        if (!dragged) {
            if (!moved(x, y)) return new Motion(true, 0, 0);
            dragged = true;
            lastClick = -1;
            return new Motion(true, x - pressX, y - pressY);
        }
        return new Motion(true, dx, dy);
    }

    public Release release(int releasedButton, double x, double y, boolean inside, long now) {
        if (button < 0 || releasedButton != button) return Release.NONE;
        boolean click = inside && !dragged && !moved(x, y);
        button = -1;
        dragged = false;
        if (!click) { lastClick = -1; return Release.DRAG; }
        if (releasedButton == 0 && lastClick >= 0 && now - lastClick <= 350
                && now >= lastClick && Math.hypot(x - lastX, y - lastY) < 4) {
            lastClick = -1;
            return Release.RESET;
        }
        lastClick = releasedButton == 0 ? now : -1;
        lastX = x;
        lastY = y;
        return Release.CLICK;
    }

    public void cancel() { button = -1; dragged = false; }

    public void reset() { cancel(); lastClick = -1; }

    private boolean moved(double x, double y) {
        return Math.hypot(x - pressX, y - pressY) >= 4;
    }
}
