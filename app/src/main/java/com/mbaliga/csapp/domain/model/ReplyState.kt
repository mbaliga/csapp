package com.mbaliga.csapp.domain.model

/**
 * Per-signal reply lifecycle. Replies are only ever sent as a result of an explicit human
 * confirmation ([ReplyState.SEND_CONFIRMED] -> [ReplyState.SENT]); nothing in this app
 * transitions a reply to SENT automatically.
 */
enum class ReplyState {
    /** No reply has been drafted for this signal yet. */
    NONE,
    /** A human (or the app, as a suggestion) has drafted reply text that has not been sent. */
    DRAFTED,
    /** The human tapped "send" and confirmed the exact text; send is in flight. */
    SEND_CONFIRMED,
    /** The reply was successfully delivered to the source platform. */
    SENT,
    /** The reply attempt failed and needs human attention. */
    FAILED,
}
