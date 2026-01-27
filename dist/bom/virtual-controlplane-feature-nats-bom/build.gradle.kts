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

plugins {
    `java-library`
}

dependencies {
    api(project(":extensions:control-plane:tasks:publisher:negotiation-tasks-publisher-nats"))
    api(project(":extensions:control-plane:tasks:publisher:transfer-tasks-publisher-nats"))
    api(project(":extensions:control-plane:tasks:subscriber:negotiation-tasks-subscriber-nats"))
    api(project(":extensions:control-plane:tasks:subscriber:transfer-tasks-subscriber-nats"))
}

