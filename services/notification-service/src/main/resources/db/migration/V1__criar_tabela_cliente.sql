CREATE TABLE cliente (
                         id BIGSERIAL PRIMARY KEY,
                         telegram_chat_id BIGINT NOT NULL UNIQUE,
                         nome VARCHAR(255),
                         username_telegram VARCHAR(255),
                         criado_em TIMESTAMP NOT NULL,
                         ativo BOOLEAN NOT NULL DEFAULT true
);
