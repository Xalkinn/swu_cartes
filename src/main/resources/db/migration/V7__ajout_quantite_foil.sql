ALTER TABLE possessions
    ADD COLUMN quantite_foil INT NOT NULL DEFAULT 0,

    ADD CONSTRAINT chk_possessions_foil
        CHECK (
            quantite_foil >= 0
            AND quantite_foil <= quantite
        );