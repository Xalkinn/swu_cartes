CREATE TABLE verrous_administration (
    id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;

-- Toutes les modifications d'accès verrouillent cette ligne dans leur transaction.
-- Cela protège notamment le dernier administrateur lors de demandes simultanées.
INSERT INTO verrous_administration (id) VALUES (1);
