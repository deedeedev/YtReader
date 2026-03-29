/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    `java-library`
    id("com.google.protobuf") version "0.9.4"
    `maven-publish`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    main {
        java.srcDir("../timeago-parser/src/main/java")
    }
}

// Protobuf files would uselessly end up in the JAR otherwise, see
// https://github.com/google/protobuf-gradle-plugin/issues/390
tasks.jar {
    exclude("**/*.proto")
    includeEmptyDirs = false
}

dependencies {
    implementation(libs.newpipe.nanojson)
    implementation(libs.jsoup)
    implementation(libs.google.jsr305)
    implementation(libs.google.protobuf)

    implementation(libs.mozilla.rhino.core)
    implementation(libs.mozilla.rhino.engine)

    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation(libs.okhttp)
    testImplementation(libs.google.gson)
    testImplementation("com.google.errorprone:error_prone_annotations:2.21.1")
}

tasks.test {
    useJUnitPlatform()
    failOnNoDiscoveredTests.set(false)
    enabled = false
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.lib.get()}"
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
            }
        }
    }
}
