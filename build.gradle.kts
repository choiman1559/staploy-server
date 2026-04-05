val kotlinVersion: String by project
val logbackVersion: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.staploy"
version = "0.0.1"

kotlin {
    jvmToolchain(24)
}

application {
    mainClass = "com.staploy.ApplicationKt"
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
        }
        proto {
            srcDir("src/main/protobuf")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.29.2"
    }
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-compression")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-serialization-jackson")

    implementation("com.google.protobuf:protobuf-java:4.29.3")
    implementation("com.google.protobuf:protobuf-java-util:4.29.3")
    implementation("org.json:json:20250107")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}
