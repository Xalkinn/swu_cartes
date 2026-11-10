CREATE TABLE meta_snapshot (
 id BIGINT NOT NULL PRIMARY KEY,
 payload LONGTEXT NOT NULL,
 derniere_reussite TIMESTAMP(6) NULL,
 derniere_tentative TIMESTAMP(6) NULL,
 verrou_jusqua TIMESTAMP(6) NULL,
 jeton VARCHAR(36) NULL,
 erreur VARCHAR(250) NULL
);
INSERT INTO meta_snapshot(id,payload) VALUES(1,'');
