import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.6.10"
    java
    kotlin("plugin.serialization") version "1.6.10"
    id("com.google.protobuf") version "0.8.18"
}

val krotoplusVersion: String by project
val protobufVersion: String by project
val grpcVersion: String by project
val kotlinxCoroutinesVersion: String by project
val logbackVersion: String by project

group = "com.kenvix"
version = "0.1"
val applicationName = "NATPoked"
val versionCode = 1
val archivesBaseName = "natpoked"
val mainSrcDir = "src/main"
val testSrcDir = "src/test"
val generatedSrcDir = "generatedSrc"
val fullPackageName = "${group}.$archivesBaseName"
val fullPackagePath = fullPackageName.replace('.', '/')
val isReleaseBuild = System.getProperty("isReleaseBuild") != null
val systemProperties: java.util.Properties = System.getProperties()
val libDirName = "libs"
val appMainClass = "${fullPackageName}.Main"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    /* Protobuf & GRPC */
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-coroutines:$krotoplusVersion")
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-message:$krotoplusVersion")

    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty:$grpcVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    /* Logback */
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-protobuf
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.3.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

// Add generated build-config directories to the main source set, so that the
// IDE doesn"t complain when the app references BuildConfig classes
sourceSets {
    getByName("main").apply {
        java.srcDirs(
            File(mainSrcDir),
            File(generatedSrcDir),
            File(buildDir, "gen/buildconfig/src"),
            File(buildDir, "src"),
            File("$buildDir/generated/source/proto/main/java"),
            File("$buildDir/generated/source/proto/main/grpc"),
            File("$buildDir/generated/source/proto/main/coroutines"),
        )
        resources.srcDirs("$mainSrcDir/resources")
    }

    getByName("test").apply {
        java.srcDirs(
            File(testSrcDir),
            File(generatedSrcDir),
            File(buildDir, "gen/buildconfig/src"),
            File(buildDir, "src"),
            File("$buildDir/generated/source/proto/main/java"),
            File("$buildDir/generated/source/proto/main/grpc"),
            File("$buildDir/generated/source/proto/main/coroutines"),
        )
        resources.srcDirs("$testSrcDir/resources")
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        kotlinOptions.freeCompilerArgs += "-Xinline-classes"
    }

    withType<JavaCompile> {
        options.encoding = "utf-8"
    }
}

apply(from = "enableGRPC.gradle")