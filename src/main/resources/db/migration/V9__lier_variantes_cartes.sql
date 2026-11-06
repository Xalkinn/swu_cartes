ALTER TABLE cartes
    ADD COLUMN carte_standard_id BIGINT NULL,
    ADD INDEX idx_cartes_standard (carte_standard_id),
    ADD CONSTRAINT fk_cartes_standard
        FOREIGN KEY (carte_standard_id)
        REFERENCES cartes(id);