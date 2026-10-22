package com.byflx.shapeexcavator.net;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player request limit (plan §9: request rate): at most {@code maxRequests} requests within
 * {@code windowTicks} server ticks. Bursts a human causes by reopening the editor stay allowed;
 * a client flooding requests is refused before the server does any work or answers. Server main
 * thread only.
 */
final class RequestThrottle {
    private static final class Window {
        int start;
        int count;
    }

    private final int maxRequests;
    private final int windowTicks;
    private final Map<UUID, Window> windows = new HashMap<>();

    RequestThrottle(int maxRequests, int windowTicks) {
        if (maxRequests < 1 || windowTicks < 1) {
            throw new IllegalArgumentException("maxRequests and windowTicks must be at least 1");
        }
        this.maxRequests = maxRequests;
        this.windowTicks = windowTicks;
    }

    /** Records a request at {@code tick} and returns whether it is within the limit. */
    boolean tryAcquire(UUID player, int tick) {
        Window window = windows.get(player);
        if (window == null || tick - window.start >= windowTicks || tick < window.start) {
            window = new Window();
            window.start = tick;
            windows.put(player, window);
        }
        if (window.count >= maxRequests) {
            return false;
        }
        window.count++;
        return true;
    }

    void forget(UUID player) {
        windows.remove(player);
    }

    void clear() {
        windows.clear();
    }
}
