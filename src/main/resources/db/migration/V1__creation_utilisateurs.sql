CREATE TABLE utilisateurs (
                              id BIGINT NOT NULL AUTO_INCREMENT,

                              pseudo VARCHAR(50) NOT NULL,
                              email VARCHAR(254) NOT NULL,

    -- Contiendra uniquement le résultat du hachage du mot de passe.
                              mot_de_passe_hash VARCHAR(255) NOT NULL,

    -- Les inscriptions créeront toujours un utilisateur classique.
                              role VARCHAR(20) NOT NULL DEFAULT 'USER',

                              actif BOOLEAN NOT NULL DEFAULT TRUE,

                              date_creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              PRIMARY KEY (id),

                              CONSTRAINT uk_utilisateurs_pseudo UNIQUE (pseudo),
                              CONSTRAINT uk_utilisateurs_email UNIQUE (email),

                              CONSTRAINT chk_utilisateurs_role
                                  CHECK (role IN ('USER', 'ADMIN'))
);