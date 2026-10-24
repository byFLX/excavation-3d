package com.byflx.excavation3d.editor;

/**
 * Client side of opening an editor session (plan §9). Every begin request carries a fresh request
 * id; only the reply to the latest request of this editor is accepted, so a late reply meant for
 * an earlier editor or an earlier attempt never changes the session. The server drops begin
 * requests above its rate limit without answering, so an unanswered request is sent again after
 * {@link #RETRY_TICKS}, at most {@link #MAX_ATTEMPTS} times in total. Client main thread only.
 */
public final class SessionHandshake {
    /** Ticks to wait for a reply before asking again; longer than the server's begin window (40 ticks). */
    public static final int RETRY_TICKS = 50;
    /** Requests per editor including the first; afterwards the editor reports that the server did not answer. */
    public static final int MAX_ATTEMPTS = 4;

    public enum State {
        /** Nothing sent yet. */
        IDLE,
        /** A request is on its way; Apply stays disabled. */
        WAITING,
        /** The server opened a session. */
        OPEN,
        /** The server answered but refused the session (e.g. tool not held). */
        REFUSED,
        /** No answer after {@link #MAX_ATTEMPTS} requests; a late answer to the last one is still accepted. */
        NO_REPLY
    }

    /** Shared by all editors, so a reply addressed to a closed editor never matches a newer one. */
    private static int lastRequestId;

    private State state = State.IDLE;
    private int requestId;
    private int attempts;
    private int ticksWaiting;
    private int sessionId;

    /**
     * Sends the first request.
     *
     * @return the request id to send
     */
    public int start() {
        if (state != State.IDLE) {
            throw new IllegalStateException("handshake already started");
        }
        return send();
    }

    /**
     * Advances the wait by one client tick.
     *
     * @return the request id to send again, or 0 if nothing is to be sent
     */
    public int tick() {
        if (state != State.WAITING || ++ticksWaiting < RETRY_TICKS) {
            return 0;
        }
        if (attempts >= MAX_ATTEMPTS) {
            state = State.NO_REPLY;
            return 0;
        }
        return send();
    }

    /**
     * Handles a session reply.
     *
     * @return whether the reply answered this editor's latest request; other replies change nothing
     */
    public boolean onReply(int replyRequestId, boolean ok, int replySessionId) {
        boolean open = state == State.WAITING || state == State.NO_REPLY;
        if (!open || replyRequestId == 0 || replyRequestId != requestId) {
            return false;
        }
        if (ok && replySessionId != 0) {
            state = State.OPEN;
            sessionId = replySessionId;
        } else {
            state = State.REFUSED;
            sessionId = 0;
        }
        return true;
    }

    public State state() {
        return state;
    }

    /** The open session's id, or 0 while there is none. */
    public int sessionId() {
        return state == State.OPEN ? sessionId : 0;
    }

    /** Requests sent so far, including the first. */
    public int attempts() {
        return attempts;
    }

    private int send() {
        lastRequestId = lastRequestId == Integer.MAX_VALUE ? 1 : lastRequestId + 1;
        requestId = lastRequestId;
        attempts++;
        ticksWaiting = 0;
        state = State.WAITING;
        return requestId;
    }
}
