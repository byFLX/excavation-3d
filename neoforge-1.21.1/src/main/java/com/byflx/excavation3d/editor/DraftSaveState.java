package com.byflx.excavation3d.editor;

/**
 * Tracks whether the editor draft differs from what the server last confirmed (plan §2). Every
 * edit raises a generation counter; Apply remembers the generation it sent. The server's
 * confirmation only marks that generation as saved, so edits made while the reply is on its way
 * stay unsaved and still need the discard confirmation.
 */
public final class DraftSaveState {
    private long generation;
    private long savedGeneration;

    /** Records a change to the draft. */
    public void edited() {
        generation++;
    }

    public boolean isDirty() {
        return generation != savedGeneration;
    }

    /** @return the token to pass to {@link #accepted} when the server confirms this submission */
    public long submit() {
        return generation;
    }

    /** Retracts a speculative click only while it is still the latest, unconfirmed change. */
    public boolean rollbackEdit(long before, long after) {
        if (generation != after || savedGeneration > before || before > after) return false;
        generation = before;
        return true;
    }

    /**
     * The server confirmed the draft as it was at {@code submitted}.
     *
     * @return whether the draft is now fully saved, i.e. nothing was edited after submitting
     */
    public boolean accepted(long submitted) {
        if (submitted > savedGeneration && submitted <= generation) {
            savedGeneration = submitted;
        }
        return !isDirty();
    }
}
