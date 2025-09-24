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

val edcVersion = libs.versions.edc

buildscript {
    dependencies {
        classpath(libs.edc.build.plugin)
    }
}


allprojects {
    apply(plugin = "org.eclipse.edc.edc-build")
    
    repositories {
        mavenLocal()
        mavenCentral()
    }

    // configure which version of the annotation processor to use. defaults to the same version as the plugin
    configure<org.eclipse.edc.plugins.autodoc.AutodocExtension> {
        processorVersion.set(edcVersion)
        outputDirectory.set(project.layout.buildDirectory.asFile)
    }

    configure<org.eclipse.edc.plugins.edcbuild.extensions.BuildExtension> {
        pom {
            scmUrl.set("https://github.com/OWNER/REPO.git")
            scmConnection.set("scm:git:git@github.com:OWNER/REPO.git")
            developerName.set("yourcompany")
            developerEmail.set("admin@yourcompany.com")
            projectName.set("your cool project based on EDC")
            projectUrl.set("www.coolproject.com")
            description.set("your description")
            licenseUrl.set("https://opensource.org/licenses/MIT")
        }
    }

    configure<CheckstyleExtension> {
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        configDirectory.set(rootProject.file("config/checkstyle"))
    }
}