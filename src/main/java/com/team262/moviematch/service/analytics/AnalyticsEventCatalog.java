package com.team262.moviematch.service.analytics;

import java.util.Set;

/** Event names from the Product Analytics & Event Tracking Specification. */
public final class AnalyticsEventCatalog {

    private AnalyticsEventCatalog() {
    }

    /** Events the browser may send through POST /event. */
    private static final Set<String> CLIENT_EVENTS = Set.of(
            // landing and entry
            "create_room_submitted", "create_room_clicked", "guest_start_clicked", "join_room_clicked",
            "header_sign_in_clicked", "sign_up_clicked", "account_section_sign_in_clicked",
            // create room
            "guest_name_entered", "content_type_selected", "genres_selected", "max_duration_selected",
            "streaming_services_selected",
            // host room / invite
            "room_invite_link_copied", "room_invite_telegram_clicked", "start_session_clicked", "participant_count",
            // swipe session
            "swipe_right", "swipe_left", "like_button", "dislike_button", "movie_liked", "movie_disliked",
            // results
            "winner_details_clicked", "another_round_clicked", "view_sessions_clicked", "explore_taste_clicked",
            // sign in / sign up
            "sign_in_clicked", "forgot_password_clicked", "sign_up_succeeded", "sign_in_from_signup_clicked",
            // home and settings
            "session_details_clicked", "settings_clicked", "profile_photo_upload_clicked",
            "change_password_clicked", "taste_profile_preference_changed", "delete_account_clicked");

    /** Events only the server may record; the browser cannot send them. */
    private static final Set<String> BACKEND_EVENTS = Set.of(
            "movie_won", "swipe_session_started", "swipe_session_completed");

    public static boolean isClientEvent(String name) {
        return CLIENT_EVENTS.contains(name);
    }

    public static boolean isBackendOnly(String name) {
        return BACKEND_EVENTS.contains(name);
    }

    public static Set<String> clientEvents() {
        return CLIENT_EVENTS;
    }
}
