import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.mamoe.mirai-console") version "2.13.0"
    val kotlinVersion = "1.7.0"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

project.group = "me.stageguard"
project.version = "2.5.2"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://packages.jetbrains.team/maven/p/skija/maven")
    maven("https://repo.mirai.mamoe.net/snapshots")
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

val exposedVersion = "0.32.1"
val hikariVersion = "5.0.1"
val mysqlVersion = "8.0.29"
val miraiSlf4jBridgeVersion = "1.2.0"
val skijaVersion = "0.102.0"
val ktorServerVersion = "2.0.2"
val ktormVersion = "3.5.0"
val atomicFUVersion = "0.17.3"

val host: String = System.getProperty("os.name")
val arch: String = System.getProperty("os.arch")
val targetOs = when {
    host == "Mac OS X" -> "macos"
    host.startsWith("Win") -> "windows"
    host.startsWith("Linux") -> "linux"
    else -> error("Unsupported OS: $host")
}
val targetArch = when (arch) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported arch: $arch")
}

configure<KotlinProjectExtension> {
    project.dependencies {
        //kotlinx utilities
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
        implementation("org.jetbrains.kotlinx:atomicfu-jvm:$atomicFUVersion")
        //skija
        when {
            host.startsWith("Windows") ->
                api("io.github.humbleui:skija-windows:$skijaVersion")
            host == "Mac OS X" ->
                when (arch) {
                    "x86_64", "amd64" -> api("io.github.humbleui:skija-macos-x64:$skijaVersion")
                    "aarch64" -> api("io.github.humbleui:skija-macos-arm64:$skijaVersion")
                    else -> error("Unsupported arch: $arch")
                }
            host == "Linux" ->
                api("io.github.humbleui:skija-linux:$skijaVersion")
            else -> error("Unsupported platform: $host")
        }
        //database related lib
        implementation("org.ktorm:ktorm-core:${ktormVersion}")
        implementation("mysql:mysql-connector-java:$mysqlVersion")
        implementation("com.zaxxer:HikariCP:$hikariVersion")
        //network
        implementation("io.ktor:ktor-server-netty:$ktorServerVersion")
        implementation("io.ktor:ktor-client-core:$ktorServerVersion")
        implementation("io.ktor:ktor-client-okhttp:$ktorServerVersion")
        //mirai
        implementation("net.mamoe:mirai-slf4j-bridge:$miraiSlf4jBridgeVersion")
        //apache utilities
        implementation("commons-io:commons-io:2.11.0")
        implementation("commons-codec:commons-codec:1.15")
        implementation("org.apache.commons:commons-math3:3.6.1")
        implementation("org.apache.commons:commons-compress:1.21")
        implementation("org.tukaani:xz:1.9")
        //javascript engine
        implementation("org.mozilla:rhino:1.7.14")
        //test suite
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.5.21")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
        implementation(kotlin("stdlib-jdk8"))
    }

    project.kotlin {
        sourceSets {
            all {
                languageSettings {
                    optIn("kotlin.RequiresOptIn")
                    optIn("kotlin.ExperimentalStdlibApi")
                    optIn("kotlin.contracts.ExperimentalContracts")
                }
            }
        }
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}

val version: Task by tasks.creating {
    group = "verification"
    file("$projectDir/version")
        .writeText("OsuMapSuggester-${targetOs}-${targetArch}-${project.version}")
}

val checkCargo: Task by tasks.creating {
    group = "build"
    project.exec {
        commandLine("cargo", "--version")
    }.assertNormalExitValue()
}

val buildJniNative: Task by tasks.creating {
    group = "build"
    dependsOn(checkCargo)

    project.exec {
        workingDir("$projectDir/rosu-pp-jni/")
        commandLine("cargo", "build", "--color=always", "--release")
    }.assertNormalExitValue()

    val libFile = when {
        host.startsWith("Windows") -> "rosu_pp.dll"
        host == "Mac OS X" -> "librosu_pp.dylib"
        host == "Linux" -> "librosu_pp.so"
        else -> throw Error("Unsupported platform: $host")
    }

    val buildOutputLib = file("$projectDir/rosu-pp-jni/target/release/$libFile")
    buildOutputLib.copyTo(file("$projectDir/src/main/resources/$libFile"), overwrite = true)
}

val generateJniHeaders: Task by tasks.creating {
    group = "build"
    dependsOn(tasks.getByName("compileKotlin"))

    // For caching
    val path = "build/generated/jni"
    inputs.dir("src/main/kotlin")
    outputs.dir(path)

    doLast {
        val javaHome = org.gradle.internal.jvm.Jvm.current().javaHome
        val javap = javaHome.resolve("bin").walk().firstOrNull { it.name.startsWith("javap") }?.absolutePath ?: error("javap not found")
        val javac = javaHome.resolve("bin").walk().firstOrNull { it.name.startsWith("javac") }?.absolutePath ?: error("javac not found")
        val buildDir = file("build/classes/kotlin/main")
        val tmpDir = file("build/tmp/jvmJni").apply { mkdirs() }

        val bodyExtractingRegex = """^.+\Rpublic \w* ?class ([^\s]+).*\{\R((?s:.+))\}\R$""".toRegex()
        val nativeMethodExtractingRegex = """.*\bnative\b.*""".toRegex()

        buildDir.walkTopDown()
            .filter { "META" !in it.absolutePath }
            .forEach { file ->
                if (!file.isFile) return@forEach

                val output = org.apache.commons.io.output.ByteArrayOutputStream().use {
                    project.exec {
                        commandLine(javap, "-private", "-cp", buildDir.absolutePath, file.absolutePath)
                        standardOutput = it
                    }.assertNormalExitValue()
                    it.toByteArray().decodeToString()
                }

                val (qualifiedName, methodInfo) = bodyExtractingRegex.find(output)?.destructured ?: return@forEach

                val lastDot = qualifiedName.lastIndexOf('.')
                val packageName = qualifiedName.substring(0, lastDot)
                val className = qualifiedName.substring(lastDot+1, qualifiedName.length)

                val nativeMethods =
                    nativeMethodExtractingRegex.findAll(methodInfo).map { it.groups }.flatMap { it.asSequence().mapNotNull { group -> group?.value } }.toList()
                if (nativeMethods.isEmpty()) return@forEach

                val source = buildString {
                    appendLine("package $packageName;")
                    appendLine("public class $className {")
                    for (method in nativeMethods) {
                        if ("()" in method) appendLine(method)
                        else {
                            val updatedMethod = StringBuilder(method).apply {
                                var count = 0
                                var i = 0
                                while (i < length) {
                                    if (this[i] == ',' || this[i] == ')') insert(i, " arg${count++}".also { i += it.length + 1 })
                                    else i++
                                }
                            }
                            appendLine(updatedMethod)
                        }
                    }
                    appendLine("}")
                }
                val outputFile = tmpDir.resolve(packageName.replace(".", "/")).apply { mkdirs() }.resolve("$className.java").apply { delete() }.apply { createNewFile() }
                outputFile.writeText(source)

                project.exec {
                    commandLine(javac, "-h", path, outputFile.absolutePath)
                }.assertNormalExitValue()
            }
    }
}


afterEvaluate {
    arrayOf("buildPlugin", "buildPluginLegacy").forEach { taskName ->
        tasks.named<Jar>(taskName).configure {
            archiveBaseName.set("OsuMapSuggester-${targetOs}-${targetArch}")
        }
    }
}
