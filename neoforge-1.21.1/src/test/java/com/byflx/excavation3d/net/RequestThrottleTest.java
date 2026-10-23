package com.byflx.excavation3d.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestThrottleTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void floodWithinOneWindowIsRefused() {
        RequestThrottle throttle = new RequestThrottle(4, 40);
        for (int i = 0; i < 4; i++) {
            assertTrue(throttle.tryAcquire(A, 100));
        }
        for (int i = 0; i < 1000; i++) {
            assertFalse(throttle.tryAcquire(A, 100 + i % 40));
        }
    }

    @Test
    void nextWindowAllowsRequestsAgain() {
        RequestThrottle throttle = new RequestThrottle(2, 40);
        assertTrue(throttle.tryAcquire(A, 0));
        assertTrue(throttle.tryAcquire(A, 1));
        assertFalse(throttle.tryAcquire(A, 39));
        assertTrue(throttle.tryAcquire(A, 40));
    }

    @Test
    void playersAreLimitedIndependently() {
        RequestThrottle throttle = new RequestThrottle(1, 40);
        assertTrue(throttle.tryAcquire(A, 0));
        assertFalse(throttle.tryAcquire(A, 0));
        assertTrue(throttle.tryAcquire(B, 0));
    }

    @Test
    void forgetAndClearResetTheLimit() {
        RequestThrottle throttle = new RequestThrottle(1, 40);
        assertTrue(throttle.tryAcquire(A, 0));
        throttle.forget(A);
        assertTrue(throttle.tryAcquire(A, 0));
        throttle.clear();
        assertTrue(throttle.tryAcquire(A, 0));
    }

    @Test
    void tickCounterGoingBackStartsANewWindow() {
        // A restarted server counts from 0 again; an old window must not block forever.
        RequestThrottle throttle = new RequestThrottle(1, 40);
        assertTrue(throttle.tryAcquire(A, 1000));
        assertTrue(throttle.tryAcquire(A, 5));
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RequestThrottle(0, 40));
        assertThrows(IllegalArgumentException.class, () -> new RequestThrottle(4, 0));
    }
}
