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

package org.eclipse.edc.virtual.test.system.fixtures;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

public class DockerImages {

    static final ImageFromDockerfile BASE_IMAGE = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder ->
                    builder.from("postgres:17.5")
                            .run("apt update && apt install -y postgresql-17-wal2json postgresql-contrib")
                            .build());
    public static final DockerImageName PG_IMAGE = DockerImageName.parse(BASE_IMAGE.get())
            .asCompatibleSubstituteFor(PostgreSQLContainer.IMAGE);


    @SuppressWarnings("resource")
    public static PostgreSQLContainer<?> createPgContainer() {
        return new PostgreSQLContainer<>(PG_IMAGE)
                .withCommand("-c", "wal_level=logical");
    }
}
