CREATE TABLE recherches_cartes (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL,
    carte_id BIGINT NOT NULL,
    date_ajout TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_recherches_utilisateur_carte UNIQUE(utilisateur_id, carte_id),
    CONSTRAINT fk_recherches_utilisateur FOREIGN KEY(utilisateur_id) REFERENCES utilisateurs(id),
    CONSTRAINT fk_recherches_carte FOREIGN KEY(carte_id) REFERENCES cartes(id),
    INDEX idx_recherches_date(utilisateur_id, date_ajout)
) ENGINE=InnoDB;

CREATE TABLE vitrines (
    utilisateur_id BIGINT NOT NULL PRIMARY KEY,
    publiee BOOLEAN NOT NULL DEFAULT FALSE,
    partager_collection BOOLEAN NOT NULL DEFAULT FALSE,
    partager_decks BOOLEAN NOT NULL DEFAULT FALSE,
    partager_recherches BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_vitrines_utilisateur FOREIGN KEY(utilisateur_id) REFERENCES utilisateurs(id)
) ENGINE=InnoDB;
