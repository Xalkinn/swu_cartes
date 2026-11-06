-- NULL signifie que la carte n'a pas de coût renseigné.
ALTER TABLE cartes
    ADD COLUMN cout INT NULL,
    ADD CONSTRAINT chk_cartes_cout CHECK (cout IS NULL OR cout >= 0);

-- Une ligne par affinité et par carte.
-- nombre_symboles permet de représenter une double affinité identique.
CREATE TABLE carte_affinites (
                                 carte_id BIGINT NOT NULL,
                                 code_affinite VARCHAR(30) NOT NULL,
                                 nombre_symboles INT NOT NULL,

                                 PRIMARY KEY (carte_id, code_affinite),

                                 CONSTRAINT fk_carte_affinites_carte
                                     FOREIGN KEY (carte_id)
                                         REFERENCES cartes (id),

                                 CONSTRAINT chk_affinites_nombre CHECK (nombre_symboles > 0),

                                 INDEX idx_affinites_code (code_affinite)
);