ALTER TABLE cartes ADD COLUMN type_carte VARCHAR(40) NULL;
CREATE INDEX idx_cartes_type ON cartes(type_carte);

CREATE TABLE decks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL,
    nom VARCHAR(80) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    leader_id BIGINT NULL,
    base_id BIGINT NULL,
    date_creation TIMESTAMP(6) NOT NULL,
    date_modification TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_decks_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id),
    CONSTRAINT fk_decks_leader FOREIGN KEY (leader_id) REFERENCES cartes(id),
    CONSTRAINT fk_decks_base FOREIGN KEY (base_id) REFERENCES cartes(id),
    INDEX idx_decks_utilisateur_date(utilisateur_id, date_modification)
) ENGINE=InnoDB;

CREATE TABLE deck_cartes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    deck_id BIGINT NOT NULL,
    carte_id BIGINT NOT NULL,
    quantite INT NOT NULL,
    CONSTRAINT uk_deck_carte UNIQUE(deck_id, carte_id),
    CONSTRAINT fk_deck_cartes_deck FOREIGN KEY(deck_id) REFERENCES decks(id) ON DELETE CASCADE,
    CONSTRAINT fk_deck_cartes_carte FOREIGN KEY(carte_id) REFERENCES cartes(id),
    CONSTRAINT chk_deck_quantite CHECK (quantite BETWEEN 1 AND 99)
) ENGINE=InnoDB;
