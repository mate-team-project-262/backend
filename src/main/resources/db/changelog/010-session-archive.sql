--liquibase formatted sql
--changeset team262:010-session-archive
CREATE TABLE game_session_archives (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_room_id BIGINT NOT NULL,
    room_code VARCHAR(6) NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NOT NULL,
    participant_count INT NOT NULL,
    CONSTRAINT uk_archive_source_room UNIQUE (source_room_id)
);
CREATE TABLE archived_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    source_participant_id BIGINT NOT NULL,
    display_name VARCHAR(20) NOT NULL,
    was_host BIT(1) NOT NULL,
    CONSTRAINT uk_archived_participant_source UNIQUE (session_id, source_participant_id),
    CONSTRAINT fk_archived_participant_session FOREIGN KEY (session_id)
        REFERENCES game_session_archives(id) ON DELETE CASCADE
);
CREATE TABLE archived_cards (
    session_id BIGINT NOT NULL,
    source_card_id BIGINT NOT NULL,
    title_id BIGINT NOT NULL,
    position INT NOT NULL,
    likes_count INT NOT NULL,
    winner BIT(1) NOT NULL,
    PRIMARY KEY (session_id, source_card_id),
    CONSTRAINT uk_archived_card_position UNIQUE (session_id, position),
    CONSTRAINT uk_archived_card_title UNIQUE (session_id, title_id),
    CONSTRAINT fk_archived_card_session FOREIGN KEY (session_id)
        REFERENCES game_session_archives(id) ON DELETE CASCADE,
    CONSTRAINT fk_archived_card_title FOREIGN KEY (title_id) REFERENCES titles(id)
);
--rollback DROP TABLE archived_cards;
--rollback DROP TABLE archived_participants;
--rollback DROP TABLE game_session_archives;

