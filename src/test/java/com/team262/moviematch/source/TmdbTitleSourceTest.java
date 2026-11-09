package com.team262.moviematch.source;

import com.team262.moviematch.client.TmdbClient;
import com.team262.moviematch.config.tmdb.TmdbConfiguration;
import com.team262.moviematch.config.tmdb.TmdbProperties;
import com.team262.moviematch.exception.ErrorCode;
import com.team262.moviematch.exception.ex.ExternalServiceException;
import com.team262.moviematch.model.ContentType;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmdbTitleSourceTest {
    private MockRestServiceServer server;
    private TmdbTitleSource source;

    @BeforeEach
    void setUp() {
        TmdbProperties properties = new TmdbProperties("https://tmdb.example/3", "test-read-token");
        RestClient.Builder builder = new TmdbConfiguration().restClient(properties).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new TmdbTitleSource(new TmdbClient(builder.build()), properties);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void discoverUsesCorrectEndpointFiltersAndContentSpecificFields(ContentType type) {
        boolean movie = type == ContentType.MOVIES;
        String path = movie ? "movie" : "tv";
        String nameField = movie ? "title" : "name";
        String dateField = movie ? "release_date" : "first_air_date";
        server.expect(requestTo(startsWith("https://tmdb.example/3/discover/" + path + "?")))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-read-token"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(queryParam("language", "en-US"))
                .andExpect(queryParam("page", "2"))
                .andExpect(queryParam("sort_by", "popularity.desc"))
                .andExpect(queryParam("include_adult", "false"))
                .andExpect(queryParam("with_genres", "18%7C35"))
                .andExpect(queryParam("with_runtime.lte", "120"))
                .andExpect(queryParam("vote_count.gte", "100"))
                .andRespond(withSuccess("""
                        {"page":2,"total_pages":3,"total_results":41,"results":[{"id":42,"%s":"Candidate",
                        "%s":"2021-04-01","overview":"Story","poster_path":"/image.jpg",
                        "popularity":42.125,"ignored":"extra"}]}
                        """.formatted(nameField, dateField), APPLICATION_JSON));
        DiscoverPage result = source.discover(type, new DiscoverFilters(Set.of(35, 18), 120, 100), 2);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.totalResults()).isEqualTo(41);
        assertThat(result.items())
                .containsExactly(new TitleCandidate(42L, movie ? ContentType.MOVIES : ContentType.TV_SERIES,
                        "Candidate", 2021, "Story", "/image.jpg", new java.math.BigDecimal("42.125")));
    }

    @Test
    void omittedFiltersAreNotSentAndEmptyResultsAreValid() {
        server.expect(requestTo(startsWith("https://tmdb.example/3/discover/movie?")))
                .andExpect(request -> assertThat(request.getURI().getQuery())
                        .doesNotContain("with_genres", "with_runtime.lte", "vote_count.gte"))
                .andRespond(withSuccess(
                        "{\"page\":500,\"total_pages\":0,\"total_results\":0,\"results\":[]}",
                        APPLICATION_JSON));
        assertThat(source.discover(ContentType.MOVIES, null, 500).items()).isEmpty();
    }

    @Test
    void movieDetailsIncludeCreditsCertificationAndEveryCardField() throws IOException {
        server.expect(requestTo(startsWith("https://tmdb.example/3/movie/550?")))
                .andExpect(queryParam("append_to_response", "credits,release_dates"))
                .andRespond(withSuccess(fixture("movie-details.json"), APPLICATION_JSON));
        TmdbTitle title = source.getDetails(ContentType.MOVIES, 550);
        assertThat(title.tmdbId()).isEqualTo(550L);
        assertThat(title.mediaType()).isEqualTo(ContentType.MOVIES);
        assertThat(title.title()).isEqualTo("Example Movie");
        assertThat(title.releaseYear()).isEqualTo(2024);
        assertThat(title.overview()).isEqualTo("A complete movie overview.");
        assertThat(title.posterPath()).isEqualTo("/movie.jpg");
        assertThat(title.popularity()).isEqualByComparingTo("123.456");
        assertThat(title.voteAverage()).isEqualByComparingTo("8.25");
        assertThat(title.runtimeMinutes()).isEqualTo(123);
        assertThat(title.genres()).containsExactly(new TitleGenre(18, "Drama"), new TitleGenre(35, "Comedy"));
        assertThat(title.director()).isEqualTo("A Director, Another Director");
        assertThat(title.creators()).isEmpty();
        assertThat(title.cast()).containsExactly("First Actor", "Second Actor", "Unknown Order");
        assertThat(title.ageRating()).isEqualTo("PG-13");
        assertThat(title.budget()).isEqualTo(3000000000L);
        assertThat(title.revenue()).isEqualTo(6000000000L);
        assertThat(title.originalLanguage()).isEqualTo("fr");
    }

    @Test
    void tvDetailsUseEpisodeRuntimeCreatorsAggregateCreditsAndContentRatings() throws IOException {
        server.expect(requestTo(startsWith("https://tmdb.example/3/tv/1396?")))
                .andExpect(queryParam("append_to_response", "aggregate_credits,content_ratings"))
                .andRespond(withSuccess(fixture("tv-details.json"), APPLICATION_JSON));
        TmdbTitle title = source.getDetails(ContentType.TV_SERIES, 1396);
        assertThat(title.tmdbId()).isEqualTo(1396L);
        assertThat(title.mediaType()).isEqualTo(ContentType.TV_SERIES);
        assertThat(title.title()).isEqualTo("Example Series");
        assertThat(title.releaseYear()).isEqualTo(2022);
        assertThat(title.overview()).isEqualTo("A complete TV overview.");
        assertThat(title.posterPath()).isEqualTo("/tv.jpg");
        assertThat(title.popularity()).isEqualByComparingTo("98.125");
        assertThat(title.voteAverage()).isEqualByComparingTo("9.1");
        assertThat(title.runtimeMinutes()).isEqualTo(47);
        assertThat(title.genres()).containsExactly(new TitleGenre(18, "Drama"),
                new TitleGenre(10759, "Action & Adventure"));
        assertThat(title.director()).isEqualTo("Series Director");
        assertThat(title.creators()).containsExactly("First Creator", "Second Creator");
        assertThat(title.cast()).containsExactly("Series Actor A", "Series Actor B");
        assertThat(title.ageRating()).isEqualTo("TV-MA");
        assertThat(title.budget()).isNull();
        assertThat(title.revenue()).isNull();
        assertThat(title.originalLanguage()).isEqualTo("en");
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void missingAndEmptyFieldsRemainUnknown(ContentType type) {
        expectDetails("{\"id\":42,\"budget\":0,\"revenue\":0,\"overview\":\"\","
                + "\"release_date\":\"invalid\",\"first_air_date\":\"\",\"runtime\":0}");
        TmdbTitle title = source.getDetails(type, 42);
        assertThat(title.title()).isNull();
        assertThat(title.releaseYear()).isNull();
        assertThat(title.overview()).isNull();
        assertThat(title.posterPath()).isNull();
        assertThat(title.popularity()).isNull();
        assertThat(title.voteAverage()).isNull();
        assertThat(title.runtimeMinutes()).isNull();
        assertThat(title.genres()).isEmpty();
        assertThat(title.director()).isNull();
        assertThat(title.creators()).isEmpty();
        assertThat(title.cast()).isEmpty();
        assertThat(title.ageRating()).isNull();
        assertThat(title.budget()).isNull();
        assertThat(title.revenue()).isNull();
        assertThat(title.originalLanguage()).isNull();
    }

    @Test
    void tvRuntimeCanFallBackToLastEpisodeWhenTypicalRuntimeIsMissing() {
        expectDetails("{\"id\":42,\"episode_run_time\":[],\"last_episode_to_air\":{\"runtime\":51}}");
        assertThat(source.getDetails(ContentType.TV_SERIES, 42).runtimeMinutes()).isEqualTo(51);
    }

    @Test
    void ratingDoesNotSilentlyUseAnotherCountrysSystem() {
        expectDetails("""
                {"id":42,"content_ratings":{"results":[{"iso_3166_1":"GB","rating":"15"}]}}
                """);
        assertThat(source.getDetails(ContentType.TV_SERIES, 42).ageRating()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 429, 500, 503})
    void upstreamHttpErrorsBecomeConsistentUnavailableErrors(int status) {
        server.expect(requestTo(startsWith("https://tmdb.example/3/")))
                .andRespond(withStatus(HttpStatusCode.valueOf(status))
                        .body("private provider details").contentType(APPLICATION_JSON));
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, 42))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("TMDB is unavailable");
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void connectionAndReadErrorsAreTranslatedForDiscover(ContentType type) {
        server.expect(requestTo(startsWith("https://tmdb.example/3/discover/")))
                .andRespond(request -> { throw new SocketTimeoutException("upstream secret"); });
        assertThatThrownBy(() -> source.discover(type, null, 1))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("TMDB is unavailable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{invalid", "{\"success\":false}",
            "{}", "{\"id\":43}", "{\"id\":42,\"runtime\":\"bad\"}",
            "{\"id\":42,\"genres\":{}}", "{\"id\":42,\"runtime\":9999999999}"})
    void invalidOrUnexpectedResponsesAreNotTreatedAsCardDetails(String body) {
        expectDetails(body);
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, 42))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void missingDiscoverResultsAreAnUpstreamError() {
        server.expect(requestTo(startsWith("https://tmdb.example/3/discover/")))
                .andRespond(withSuccess("{}", APPLICATION_JSON));
        assertThatThrownBy(() -> source.discover(ContentType.MOVIES, null, 1))
                .isInstanceOf(ExternalServiceException.class)
                .extracting("code").isEqualTo(ErrorCode.TMDB_UNAVAILABLE);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void errorsInAppendedCreditsAndRatingsAreNotTreatedAsMissingDetails(ContentType type) {
        String credits = type == ContentType.MOVIES ? "credits" : "aggregate_credits";
        expectDetails("{\"id\":42,\"" + credits + "\":{\"success\":false,\"status_code\":9}}");
        assertThatThrownBy(() -> source.getDetails(type, 42))
                .isInstanceOf(ExternalServiceException.class);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void errorsInAppendedRatingsBecomeUnavailable(ContentType type) {
        String ratings = type == ContentType.MOVIES ? "release_dates" : "content_ratings";
        expectDetails("{\"id\":42,\"" + ratings + "\":{\"success\":false}}");
        assertThatThrownBy(() -> source.getDetails(type, 42))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void connectionFailureIsTranslatedForDetails() {
        server.expect(requestTo(startsWith("https://tmdb.example/3/movie/")))
                .andRespond(request -> { throw new ConnectException("connection refused"); });
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, 42))
                .isInstanceOf(ExternalServiceException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void configurableLanguageAndCertificationCountryAreUsed() throws IOException {
        TmdbProperties properties = new TmdbProperties("https://tmdb.example/3", "token",
                null, null, "uk-UA", "GB");
        RestClient.Builder builder = new TmdbConfiguration().restClient(properties).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        source = new TmdbTitleSource(new TmdbClient(builder.build()), properties);
        server.expect(requestTo(startsWith("https://tmdb.example/3/movie/550?")))
                .andExpect(queryParam("language", "uk-UA"))
                .andRespond(withSuccess(fixture("movie-details.json"), APPLICATION_JSON));
        assertThat(source.getDetails(ContentType.MOVIES, 550).ageRating()).isEqualTo("15");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 501})
    void invalidPageDoesNotCallTmdb(int page) {
        assertThatThrownBy(() -> source.discover(ContentType.MOVIES, null, page))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidIdDoesNotCallTmdb() {
        assertThatThrownBy(() -> source.getDetails(ContentType.MOVIES, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void expectDetails(String body) {
        server.expect(requestTo(startsWith("https://tmdb.example/3/")))
                .andRespond(withSuccess(body, APPLICATION_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"page\":1,\"results\":[]}",
            "{\"page\":2,\"total_pages\":2,\"total_results\":30,\"results\":[]}",
            "{\"page\":1,\"total_pages\":-1,\"total_results\":0,\"results\":[]}",
            "{\"page\":1,\"total_pages\":1,\"total_results\":-1,\"results\":[]}",
            "{\"page\":1,\"total_pages\":\"bad\",\"total_results\":0,\"results\":[]}"
    })
    void invalidPaginationIsAnUpstreamFailure(String body) {
        server.expect(requestTo(startsWith("https://tmdb.example/3/discover/")))
                .andRespond(withSuccess(body, APPLICATION_JSON));
        assertThatThrownBy(() -> source.discover(ContentType.MOVIES, null, 1))
                .isInstanceOf(ExternalServiceException.class);
    }

    private String fixture(String name) throws IOException {
        return new ClassPathResource("tmdb/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
