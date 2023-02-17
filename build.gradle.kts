plugins {
    kotlin("jvm") version "1.7.22"
    kotlin("plugin.serialization") version "1.7.22"

    id("net.mamoe.mirai-console") version "2.14.0"
}

group = "me.stageguard"
version = "2.6.0"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // skiko/skia
    compileOnly("xyz.cssxsh.mirai:mirai-skia-plugin:1.2.4")
    // database related lib
    implementation("org.ktorm:ktorm-core:3.6.0")
    implementation("com.mysql:mysql-connector-j:8.0.32")
    implementation("com.zaxxer:HikariCP:5.0.1")
    // network
    implementation(platform("io.ktor:ktor-bom:2.1.3"))
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-okhttp")
    // apache utilities
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.apache.commons:commons-compress:1.22")
    implementation("org.tukaani:xz:1.9")
    // javascript engine
    implementation("org.mozilla:rhino:1.7.14")
    // test
    testImplementation(kotlin("test"))
    // skiko/skia
    testImplementation("org.jetbrains.skiko:skiko-awt:0.7.50")
    testImplementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.7.50")
    testImplementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.50")
    testImplementation("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.7.50")
    testImplementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.7.50")
}

mirai {
    jvmTarget = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
}
