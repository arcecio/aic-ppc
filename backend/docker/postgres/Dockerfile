# Postgres 16 + pgvector for the AIP PPC Assistant. pgvector backs the semantic
# arm of the regulatory-knowledgebase retriever (V4 migration adds the
# vector(1024) embedding column + HNSW index). Kept as a dedicated image so
# further extensions (e.g. Apache AGE for graph expansion, as in Blue) can be
# layered here without touching docker-compose.
FROM pgvector/pgvector:pg16
