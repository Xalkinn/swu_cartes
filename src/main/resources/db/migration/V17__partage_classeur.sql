-- Le classeur reste privé tant que le joueur n'a pas explicitement autorisé son partage.
ALTER TABLE vitrines ADD COLUMN partager_classeur BOOLEAN NOT NULL DEFAULT FALSE;
