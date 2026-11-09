--liquibase formatted sql
--changeset team262:012-archive-votes
ALTER TABLE archived_participants ADD COLUMN user_id BIGINT NULL;
CREATE INDEX idx_archived_participant_user ON archived_participants(user_id, session_id);
ALTER TABLE archived_participants ADD CONSTRAINT fk_archived_participant_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
CREATE TABLE archived_votes (
    session_id BIGINT NOT NULL,
    source_participant_id BIGINT NOT NULL,
    source_card_id BIGINT NOT NULL,
    liked BIT(1) NOT NULL,
    PRIMARY KEY (session_id, source_participant_id, source_card_id),
    CONSTRAINT fk_archived_vote_participant FOREIGN KEY (session_id, source_participant_id)
        REFERENCES archived_participants(session_id, source_participant_id) ON DELETE CASCADE,
    CONSTRAINT fk_archived_vote_card FOREIGN KEY (session_id, source_card_id)
        REFERENCES archived_cards(session_id, source_card_id) ON DELETE CASCADE
);
--rollback DROP TABLE archived_votes;
--rollback ALTER TABLE archived_participants DROP FOREIGN KEY fk_archived_participant_user;
--rollback ALTER TABLE archived_participants DROP INDEX idx_archived_participant_user;
--rollback ALTER TABLE archived_participants DROP COLUMN user_id;
