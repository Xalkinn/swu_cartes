ALTER TABLE cartes ADD COLUMN variante VARCHAR(30) NOT NULL DEFAULT 'STANDARD';
ALTER TABLE cartes ADD COLUMN finition VARCHAR(12) NOT NULL DEFAULT 'MIXTE';
ALTER TABLE cartes ADD COLUMN code_variante_source VARCHAR(24) NULL;
ALTER TABLE cartes ADD COLUMN libelle_variante VARCHAR(120) NULL;
ALTER TABLE cartes ADD COLUMN rattachement_attendu BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_cartes_variante ON cartes (variante, finition);

-- Les possessions existantes et leurs compteurs ne sont pas déplacés.
UPDATE cartes SET variante = 'HYPERSPACE' WHERE hyperspace = TRUE;

CREATE TABLE possession_series (
    possession_id BIGINT NOT NULL,
    numero_serie VARCHAR(30) NOT NULL,
    PRIMARY KEY (possession_id, numero_serie),
    CONSTRAINT fk_possession_series FOREIGN KEY (possession_id)
        REFERENCES possessions(id) ON DELETE CASCADE
);
