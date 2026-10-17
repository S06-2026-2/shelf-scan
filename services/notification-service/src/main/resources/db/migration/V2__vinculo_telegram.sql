ALTER TABLE cliente ADD COLUMN usuario_id VARCHAR(64);

CREATE UNIQUE INDEX uk_cliente_usuario_id ON cliente (usuario_id);

CREATE TABLE vinculo_telegram (
    codigo     VARCHAR(64) PRIMARY KEY,
    usuario_id VARCHAR(64) NOT NULL,
    criado_em  TIMESTAMP   NOT NULL,
    expira_em  TIMESTAMP   NOT NULL,
    usado_em   TIMESTAMP
);
