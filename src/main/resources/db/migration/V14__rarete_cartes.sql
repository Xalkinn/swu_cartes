ALTER TABLE cartes ADD COLUMN rarete VARCHAR(2) NULL;
CREATE INDEX idx_cartes_rarete_type ON cartes (rarete, type_carte);
