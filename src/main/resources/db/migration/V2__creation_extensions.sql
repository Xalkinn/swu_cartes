CREATE TABLE extensions (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            code VARCHAR(12) NOT NULL,
                            nom VARCHAR(150) NOT NULL,
                            date_sortie DATE NULL,

                            PRIMARY KEY (id),
                            CONSTRAINT uk_extensions_code UNIQUE (code)
);