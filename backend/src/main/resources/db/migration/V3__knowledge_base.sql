CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT       NOT NULL,
    title       VARCHAR(300) NOT NULL,
    file_name   VARCHAR(500) NULL,
    file_type   VARCHAR(50)  NULL,
    file_size   BIGINT       NULL,
    chunk_count INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    INDEX idx_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id      BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL DEFAULT 0,
    content      TEXT         NOT NULL,
    keywords     VARCHAR(1000) NULL,
    INDEX idx_base (base_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
