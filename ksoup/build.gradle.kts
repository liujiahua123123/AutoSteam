import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

group = "lazy.him188.sleep"
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

fun kotlinx(id: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$id:$version"

dependencies {
    implementation("io.ktor:ktor-server-netty:1.4.0")
    api(kotlinx("serialization-core", serializationVersion))
    api(kotlinx("serialization-json", serializationVersion))
    api("org.jsoup:jsoup:1.13.1")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}