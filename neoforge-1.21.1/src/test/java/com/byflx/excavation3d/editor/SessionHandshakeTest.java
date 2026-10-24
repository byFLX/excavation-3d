package com.byflx.excavation3d.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SessionHandshakeTest {
    /** Ticks until the handshake resends; returns the new request id (0 if none came). */
    private static int waitForRetry(SessionHandshake handshake) {
        for (int i = 0; i < SessionHandshake.RETRY_TICKS; i++) {
            int retry = handshake.tick();
            if (retry != 0) {
                return retry;
            }
        }
        return 0;
    }

    @Test
    void replyToTheRequestOpensTheSession() {
        SessionHandshake handshake = new SessionHandshake();
        assertEquals(SessionHandshake.State.IDLE, handshake.state());
        int request = handshake.start();
        assertEquals(SessionHandshake.State.WAITING, handshake.state());
        assertEquals(0, handshake.sessionId());

        assertTrue(handshake.onReply(request, true, 42));
        assertEquals(SessionHandshake.State.OPEN, handshake.state());
        assertEquals(42, handshake.sessionId());
        assertThrows(IllegalStateException.class, handshake::start);
    }

    @Test
    void droppedRequestIsSentAgainAfterTheWait() {
        SessionHandshake handshake = new SessionHandshake();
        int first = handshake.start();
        // The server's flood limit dropped the request: no reply arrives.
        for (int i = 0; i < SessionHandshake.RETRY_TICKS - 1; i++) {
            assertEquals(0, handshake.tick());
        }
        int second = handshake.tick();
        assertNotEquals(0, second);
        assertNotEquals(first, second);
        assertEquals(2, handshake.attempts());
        assertEquals(SessionHandshake.State.WAITING, handshake.state());

        assertTrue(handshake.onReply(second, true, 7));
        assertEquals(7, handshake.sessionId());
        assertEquals(0, waitForRetry(handshake), "an open session is never requested again");
    }

    @Test
    void retriesAreBoundedAndEndInAVisibleState() {
        SessionHandshake handshake = new SessionHandshake();
        int last = handshake.start();
        for (int attempt = 2; attempt <= SessionHandshake.MAX_ATTEMPTS; attempt++) {
            last = waitForRetry(handshake);
            assertNotEquals(0, last);
            assertEquals(attempt, handshake.attempts());
        }
        assertEquals(0, waitForRetry(handshake));
        assertEquals(SessionHandshake.State.NO_REPLY, handshake.state());
        for (int i = 0; i < 10 * SessionHandshake.RETRY_TICKS; i++) {
            assertEquals(0, handshake.tick());
        }
        assertEquals(SessionHandshake.MAX_ATTEMPTS, handshake.attempts());

        // A very late answer to the last request still opens the session.
        assertTrue(handshake.onReply(last, true, 9));
        assertEquals(SessionHandshake.State.OPEN, handshake.state());
        assertEquals(9, handshake.sessionId());
    }

    @Test
    void replyToAnEarlierAttemptIsIgnored() {
        SessionHandshake handshake = new SessionHandshake();
        int first = handshake.start();
        int second = waitForRetry(handshake);
        // The first request was only slow; its session was replaced on the server by the second.
        assertFalse(handshake.onReply(first, true, 1));
        assertEquals(0, handshake.sessionId());
        assertEquals(SessionHandshake.State.WAITING, handshake.state());
        assertTrue(handshake.onReply(second, true, 2));
        assertEquals(2, handshake.sessionId());
    }

    @Test
    void lateReplyForAClosedEditorNeverReachesTheNextOne() {
        // Editor A opens and is closed before its reply arrives; editor B opens.
        SessionHandshake editorA = new SessionHandshake();
        int requestA = editorA.start();
        SessionHandshake editorB = new SessionHandshake();
        int requestB = editorB.start();
        assertNotEquals(requestA, requestB);

        // A's reply arrives while B is shown: B stays without a session, so Apply stays disabled.
        assertFalse(editorB.onReply(requestA, true, 100));
        assertEquals(0, editorB.sessionId());
        assertEquals(SessionHandshake.State.WAITING, editorB.state());

        // B's own reply opens B's session (the one the server now holds).
        assertTrue(editorB.onReply(requestB, true, 200));
        assertEquals(200, editorB.sessionId());

        // Nothing arriving later replaces B's session, so an Apply reply for it is never ignored.
        assertFalse(editorB.onReply(requestA, true, 100));
        assertFalse(editorB.onReply(requestB, true, 300));
        assertEquals(200, editorB.sessionId());
    }

    @Test
    void refusalEndsTheWaitWithoutASession() {
        SessionHandshake handshake = new SessionHandshake();
        int request = handshake.start();
        assertFalse(handshake.onReply(0, false, 0));
        assertTrue(handshake.onReply(request, false, 0));
        assertEquals(SessionHandshake.State.REFUSED, handshake.state());
        assertEquals(0, handshake.sessionId());
        assertEquals(0, waitForRetry(handshake));
    }

    @Test
    void okReplyWithoutSessionIdIsTreatedAsRefusal() {
        SessionHandshake handshake = new SessionHandshake();
        int request = handshake.start();
        assertTrue(handshake.onReply(request, true, 0));
        assertEquals(SessionHandshake.State.REFUSED, handshake.state());
        assertEquals(0, handshake.sessionId());
    }
}
