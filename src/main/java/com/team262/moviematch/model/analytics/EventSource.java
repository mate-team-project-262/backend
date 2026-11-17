package com.team262.moviematch.model.analytics;

public enum EventSource {
    /** Sent by the browser through POST /event. */
    FRONTEND,
    /** Recorded by the server itself (votes, results). */
    BACKEND
}
