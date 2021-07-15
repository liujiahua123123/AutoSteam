import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
    kotlin("plugin.serialization")
}

group = "mamoe.naturalhg"
version = "1.0-SNAPSHOT"

/*
application {
    @Suppress("DEPRECATION")
    mainClassName = "myproxy.Main"
    mainClass.set("myproxy.Main")
}

 */

repositories {
    mavenCentral()
}

dependencies {
    val ktor = "1.6.0"
    api("io.ktor:ktor-server-netty:$ktor")
    api("org.slf4j:slf4j-simple:1.7.30")
    api("org.xerial:sqlite-jdbc:3.23.1")
    implementation("org.jetbrains.exposed:exposed:0.17.13")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.32.1")
    api(project(":ksoup"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}