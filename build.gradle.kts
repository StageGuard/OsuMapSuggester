plugins {
    val kotlinVersion = "1.5.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.7-M2"
}

group = "me.stageguard"
version = "1.2"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven ("https://packages.jetbrains.team/maven/p/skija/maven")
    mavenCentral()
    gradlePluginPortal()
}

val exposedVersion = "0.32.1"
val hikariVersion = "5.0.0"
val mysqlVersion = "8.0.25"
val miraiSlf4jBridgeVersion = "1.1.0"
val skijaVersion = "0.92.15"

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
    implementation("io.ktor:ktor-server-netty:1.6.2")
    //mirai
    implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
    //apache commons
    implementation("commons-io:commons-io:2.6")
    implementation("org.apache.commons:commons-math3:3.6.1")

}