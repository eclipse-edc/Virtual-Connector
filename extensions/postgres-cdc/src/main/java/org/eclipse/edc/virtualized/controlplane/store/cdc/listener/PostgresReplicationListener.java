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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.virtualized.controlplane.store.cdc.DatabaseChange;
import org.eclipse.edc.virtualized.controlplane.store.cdc.PostgresChangeDataCaptureExtension.PostgresCdcConfig;
import org.postgresql.jdbc.PgConnection;
import org.postgresql.replication.PGReplicationStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.postgresql.PGProperty.ASSUME_MIN_SERVER_VERSION;
import static org.postgresql.PGProperty.PASSWORD;
import static org.postgresql.PGProperty.PREFER_QUERY_MODE;
import static org.postgresql.PGProperty.REPLICATION;
import static org.postgresql.PGProperty.USER;

public class PostgresReplicationListener {

    private static final String WAL_OUTPUT_PLUGIN = "wal2json";
    private static final String SQLSTATE_DUPLICATE_OBJECT = "42710";
    private final PostgresCdcConfig config;
    private final Supplier<ObjectMapper> objectMapper;
    private final Monitor monitor;
    private final Properties datasourceProperties = new Properties();
    private final Function<DatabaseChange, StatusResult<Void>> consumer;
    private final ExecutorService executorService;
    private final AtomicBoolean active = new AtomicBoolean();

    public PostgresReplicationListener(PostgresCdcConfig config, Function<DatabaseChange, StatusResult<Void>> consumer, Supplier<ObjectMapper> objectMapper, Monitor monitor) {
        this.config = config;
        this.consumer = consumer;
        this.objectMapper = objectMapper;
        this.monitor = monitor;
        USER.set(datasourceProperties, config.username());
        PASSWORD.set(datasourceProperties, config.password());
        REPLICATION.set(datasourceProperties, "database");
        PREFER_QUERY_MODE.set(datasourceProperties, "simple");
        ASSUME_MIN_SERVER_VERSION.set(datasourceProperties, "9.4");

        executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("pg-replication-stream-listener");
            return thread;
        });
    }

    public void createReplicationSlot() {
        try (var connection = createConnection()) {
            connection.getReplicationAPI()
                    .createReplicationSlot()
                    .logical()
                    .withSlotName(config.replicationSlotName())
                    .withOutputPlugin(WAL_OUTPUT_PLUGIN)
                    .make();
        } catch (SQLException e) {
            if (SQLSTATE_DUPLICATE_OBJECT.equals(e.getSQLState())) {
                monitor.info(format("Replication slot %s already exists", config.replicationSlotName()));
            } else {
                throw new RuntimeException("Could not create replication slot " + config.replicationSlotName(), e);
            }
        }
    }

    public void start() {
        this.active.set(true);
        executorService.submit(() -> {
            try (var connection = createConnection()) {
                try (var stream = getStream(connection)) {
                    consumeStream(stream);
                }
            } catch (SQLException e) {
                monitor.severe("Error in replication stream: " + e.getMessage());
            }
        });
    }

    private void consumeStream(PGReplicationStream stream) throws SQLException {
        monitor.info("Starting replication stream for slot: " + config.replicationSlotName());
        while (active.get()) {
            var msg = stream.read();
            if (msg == null) {
                monitor.info("Replication stream has no new messages, waiting for next message...");
                continue;
            }
            var payload = deserializedDatabaseChange(msg);
            var result = consumer.apply(payload);
            if (result.failed()) {
                monitor.severe("Failed to process change: " + result.getFailureDetail());
                continue;
            }
            stream.setAppliedLSN(stream.getLastReceiveLSN());
            stream.setFlushedLSN(stream.getLastReceiveLSN());
            stream.forceUpdateStatus();
        }
        monitor.info("Stopping replication stream for slot: " + config.replicationSlotName());
    }

    private PGReplicationStream getStream(PgConnection connection) throws SQLException {
        return connection.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(config.replicationSlotName())
                .withSlotOption("format-version", 2)
                .withSlotOption("include-transaction", false)
                .withSlotOption("include-timestamp", true)
                .withSlotOption("add-tables", config.tables())
                .withStatusInterval(10, TimeUnit.SECONDS)
                .start();
    }

    private DatabaseChange deserializedDatabaseChange(ByteBuffer message) {
        int offset = message.arrayOffset();
        byte[] source = message.array();
        int length = source.length - offset;
        try {
            return objectMapper.get().readValue(source, offset, length, DatabaseChange.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        this.active.set(false);
        executorService.shutdown();
    }

    private PgConnection createConnection() {
        try {
            return DriverManager.getConnection(config.jdbcUrl(), datasourceProperties).unwrap(PgConnection.class);
        } catch (SQLException e) {
            throw new RuntimeException("Could not create database connection", e);
        }
    }

}
