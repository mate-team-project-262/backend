# Random room decks

`RoomDeckService.prepare(code, participantId)` synchronously returns 20 local
`Title` IDs in room order. `getTitleIds(code, participantId)` reads that order
for any participant in the room. Callers must supply the participant ID from
validated authentication. This is an internal service: no HTTP endpoints or
public DTOs are added, and the room is not transitioned into voting here.

## Algorithm

1. Discover page 1 obtains the filtered result and page counts.
2. Choose four different pages at random from the first 50 available pages.
   Reuse the probe response when page 1 is selected. Small catalogs use fewer
   pages; no page number exceeds 500.
3. Shuffle page candidates and take roughly five per page, keeping a small
   reserve for rejected cards.
4. Fetch details one at a time on the calling thread. Check identity, media
   type, all requested genres and maximum runtime. An unknown runtime cannot
   satisfy an explicit duration limit. Room preferences currently provide no
   minimum vote count.
5. Replace unsuitable cards within the request budget. Shuffle the final 20
   cards once and atomically persist details and positions 0-19.

TMDB's popularity order determines page contents; randomly choosing pages and
candidates changes the composition of new decks. Sampling covers a limited
window and is not uniform across the whole TMDB catalog. There is no viewing
history, so different rooms can contain some of the same titles. The offline
stub uses the same algorithm over its small fixed fictional catalog.

## Simple synchronous execution

There are no application-created worker threads, executors, build queues,
futures or rate limiter. Different incoming HTTP requests may still execute
concurrently on Spring's request threads. A slow upstream call blocks its
caller until the HTTP timeout. Keep this tradeoff in mind before exposing
preparation at high traffic; per-build limits do not cap aggregate traffic.

| Setting | Default | Purpose |
| --- | --- | --- |
| `DECK_MAX_PAGE_WINDOW` | 50 | Random sampling window |
| `DECK_INITIAL_PAGE_COUNT` | 4 | Initial page diversity |
| `DECK_MAX_DISCOVER_REQUESTS` | 8 | Includes the metadata probe |
| `DECK_MAX_DETAILS_REQUESTS` | 30 | Includes rejected details |
| `DECK_BUILD_TIMEOUT` | 60s | Cooperative total build budget |
| `TMDB_MAX_RESPONSE_BYTES` | 4194304 | Maximum response body |

The deadline is checked before and after provider calls and during saving.
It does not interrupt a blocking HTTP call: the configured connection/read
limits (3s/5s by default) apply to that call. Once a late response returns, no
further selection or persistence proceeds. This is not a hard wall-clock SLA.

The selector processes pages incrementally. It retains a bounded set of seen
IDs, up to the details budget of queued candidate IDs, and 20 chosen cards.
It never loads the local catalog for shuffling and uses no `ORDER BY RAND()`.
HTTP bodies are bounded, but total JVM usage also depends on the number of
concurrent incoming requests and the rest of the application.

## Persistence and retries

Only the host can prepare an empty deck, and only in the lobby. Once a complete
deck exists, preparation reuses its stored order without new TMDB calls.
Every participant reads the same positions, including after a restart.

A short transaction locks the room and records a build token and lease.
Another request for that room receives `DECK_BUILD_IN_PROGRESS`; it does not
start another provider search or wait on an in-memory future. The lease
includes five seconds of cleanup grace. An expired lease can be reclaimed,
and the old attempt cannot overwrite or clear a newer attempt.

Provider calls happen outside database transactions. Final saving uses a
short independent `READ_COMMITTED` transaction. It validates the attempt,
room status and unchanged filters, then locks shared titles in a consistent
identity order and upserts on `(tmdb_id, media_type)`. Collections and all
scalar details are updated before inserting 20 room positions. Two rooms
reuse the same title row; later builds may refresh its shared metadata.
Any failure rolls back the entire final save and marks the attempt failed.

Migration `004` adds the durable build state, backfills complete decks as
ready, and widens `titles.director` to `TEXT` for long TV credit lists.
Rolling that column back to `VARCHAR(512)` requires existing values to fit.

Migration `005` adds `title_genres.genre_name` without changing the unique
`(title_id, genre_id)` identity. `Title.genres` stores `StoredGenre(id, name)`
snapshots, so ID and name can be read from the database without TMDB calls.
New details preserve the provider's localized genre names; missing names stay
null. Existing known movie/TV genre IDs receive canonical en-US names verified
against TMDB on 2026-11-02. Unknown legacy IDs are kept with a null name.
The migration contains no network calls. Rolling it back preserves genre IDs
but removes the newly stored names. Room filter preferences still store IDs.

## Errors and verification

* `502 TMDB_UNAVAILABLE`: upstream transport, response or payload failure.
* `503 DECK_BUILD_TIMEOUT`: the build budget expired.
* `503 DECK_SELECTION_LIMIT`: the bounded search could not find 20 valid cards.
  This does not imply the entire catalog has fewer than 20 matches.
* `409 NOT_ENOUGH_MOVIES`: Discover reports fewer than 20 filtered results.
* `409 DECK_BUILD_IN_PROGRESS`: another request holds the room's build lease.
* `409 DECK_BUILD_STALE`: the attempt, room status or filters changed.
* `409 RESULT_NOT_READY`: a participant reads a deck before it is ready.

No partial deck or automatic fallback to stub is returned. Failures may be
retried; abandoned attempts can be reclaimed after their lease expires.

Run `mvn verify` with Docker for unit and MySQL integration tests. Set
`TMDB_LIVE_TESTS=true` to enable live source checks and additionally
`DECK_LIVE_TESTS=true` for real random movie and TV deck checks. Provide
`TMDB_API_KEY` through the environment; never commit credentials. Live tests
consume upstream requests and depend on the current catalog and network.
