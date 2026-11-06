CREATE TABLE cartes (
                        id BIGINT NOT NULL AUTO_INCREMENT,

    -- Identifiant technique de la fiche française dans la source.
                        identifiant_source VARCHAR(100) NOT NULL,

                        titre VARCHAR(255) NOT NULL,
                        sous_titre VARCHAR(255) NULL,
                        numero INT NOT NULL,

    -- Référence à notre table des extensions.
                        extension_id BIGINT NOT NULL,

                        PRIMARY KEY (id),

                        CONSTRAINT uk_cartes_source UNIQUE (identifiant_source),

                        CONSTRAINT fk_cartes_extension
                            FOREIGN KEY (extension_id)
                                REFERENCES extensions (id),

                        CONSTRAINT chk_cartes_numero CHECK (numero > 0),

                        INDEX idx_cartes_extension_numero (extension_id, numero)
);