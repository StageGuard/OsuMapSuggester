plugins {
    val kotlinVersion = "1.5.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.7-RC-dev-3"
}

group = "me.stageguard"
version = "1.3"

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
val ktorServerVersion = "1.6.2"

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
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("mysql:mysql-connector-java:$mysqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.ktor:ktor-server-netty:$ktorServerVersion")
    //mirai
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
    //apache utilities
    implementation("commons-io:commons-io:2.6")
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
            }
        }
    }
}