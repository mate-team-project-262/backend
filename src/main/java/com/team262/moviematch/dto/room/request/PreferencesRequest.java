package com.team262.moviematch.dto.room.request;

import com.team262.moviematch.model.ContentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreferencesRequest(
        @Schema(description = "Тип контента; обязателен, если объект preferences передан", example = "MOVIES",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ContentType contentType,
        @Schema(description = "ID жанров внешнего каталога. Отсутствие, null или [] означают любые жанры. "
                + "Дубли удаляются, элементы null запрещены; порядок не важен.", example = "[28,35]", nullable = true)
        Set<@NotNull Integer> genreIds,
        @Schema(description = "Максимальная длительность: любое целое от 30 до 150 включительно; "
                + "отсутствие или null снимает ограничение. Кнопки 90/120/150 — варианты интерфейса, не enum API.",
                example = "120", nullable = true)
        @Min(30) @Max(150) Integer maxDurationMinutes
) {
}
