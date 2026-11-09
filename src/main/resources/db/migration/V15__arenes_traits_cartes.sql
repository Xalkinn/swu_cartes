CREATE TABLE carte_arenes (
    carte_id BIGINT NOT NULL,
    code_arene VARCHAR(20) NOT NULL,
    PRIMARY KEY (carte_id, code_arene),
    CONSTRAINT fk_carte_arenes_carte FOREIGN KEY (carte_id) REFERENCES cartes(id)
);
CREATE INDEX idx_carte_arenes_code ON carte_arenes (code_arene, carte_id);
CREATE TABLE carte_traits (
    carte_id BIGINT NOT NULL,
    trait VARCHAR(80) NOT NULL,
    PRIMARY KEY (carte_id, trait),
    CONSTRAINT fk_carte_traits_carte FOREIGN KEY (carte_id) REFERENCES cartes(id)
);
CREATE INDEX idx_carte_traits_nom ON carte_traits (trait, carte_id);
