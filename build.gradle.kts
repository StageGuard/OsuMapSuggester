plugins {
    id("net.mamoe.mirai-console") version "2.7.0"
    val kotlinVersion = "1.5.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

group = "me.stageguard"
version = "1.5"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://packages.jetbrains.team/maven/p/skija/maven")
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

val exposedVersion = "0.32.1"
val hikariVersion = "5.0.0"
val mysqlVersion = "8.0.25"
val miraiSlf4jBridgeVersion = "1.2.0"
val skijaVersion = "0.92.15"
val ktorServerVersion = "1.4.0"
val ktormVersion = "3.4.1"

dependencies {
    //test suite
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.5.21")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    //kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    //skija
    implementation("org.jetbrains.skija:skija-windows:$skijaVersion")
    implementation("org.jetbrains.skija:skija-linux:$skijaVersion")
    //database related lib
    implementation("org.ktorm:ktorm-core:${ktormVersion}")
    implementation("mysql:mysql-connector-java:$mysqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    //network
    implementation("io.ktor:ktor-server-netty:$ktorServerVersion")
    //mirai
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
    //apache utilities
    implementation("commons-io:commons-io:2.6")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.tukaani:xz:1.9")

}

kotlin {
    sourceSets {
        all {
            languageSettings {
                useExperimentalAnnotation("kotlin.RequiresOptIn")
                useExperimentalAnnotation("kotlin.ExperimentalStdlibApi")
                useExperimentalAnnotation("kotlin.contracts.ExperimentalContracts")
            }
        }
    }
}