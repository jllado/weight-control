CREATE TABLE stretching_sets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(765) NOT NULL,
    CONSTRAINT uq_stretching_set_name UNIQUE (user_id, normalized_name),
    CONSTRAINT fk_stretching_set_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE TABLE stretching_set_entries (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stretching_set_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    position INT NOT NULL,
    CONSTRAINT fk_stretching_entry_set FOREIGN KEY (stretching_set_id) REFERENCES stretching_sets(id),
    CONSTRAINT fk_stretching_entry_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id)
);
CREATE TABLE stretching_set_holds (
    entry_id BIGINT NOT NULL,
    position INT NOT NULL,
    duration_seconds INT NOT NULL,
    PRIMARY KEY (entry_id, position),
    CONSTRAINT fk_stretching_hold_entry FOREIGN KEY (entry_id) REFERENCES stretching_set_entries(id)
);
