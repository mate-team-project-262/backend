# Title sources (S2-1)

`TitleSource` is an internal backend interface. It does not expose public API DTOs,
generate a room deck or save catalog rows. The deck service can inject it and use
S2-2 repositories to persist selected cards.

```java
List<TitleCandidate> candidates = titleSource.discover(
        ContentType.MOVIES, new DiscoverFilters(Set.of(18), 120, 100), 1).items();
TmdbTitle details = titleSource.getDetails(ContentType.MOVIES, candidates.getFirst().tmdbId());
```

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `TITLE_SOURCE` | `stub` | Explicitly choose `stub` or `tmdb`; invalid modes fail startup |
| `TMDB_API_KEY` | empty | TMDB **API Read Access Token**, without the `Bearer ` prefix |
| `TMDB_BASE_URL` | `https://api.themoviedb.org/3` | Base API URL |
| `TMDB_CONNECT_TIMEOUT` | `3s` | Connection timeout |
| `TMDB_READ_TIMEOUT` | `5s` | Socket read timeout |
| `TMDB_LANGUAGE` | `en-US` | Requested language for names and descriptions |
| `TMDB_CERTIFICATION_COUNTRY` | `US` | Two-letter country used to select age ratings |

Set the variables in the IDE or shell when running Java directly. Docker Compose
passes them from the shell or local `.env` to the backend container. Spring Boot
does not automatically read `.env`. The template contains no real token.

In `tmdb` mode a blank token or invalid base URL fails startup. A real API failure
never switches the selected source to stub. Both sources are thread-safe; the
Spring context exposes exactly one `TitleSource`.

## Discover

- `MOVIES` uses `/discover/movie`; `TV_SERIES` uses `/discover/tv`.
- `genreIds` becomes sorted, comma-separated `with_genres`: all selected genres
  must match (TMDB AND semantics). Null or empty genres impose no genre filter.
- `maxDurationMinutes` becomes `with_runtime.lte`; for TV it filters episode length.
- `minVoteCount` becomes `vote_count.gte`; null omits the filter and zero is valid.
- Pages are 1-based, from 1 through 500. Results preserve the provider's
  `popularity.desc` order. An empty provider page returns an empty list.
- Discover returns `DiscoverPage(items, page, totalPages, totalResults)`.
  Missing/inconsistent pagination and pages containing more than 20 candidates
  are rejected as invalid provider responses. Stub supplies the same metadata.
- Adult titles are excluded. IDs are positive `Long` values.
- Internal invalid arguments fail before making a provider request. These are not
  public request-validation DTOs.

## Details and unknown values

Movie details append `credits,release_dates`. TV details append
`aggregate_credits,content_ratings`, so TV cast and crew cover the entire show.
`MOVIES` maps to `MediaType.MOVIE`; `TV_SERIES` maps to `MediaType.TV`, matching
the shared S2-2 catalog identity.

| Field | Movie | TV |
| --- | --- | --- |
| `title` | `title` | `name` |
| `releaseYear` | `release_date` year | `first_air_date` year |
| `runtimeMinutes` | Positive `runtime` | First positive `episode_run_time`; otherwise positive `last_episode_to_air.runtime` |
| `director` | Crew job `Director` | Aggregate crew job `Director` |
| `creators` | Empty list | `created_by` names in provider order |
| `cast` | Credit names sorted by billing order | Aggregate credit names sorted by billing order |
| `ageRating` | Certification for configured country | Content rating for configured country |
| `budget`, `revenue` | Positive provider values | Usually absent, so null |

Multiple directors are joined with `, `. Genres retain ID/name pairs, and returned
lists are immutable. For movie certifications, nonblank theatrical entries take
priority over limited theatrical, digital and other entries. No rating from a
different country is substituted: an unavailable country-specific rating is null.

