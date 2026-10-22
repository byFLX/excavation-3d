package com.byflx.shapeexcavator.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.byflx.shapeexcavator.editor.SessionHandshake;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The client's begin retry together with the server's begin flood limit ({@link EditSessions}). */
class BeginRetryTest {
    @Test
    void retryWaitOutlastsTheServerWindow() {
        assertTrue(SessionHandshake.RETRY_TICKS > EditSessions.BEGIN_WINDOW_TICKS);
    }

    @Test
    void fifthQuickOpenIsDroppedAndItsRetryGetsASession() {
        RequestThrottle begins = new RequestThrottle(EditSessions.MAX_BEGINS_PER_WINDOW,
                EditSessions.BEGIN_WINDOW_TICKS);
        UUID player = UUID.randomUUID();
        int tick = 0;
        // Four quick opens, each answered.
        for (int i = 0; i < EditSessions.MAX_BEGINS_PER_WINDOW; i++) {
            SessionHandshake editor = new SessionHandshake();
            int request = editor.start();
            assertTrue(begins.tryAcquire(player, tick));
            assertTrue(editor.onReply(request, true, 10 + i));
            tick += 5;
        }

        // The fifth open in the same window is dropped without a reply.
        SessionHandshake editor = new SessionHandshake();
        editor.start();
        assertFalse(begins.tryAcquire(player, tick));
        assertEquals(0, editor.sessionId());

        int retry = 0;
        while (retry == 0) {
            retry = editor.tick();
            tick++;
        }
        assertNotEquals(SessionHandshake.State.NO_REPLY, editor.state());
        assertTrue(begins.tryAcquire(player, tick), "the retry falls into a new window");
        assertTrue(editor.onReply(retry, true, 99));
        assertEquals(99, editor.sessionId());
    }
}
