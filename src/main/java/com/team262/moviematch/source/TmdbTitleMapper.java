package com.team262.moviematch.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.team262.moviematch.model.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Converts provider JSON to internal models, without inventing missing catalog data. */
final class TmdbTitleMapper {
    private final String certificationCountry;

    TmdbTitleMapper(String certificationCountry) {
        this.certificationCountry = certificationCountry;
    }

    DiscoverPage page(JsonNode response, ContentType type, int requestedPage) {
        Integer page = integer(response, "page");
        Integer totalPages = integer(response, "total_pages");
        Long totalResults = number(response, "total_results");
        if (page == null || page != requestedPage || totalPages == null || totalResults == null) {
            throw new IllegalArgumentException("Missing or inconsistent discover pagination");
        }
        return new DiscoverPage(candidates(response, type), page, totalPages, totalResults);
    }

    private List<TitleCandidate> candidates(JsonNode response, ContentType type) {
        if (!response.path("results").isArray()) {
            throw new IllegalArgumentException("Missing discover results");
        }
        return array(response.get("results")).stream().map(node -> new TitleCandidate(
                requiredId(node), type, text(node, type == ContentType.MOVIES ? "title" : "name"),
                year(node, type), text(node, "overview"), text(node, "poster_path"),
                decimal(node, "popularity"))).toList();
    }

    TmdbTitle details(JsonNode node, ContentType type) {
        boolean movie = type == ContentType.MOVIES;
        JsonNode credits = node.path(movie ? "credits" : "aggregate_credits");
        checkAppendedResponse(credits);
        List<TitleGenre> genres = array(node.path("genres")).stream()
                .filter(genre -> number(genre, "id") != null)
                .map(genre -> new TitleGenre(integer(genre, "id"), text(genre, "name"))).toList();
        List<String> creators = movie ? List.of() : names(array(node.path("created_by")));
        List<String> cast = names(array(credits.path("cast")).stream()
                .sorted(Comparator.comparingInt(person -> {
                    Integer order = integer(person, "order");
                    return order == null ? Integer.MAX_VALUE : order;
                })).toList());
        String director = array(credits.path("crew")).stream()
                .filter(person -> movie ? "Director".equals(text(person, "job"))
                        : array(person.path("jobs")).stream()
                                .anyMatch(job -> "Director".equals(text(job, "job"))))
                .map(person -> text(person, "name")).filter(Objects::nonNull).distinct()
                .collect(Collectors.joining(", "));
        return new TmdbTitle(requiredId(node), type, text(node, movie ? "title" : "name"),
                year(node, type), text(node, "overview"), text(node, "poster_path"),
                decimal(node, "popularity"), decimal(node, "vote_average"),
                movie ? positive(integer(node, "runtime")) : episodeRuntime(node), genres,
                director.isEmpty() ? null : director, creators, cast, certification(node, movie),
                positive(number(node, "budget")), positive(number(node, "revenue")),
                text(node, "original_language"));
    }

    private Integer episodeRuntime(JsonNode node) {
        for (JsonNode runtime : array(node.path("episode_run_time"))) {
            if (!runtime.isIntegralNumber() || !runtime.canConvertToInt()) {
                throw new IllegalArgumentException("Invalid episode runtime");
            }
            if (runtime.intValue() > 0) {
                return runtime.intValue();
            }
        }
        return positive(integer(node.path("last_episode_to_air"), "runtime"));
    }

    private String certification(JsonNode node, boolean movie) {
        JsonNode ratings = node.path(movie ? "release_dates" : "content_ratings");
        checkAppendedResponse(ratings);
        List<JsonNode> countries = array(ratings.path("results"));
        for (JsonNode country : countries) {
            if (!certificationCountry.equals(text(country, "iso_3166_1"))) {
                continue;
            }
            if (!movie) {
                String rating = text(country, "rating");
                if (rating != null) {
                    return rating;
                }
            } else {
                String rating = array(country.path("release_dates")).stream()
                        .sorted(Comparator.comparingInt(release -> releasePriority(integer(release, "type"))))
                        .map(release -> text(release, "certification"))
                        .filter(Objects::nonNull).findFirst().orElse(null);
                if (rating != null) {
                    return rating;
                }
            }
        }
        return null;
    }

    private void checkAppendedResponse(JsonNode node) {
        if (node.has("success") && !node.path("success").asBoolean()) {
            throw new IllegalArgumentException("Failed TMDB appended response");
        }
        if (!node.isMissingNode() && !node.isNull() && !node.isObject()) {
            throw new IllegalArgumentException("Expected TMDB appended object");
        }
    }

    private int releasePriority(Integer type) {
        if (type == null) {
            return 10;
        }
        return switch (type) {
            case 3 -> 0; // theatrical
            case 2 -> 1; // limited theatrical
            case 4 -> 2; // digital
            default -> 3;
        };
    }

    private List<String> names(List<JsonNode> people) {
        return people.stream().map(person -> text(person, "name"))
                .filter(Objects::nonNull).toList();
    }

    private Integer year(JsonNode node, ContentType type) {
        String date = text(node, type == ContentType.MOVIES ? "release_date" : "first_air_date");
        if (date == null) {
            return null;
        }
        try {
            return LocalDate.parse(date).getYear();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Long requiredId(JsonNode node) {
        Long id = number(node, "id");
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Missing TMDB identity");
        }
        return id;
    }

    private List<JsonNode> array(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("Expected TMDB collection");
        }
        return StreamSupport.stream(value.spliterator(), false).toList();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Expected TMDB text field");
        }
        return value.textValue().isBlank() ? null : value.textValue();
    }

    private Long number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new IllegalArgumentException("Expected TMDB integer field");
        }
        return value.longValue();
    }

    private Integer integer(JsonNode node, String field) {
        Long value = number(node, field);
        if (value != null && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("TMDB integer field is out of range");
        }
        return value == null ? null : value.intValue();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (!value.isNumber()) {
            throw new IllegalArgumentException("Expected TMDB numeric field");
        }
        return value.decimalValue();
    }

    private Long positive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private Integer positive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }
}
