ALTER TABLE workout_lines ADD COLUMN stretching_unit VARCHAR(16) NOT NULL DEFAULT 'SECONDS';
ALTER TABLE workout_segments ADD COLUMN breaths INT NULL;
ALTER TABLE stretching_set_entries ADD COLUMN stretching_unit VARCHAR(16) NOT NULL DEFAULT 'SECONDS';
CREATE TABLE stretching_set_breaths (
    entry_id BIGINT NOT NULL,
    position INT NOT NULL,
    breaths INT NOT NULL,
    PRIMARY KEY (entry_id, position),
    CONSTRAINT fk_stretching_breaths_entry FOREIGN KEY (entry_id) REFERENCES stretching_set_entries (id) ON DELETE CASCADE
);
