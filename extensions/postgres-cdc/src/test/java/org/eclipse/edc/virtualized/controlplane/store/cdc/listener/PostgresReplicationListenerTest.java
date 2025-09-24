/*
 *  Copyright (c) 2025 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.virtualized.controlplane.store.cdc.listener;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndExtension;
import org.eclipse.edc.virtualized.controlplane.store.cdc.DatabaseChange;
import org.eclipse.edc.virtualized.controlplane.store.cdc.PostgresChangeDataCaptureExtension;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@PostgresqlIntegrationTest
@Testcontainers
public class PostgresReplicationListenerTest {

    static final ImageFromDockerfile BASE_IMAGE = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder ->
                    builder.from("postgres:17.5")
                            .run("apt update && apt install -y postgresql-17-wal2json postgresql-contrib")
                            .build());

    static final DockerImageName PG_IMAGE = DockerImageName.parse(BASE_IMAGE.get())
            .asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE);
    static final PostgreSQLContainer<?> POSTGRESQL_CONTAINER = new PostgreSQLContainer<>(PG_IMAGE)
            .withCommand("-c", "wal_level=logical");

    @Order(0)
    @RegisterExtension
    static final PostgresqlEndToEndExtension POSTGRESQL_EXTENSION = new PostgresqlEndToEndExtension(POSTGRESQL_CONTAINER);

    @Order(1)
    @RegisterExtension
    static final BeforeAllCallback CREATE_DATABASES = context -> {
        POSTGRESQL_EXTENSION.createDatabase("testdb");
    };


    @Test
    void replicationListenerTest() {
        POSTGRESQL_EXTENSION.execute("testdb", "CREATE TABLE test_publication(id serial primary key, data text);");

        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Function<DatabaseChange, StatusResult<Void>> consumer = mock();

        var cfg = new PostgresChangeDataCaptureExtension.PostgresCdcConfig(
                POSTGRESQL_EXTENSION.getJdbcUrl("testdb"),
                POSTGRESQL_EXTENSION.getUsername(),
                POSTGRESQL_EXTENSION.getPassword(),
                "test_slot",
                "public.test_publication"

        );

        when(consumer.apply(any())).thenReturn(StatusResult.success());

        var replication = new PostgresReplicationListener(cfg, consumer, () -> mapper, mock());

        replication.createReplicationSlot();
        replication.start();

        POSTGRESQL_EXTENSION.execute("testdb", "INSERT INTO test_publication(data) VALUES('1');");

        await().untilAsserted(() -> {
            var changeCaptor = ArgumentCaptor.forClass(DatabaseChange.class);
            verify(consumer).apply(changeCaptor.capture());
            var change = changeCaptor.getValue();
            assertThat(change.schema()).isEqualTo("public");
            assertThat(change.table()).isEqualTo("test_publication");
            assertThat(change.row()).satisfies(row -> {
                assertThat(row.getInt("id")).isEqualTo(1);
                assertThat(row.getString("data")).isEqualTo("1");
            });
        });
    }
}
