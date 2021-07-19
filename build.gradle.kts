plugins {
    val kotlinVersion = "1.5.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.7-M2"
}

group = "me.stageguard"
version = "1.1-SNAPSHOT"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven ("https://packages.jetbrains.team/maven/p/skija/maven")
    mavenCentral()
    jcenter()
    mavenCentral()
    gradlePluginPortal()
}

val exposedVersion = "0.25.1"
val hikariVersion = "3.4.5"
val mysqlVersion = "8.0.19"
val miraiSlf4jBridgeVersion = "1.1.0"
val skijaVersion = "0.92.15"

dependencies {
    //test suite
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.0")
    //kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    //skija
    implementation("org.jetbrains.skija:skija-windows:$skijaVersion")
    implementation("org.jetbrains.skija:skija-linux:$skijaVersion")
    //database related lib
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("mysql:mysql-connector-java:$mysqlVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.ktor:ktor-server-netty:1.4.0")
    //mirai
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")

}