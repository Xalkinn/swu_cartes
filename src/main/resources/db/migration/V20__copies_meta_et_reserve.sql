ALTER TABLE decks ADD COLUMN meta_source_id VARCHAR(36) NULL;
ALTER TABLE decks ADD CONSTRAINT uk_deck_copie_meta UNIQUE (utilisateur_id, meta_source_id);
CREATE TABLE deck_reserve (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    deck_id BIGINT NOT NULL,
    carte_id BIGINT NOT NULL,
    quantite INT NOT NULL,
    CONSTRAINT uk_deck_reserve UNIQUE (deck_id, carte_id),
    CONSTRAINT fk_reserve_deck FOREIGN KEY (deck_id) REFERENCES decks(id) ON DELETE CASCADE,
    CONSTRAINT fk_reserve_carte FOREIGN KEY (carte_id) REFERENCES cartes(id)
);
