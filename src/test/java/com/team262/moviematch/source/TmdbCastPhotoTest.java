package com.team262.moviematch.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team262.moviematch.model.ContentType;
import org.junit.jupiter.api.Test;

class TmdbCastPhotoTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsActorIdentityAndOptionalPhotoFromCredits() throws Exception {
        TmdbTitle title = new TmdbTitleMapper("US").details(json.readTree("""
                {"id": 42, "title": "Film", "credits": {"cast": [
                  {"id": 7, "name": "Actor", "profile_path": "/actor.jpg", "order": 0},
                  {"id": 8, "name": "Other", "profile_path": null, "order": 1}
                ], "crew": []}, "release_dates": {"results": []}}
                """), ContentType.MOVIES);

        assertThat(title.cast()).containsExactly("Actor", "Other");
        assertThat(title.castPhotos()).containsExactly(
                new CastPhoto(7L, "/actor.jpg"), new CastPhoto(8L, null));
    }
}
