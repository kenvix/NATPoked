import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties
import com.google.protobuf.gradle.*

plugins {
    kotlin("jvm") version "1.6.20"
    java
    kotlin("plugin.serialization") version "1.6.20"
    id("com.google.protobuf") version "0.8.18"
    id("org.graalvm.buildtools.native") version "0.9.9"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

val krotoplusVersion: String by project
val protobufVersion: String by project
val grpcVersion: String by project
val kotlinxCoroutinesVersion: String by project
val logbackVersion: String by project
val ktorVersion: String by project

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
    implementation(fileTree("libs"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")

    /* Protobuf & GRPC */
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-coroutines:$krotoplusVersion")
    implementation("com.github.marcoferrer.krotoplus:kroto-plus-message:$krotoplusVersion")

//    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
//
//    implementation("io.grpc:grpc-protobuf:$grpcVersion")
//    implementation("io.grpc:grpc-stub:$grpcVersion")
//    implementation("io.grpc:grpc-netty:$grpcVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

    /* Logback */
    implementation("ch.qos.logback:logback-classic:$logbackVersion")


    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-protobuf
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.3.2")

    implementation("net.mamoe.yamlkt:yamlkt:0.10.2")

    // https://mvnrepository.com/artifact/com.google.guava/guava
    implementation("com.google.guava:guava:31.1-jre")

    implementation("org.whispersystems:curve25519-java:0.5.0")


    /* Ktor server things */
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    implementation("io.ktor:ktor-locations:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-serialization:$ktorVersion")
    implementation("io.ktor:ktor-network:$ktorVersion")

    /* Ktor client things */
    // ktor client 就是坨屎
//    implementation("io.ktor:ktor-client-core:$ktorVersion")
//    implementation("io.ktor:ktor-client-cio:$ktorVersion")
//    implementation("io.ktor:ktor-client-websockets:$ktorVersion")

    // https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")

    // https://mvnrepository.com/artifact/org.slf4j/slf4j-api
    implementation("org.slf4j:slf4j-api:1.7.36")

    // https://mvnrepository.com/artifact/io.github.cdimascio/java-dotenv
    implementation("io.github.cdimascio:java-dotenv:5.2.2")


    /* Apache commons */
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation(group = "commons-cli", name = "commons-cli", version = "1.5.0")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.apache.commons:commons-text:1.9")


    // https://mvnrepository.com/artifact/io.netty/netty-all
//    implementation("io.netty:netty-all:4.1.73.Final") {
//        exclude("org.slf4j:slf4j-simple")
//    }
//
//    // https://mvnrepository.com/artifact/com.github.l42111996/kcp-base
//    implementation("com.github.l42111996:kcp-base:1.6") {
//        exclude("io.netty:netty-all")
//        exclude("org.slf4j:slf4j-simple")
//    }

    // https://mvnrepository.com/artifact/org.apache.commons/commons-math3
    implementation("org.apache.commons:commons-math3:3.6.1")

    // https://mvnrepository.com/artifact/io.netty/netty-buffer
    implementation("io.netty:netty-buffer:4.1.75.Final")

    // https://mvnrepository.com/artifact/org.apache.commons/commons-lang3
    implementation("org.apache.commons:commons-lang3:3.12.0")

    // https://mvnrepository.com/artifact/org.eclipse.paho/org.eclipse.paho.mqttv5.client
    implementation("org.eclipse.paho:org.eclipse.paho.mqttv5.client:1.2.5")

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
            File("$buildDir/generated/source/proto/main/coroutines")
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
            File("$buildDir/generated/source/proto/main/coroutines")
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
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveBaseName.set(archivesBaseName)

        destinationDirectory.set(file("${buildDir}/output"))
        isZip64 = true
//        minimize {
//            exclude(dependency("commons-logging:commons-logging:.*"))
//        }

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<ProcessResources> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    withType<Jar> {
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Main-Class" to appMainClass,
                    "Class-Path" to configurations.runtimeClasspath.files.joinToString(" ") { "$libDirName/${it.name}" }
                )
            )
        }
    }

    register("jarWithDepends", Jar::class.java) {
        dependsOn("copyJarLibs")
        destinationDir = file("${buildDir}/output")
        baseName = applicationName
    }

    register("copyJarLibs", Copy::class.java) {
        doLast {
            into("${buildDir}/output/$libDirName")
            from(configurations.runtime)
        }
    }
}

// apply(from = "enableProGuard.gradle")
// apply(from = "enableGRPC.gradle")

nativeBuild {
    mainClass.set(appMainClass)
    debug.set(false) // Determines if debug info should be generated, defaults to false
    verbose.set(true) // Add verbose output, defaults to false
    fallback.set(false) // Sets the fallback mode of native-image, defaults to false
    sharedLibrary.set(false) // Determines if image is a shared library, defaults to false if `java-library` plugin isn't included


    // Advanced options
    buildArgs.addAll(
        "--report-unsupported-elements-at-runtime",
         "--allow-incomplete-classpath",
        "--enable-url-protocols=http",
        "-H:+ReportExceptionStackTraces"
    )
    // Passes '' to the native image builder options. This can be used to pass parameters which are not directly supported by this extension
    // jvmArgs.add("") // Passes 'flag' directly to the JVM running the native image builder

    // Development options
    // agent.set(false) // Enables the reflection agent. Can be also set on command line using '-Pagent'
    // useFatJar.set(true) // Instead of passing each jar individually, builds a fat jar
}