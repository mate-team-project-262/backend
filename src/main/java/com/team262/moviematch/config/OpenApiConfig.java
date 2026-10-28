package com.team262.moviematch.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import com.team262.moviematch.security.CurrentParticipantArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    public static final String PARTICIPANT_TOKEN = "ParticipantToken";

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("Movie Match API")
                        .version("1.0.0")
                        .description("""
                                Movie Match Sprint 1 API.

                                1. Create a room with POST /rooms or join one with POST /rooms/{code}/join.
                                2. Save participant.token from creation or currentParticipant.token from joining.
                                3. Send this token in the X-Participant-Token header for GET /rooms/{code}.
                                In Swagger, click Authorize and paste the raw token without the Bearer prefix.
                                Tokens are valid for 24 hours after issuance.
                                4. Refresh the lobby with GET, for example every 3 seconds.

                                Public lobby access before creating or joining a room is not supported.
                                Room IDs and other participants' tokens are never returned. Room codes are case-insensitive.

                                Business and Bean Validation errors contain code, message and fieldErrors.
                                Handle errors using the HTTP status and code; message text may change.
                                HTTP/JSON errors and unexpected server failures use Spring's default error format
                                without a guaranteed code field. Do not automatically retry POST after a network error:
                                the request may already have created the room or participant.
                                """))
                .components(new Components().addSecuritySchemes(PARTICIPANT_TOKEN,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CurrentParticipantArgumentResolver.HEADER)
                                .description("Personal participant token. Paste the UUID without Bearer; required only for GET lobby.")));
    }
}
