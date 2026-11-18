package com.team262.moviematch.controller;

import com.team262.moviematch.config.openapi.AnalyticsApiExamples;
import com.team262.moviematch.dto.error.ErrorResponse;
import com.team262.moviematch.service.analytics.AnalyticsIntakeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Product analytics event intake")
public class AnalyticsController {
    private final AnalyticsIntakeService intake;

    @PostMapping("/event")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "collectEvents", summary = "Collect analytics events",
            description = "Public endpoint: no CSRF token and no session are required, so fetch (keepalive) and "
                    + "navigator.sendBeacon both work. The body is {\"events\": [...]} (up to 50 events) or a single "
                    + "event object, sent as application/json or text/plain. Event: name (see the analytics "
                    + "specification, snake_case), occurredAt (ISO-8601), sessionId (UUID of the browser tab), "
                    + "optional eventId (UUID; a retried event with the same id is stored once), optional page "
                    + "(route template such as /room/:code) and optional params (JSON object, up to 2 KB). "
                    + "Answers 202 without a body. Invalid events are dropped and counted (metric "
                    + "analytics.events) while the valid ones of the same request are kept; the response is still 202. "
                    + "Only a malformed body is an error (400, 413). Never put names, emails, passwords, tokens or "
                    + "room codes in an event: such params keys are removed and room codes are hidden in page. "
                    + "The server adds the signed-in user (session cookie, send credentials) and, when the "
                    + "X-Participant-Token header is present, the participant and room; send that header only with "
                    + "events from room screens (flush the buffer before creating or joining a room). A clearly "
                    + "wrong client time (more than 5 minutes ahead or 7 days behind) is replaced by the server time. "
                    + "Events are kept for 400 days.")
    @ApiResponse(responseCode = "202", description = "Accepted", content = @Content)
    @ApiResponse(responseCode = "400", description = "INVALID_EVENT_BATCH: the body is empty, is not a JSON object, "
            + "has no 'events' array or name, or has more than 50 events",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AnalyticsApiExamples.INVALID_BATCH)))
    @ApiResponse(responseCode = "413", description = "The body is larger than 64 KB",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class),
                    examples = @ExampleObject(value = AnalyticsApiExamples.TOO_LARGE)))
    public void collect(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object"), examples = {
                        @ExampleObject(name = "Batch", value = AnalyticsApiExamples.BATCH_REQUEST),
                        @ExampleObject(name = "Single event", value = AnalyticsApiExamples.SINGLE_REQUEST)}))
            @RequestBody(required = false) String body,
            @Parameter(description = "Optional participant token (fetch only); adds participant and room to the events")
            @RequestHeader(value = "X-Participant-Token", required = false) String participantToken,
            Authentication authentication) {
        intake.collect(body, signedInUserId(authentication), participantToken);
    }

    /** The account session cookie is optional here: guests send events too. */
    private static Long signedInUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return Long.valueOf(authentication.getName());       // the session principal is the account id
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
