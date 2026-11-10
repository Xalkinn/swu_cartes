CREATE TABLE tournois (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 nom VARCHAR(100) NOT NULL, description TEXT NOT NULL, recompenses TEXT NOT NULL, conditions TEXT NOT NULL,
 capacite INT NOT NULL, meilleur_des INT NOT NULL, etat VARCHAR(20) NOT NULL,
 date_debut DATETIME NULL, createur_id BIGINT NOT NULL, date_creation TIMESTAMP(6) NOT NULL,
 CONSTRAINT fk_tournoi_createur FOREIGN KEY (createur_id) REFERENCES utilisateurs(id),
 CONSTRAINT ck_tournoi_capacite CHECK (capacite BETWEEN 2 AND 128),
 CONSTRAINT ck_tournoi_bo CHECK (meilleur_des IN (1,3,5))
);
CREATE INDEX idx_tournoi_etat_date ON tournois(etat,date_creation);
CREATE TABLE tournoi_inscriptions (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tournoi_id BIGINT NOT NULL, utilisateur_id BIGINT NOT NULL,
 date_inscription TIMESTAMP(6) NOT NULL,
 CONSTRAINT uq_tournoi_inscrit UNIQUE(tournoi_id,utilisateur_id),
 CONSTRAINT fk_inscrit_tournoi FOREIGN KEY (tournoi_id) REFERENCES tournois(id),
 CONSTRAINT fk_inscrit_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id)
);
CREATE TABLE tournoi_matchs (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, tournoi_id BIGINT NOT NULL, ronde INT NOT NULL, position_match INT NOT NULL,
 joueur1_id BIGINT NULL, joueur2_id BIGINT NULL, vainqueur_id BIGINT NULL, score1 INT NULL, score2 INT NULL, etat VARCHAR(20) NOT NULL,
 CONSTRAINT uq_tournoi_match UNIQUE(tournoi_id,ronde,position_match),
 CONSTRAINT fk_match_tournoi FOREIGN KEY (tournoi_id) REFERENCES tournois(id),
 CONSTRAINT fk_match_j1 FOREIGN KEY (joueur1_id) REFERENCES utilisateurs(id),
 CONSTRAINT fk_match_j2 FOREIGN KEY (joueur2_id) REFERENCES utilisateurs(id),
 CONSTRAINT fk_match_vainqueur FOREIGN KEY (vainqueur_id) REFERENCES utilisateurs(id)
);
