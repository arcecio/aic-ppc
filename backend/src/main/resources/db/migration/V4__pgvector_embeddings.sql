-- Semantic-retrieval layer for the regulatory knowledgebase (SOW 2.1.1).
-- Adds pgvector and a 1024-dim embedding per code section (intfloat/e5-large-v2
-- served by a TEI sidecar, mirroring the Blue reference). The column is
-- intentionally NOT mapped on the JPA entity — RegulatoryCodeRepository handles
-- it via native SQL with explicit ::vector casts (Blue pattern).
--
-- Requires the pgvector extension binaries: the compose image is
-- pgvector/pgvector:pg16 and tests use the same image via Testcontainers.

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE regulatory_codes ADD COLUMN embedding vector(1024);

-- HNSW cosine index; `<=>` is pgvector's cosine-distance operator.
CREATE INDEX idx_regcodes_embedding ON regulatory_codes
    USING hnsw (embedding vector_cosine_ops);
