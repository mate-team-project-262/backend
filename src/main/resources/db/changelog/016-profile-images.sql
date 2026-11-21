--liquibase formatted sql
--changeset team262:016-profile-images
ALTER TABLE users ADD COLUMN avatar_version BIGINT NULL;

CREATE TABLE user_avatars (
    user_id BIGINT NOT NULL PRIMARY KEY,
    content_type VARCHAR(32) NOT NULL,
    image_bytes LONGBLOB NOT NULL,
    CONSTRAINT fk_user_avatars_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE title_cast_photos (
    title_id BIGINT NOT NULL,
    position INT NOT NULL,
    person_id BIGINT NULL,
    profile_path VARCHAR(512) NULL,
    CONSTRAINT pk_title_cast_photos PRIMARY KEY (title_id, position),
    CONSTRAINT fk_title_cast_photos_title FOREIGN KEY (title_id) REFERENCES titles(id) ON DELETE CASCADE
);

--rollback DROP TABLE title_cast_photos;
--rollback DROP TABLE user_avatars;
--rollback ALTER TABLE users DROP COLUMN avatar_version;
