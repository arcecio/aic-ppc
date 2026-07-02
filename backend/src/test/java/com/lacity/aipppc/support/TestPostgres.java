package com.lacity.aipppc.support;

import org.testcontainers.utility.DockerImageName;

/**
 * The Postgres image integration tests run against. pgvector is required at
 * runtime (the V4 migration executes {@code CREATE EXTENSION vector}), so tests
 * use the same pgvector image family as the compose stack — mirroring Blue's
 * TestPostgresImage pattern.
 */
public final class TestPostgres {

    public static final DockerImageName IMAGE =
        DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres");

    private TestPostgres() {}
}
