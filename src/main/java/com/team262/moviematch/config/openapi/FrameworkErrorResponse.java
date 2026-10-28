package com.team262.moviematch.config.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

/** Documentation of Spring Boot's default error body, not a new application response DTO. */
@Schema(description = "Стандартная ошибка Spring для некорректного JSON/HTTP и технических сбоев. "
        + "Поле code отсутствует; набор дополнительных полей зависит от настроек сервера.")
public record FrameworkErrorResponse(
        @Schema(description = "Время ошибки", format = "date-time") String timestamp,
        @Schema(description = "HTTP-статус", example = "400") int status,
        @Schema(description = "Название HTTP-ошибки", example = "Bad Request") String error,
        @Schema(description = "Путь запроса", example = "/rooms") String path) {
}
