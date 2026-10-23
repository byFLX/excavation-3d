package com.byflx.excavation3d.editor;

/** The Apply button and its explanation use the same decision. */
public enum ApplyAvailability {
    READY, INVALID_DRAFT, RESIZE_PENDING, WAITING_FOR_RESULT, SESSION_UNAVAILABLE;

    public static ApplyAvailability of(EditorDraft.Evaluation evaluation, boolean resizePending,
                                       boolean awaitingResult, boolean sessionOpen) {
        if (resizePending) return RESIZE_PENDING;
        if (evaluation == null || !evaluation.isValid()) return INVALID_DRAFT;
        if (awaitingResult) return WAITING_FOR_RESULT;
        if (!sessionOpen) return SESSION_UNAVAILABLE;
        return READY;
    }
}
