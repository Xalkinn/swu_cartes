CREATE TABLE possessions (
                             id BIGINT NOT NULL AUTO_INCREMENT,
                             utilisateur_id BIGINT NOT NULL,
                             carte_id BIGINT NOT NULL,
                             quantite INT NOT NULL,

                             PRIMARY KEY (id),

                             CONSTRAINT uk_possessions_utilisateur_carte
                                 UNIQUE (utilisateur_id, carte_id),

                             CONSTRAINT fk_possessions_utilisateur
                                 FOREIGN KEY (utilisateur_id)
                                     REFERENCES utilisateurs (id),

                             CONSTRAINT fk_possessions_carte
                                 FOREIGN KEY (carte_id)
                                     REFERENCES cartes (id),

                             CONSTRAINT chk_possessions_quantite
                                 CHECK (quantite BETWEEN 1 AND 9999)
);