Absent or blank text fields and missing scalar values remain null; missing or
null collections become empty lists. Unknown or invalid dates yield a null year.
Zero budget/revenue and nonpositive runtimes mean unknown. Unprovided data is
never replaced with a fabricated rating, duration, title or score. Poster paths
remain provider-relative paths for a future API/image resolver.

## Failures

HTTP errors (including 401, 404, 429 and 5xx), connection/read failures, invalid JSON,
missing discover results, mismatched detail identities and explicitly failed
appended responses become `ExternalServiceException`. The existing global error
handler returns HTTP **502** with this existing error shape:

```json
{"code":"TMDB_UNAVAILABLE","message":"TMDB is unavailable","fieldErrors":[]}
```

No upstream response body, token or exception text is included in the HTTP response.
HTTP bodies are bounded by `TMDB_MAX_RESPONSE_BYTES` (4 MiB by default), including
responses without Content-Length. Oversized responses are rejected before JSON
mapping. No automatic retries are performed. Missing optional metadata is valid, rather than
an external failure. Invalid shapes for known fields are treated as provider failures.

## Offline demo

`StubTitleSource` requires no TMDB token, network or database. It provides
**24 fictional movies and 24 fictional series**, including all card/detail fields,
cast, creators, named genres and locally served SVG demo posters. Discover returns
20 entries on the first page and 4 on the second page before filters. Filtering
happens before pagination and follows the same genre/runtime/vote-count semantics
as the real source. IDs, order and detail responses are deterministic across instances.

Synthetic movie IDs are `9000001–9000024`; series IDs are `9100001–9100024`.
They are demo identifiers, not real TMDB titles. Use a demo database when persisting
stub cards; clear demo data before changing that database to the real source.
Unknown detail IDs fail with the same unavailable exception as provider failures.
The two generic poster assets are under `/demo/poster-movie.svg` and
`/demo/poster-tv.svg`; they do not require an external image service.

The source is consumed by the internal [room deck service](room-decks.md).
Public deck endpoints and frontend screens remain separate integration tasks.

## Tests

Run the source/configuration/error tests without a key, database or Docker:

```shell
mvn "-Dtest=TmdbTitleSourceTest,StubTitleSourceTest,TitleSourceConfigurationTest,TmdbTimeoutTest,ExternalServiceErrorHttpTest" test
```

HTTP contract tests use synthetic Movie/TV JSON fixtures and Spring's mock HTTP
transport. Timeout tests use a delayed local loopback HTTP server with the actual
configured request factory. Run `mvn verify` with Docker for all existing MySQL
persistence, room API and OpenAPI regression tests as well. Live TMDB tests are
opt-in and do not run during the default build. To verify real Movie/TV Discover
and details, set `TMDB_LIVE_TESTS=true` and `TMDB_API_KEY` in the current process,
then run `mvn -Dtest=TmdbIntegrationTest test`. The token must be a TMDB API Read
Access Token and is sent only as an Authorization header to the official API.
The live tests report card counts and IDs, without printing credentials.
Optionally set `TMDB_LIVE_OUTPUT_DIR=target/tmdb-live-demo` to export the internal
candidate lists and complete detail models as `movies.json` and `tv_series.json`.
These snapshots contain no credentials and stay under the ignored build directory.

## Official TMDB contract references

- [Discover Movie](https://developer.themoviedb.org/reference/discover-movie)
  and [Discover TV](https://developer.themoviedb.org/reference/discover-tv)
- [Movie details](https://developer.themoviedb.org/reference/movie-details)
  and [TV details](https://developer.themoviedb.org/reference/tv-series-details)
- [Append to response](https://developer.themoviedb.org/docs/append-to-response)
- [Movie release dates and certificates](https://developer.themoviedb.org/reference/movie-release-dates),
  [TV content ratings](https://developer.themoviedb.org/reference/tv-series-content-ratings)
  and [TV aggregate credits](https://developer.themoviedb.org/reference/tv-series-aggregate-credits)
- [Application authentication](https://developer.themoviedb.org/docs/authentication-application)
