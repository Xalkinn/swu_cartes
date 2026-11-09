ALTER TABLE decks ADD COLUMN publie_communaute BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE decks ADD COLUMN date_publication_communaute TIMESTAMP(6) NULL;
CREATE INDEX idx_decks_communaute ON decks (publie_communaute, date_publication_communaute, id);
