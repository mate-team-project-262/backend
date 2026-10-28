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
                                API первого спринта Movie Match.

                                1. Создайте комнату через POST /rooms или войдите через POST /rooms/{code}/join.
                                2. Сохраните токен из participant.token (создание) или currentParticipant.token (вход).
                                3. Для GET /rooms/{code} передавайте его в заголовке X-Participant-Token.
                                В Swagger нажмите Authorize и вставьте только токен, без префикса Bearer.
                                Токен действует 24 часа с момента выдачи.
                                4. Обновляйте лобби через GET, например раз в 3 секунды.

                                Просмотр лобби до создания комнаты или присоединения не предусмотрен.
                                roomId и чужие токены не возвращаются. Коды комнаты регистронезависимы.

                                Ошибки бизнес-логики и Bean Validation имеют поля code, message, fieldErrors.
                                Для логики интерфейса используйте HTTP-статус и code; текст message может меняться.
                                Ошибки HTTP/JSON и неожиданные ошибки сервера используют стандартный формат Spring,
                                без гарантированного поля code. Не повторяйте POST автоматически после сетевой ошибки:
                                запрос мог уже успешно создать комнату или участника.
                                """))
                .components(new Components().addSecuritySchemes(PARTICIPANT_TOKEN,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(CurrentParticipantArgumentResolver.HEADER)
                                .description("Личный токен участника. Вставьте UUID без Bearer; нужен только для GET лобби.")));
    }
}
