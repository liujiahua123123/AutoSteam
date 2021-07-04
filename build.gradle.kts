import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

plugins {
    kotlin("jvm") version "1.4.30"
    application
    id("com.github.johnrengelman.shadow") version "6.1.0"
    kotlin("plugin.serialization") version "1.4.30"
}

fun kotlinx(id: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$id:$version"

group = "net.mamoe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://dl.bintray.com/kotlin/ktor")
    }
    maven {
        url = uri("https://dl.bintray.com/kotlin/kotlinx")
    }
}

val serializationVersion = "1.0.0"

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("io.ktor:ktor-server-netty:1.4.0")
    implementation("io.ktor:ktor-html-builder:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")
    implementation("com.github.davidmoten:subethasmtp:6.0.0")
    implementation("tech.blueglacier:email-mime-parser:1.0.5")
    //implementation("org.bouncycastle:bcprov-jdk15on:1.64")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.1.0-RC")

    api(kotlinx("serialization-core", serializationVersion))
    api(kotlinx("serialization-json", serializationVersion))
    api("org.jsoup:jsoup:1.13.1")

    api(project(":ksoup"))

    implementation("com.belerweb:pinyin4j:2.5.1")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=all"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

application {
    mainClassName = "Starter"
}
