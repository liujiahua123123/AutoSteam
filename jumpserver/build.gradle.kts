import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow")
}

group = "me.him188"
version = "1.0-SNAPSHOT"

application {
    @Suppress("DEPRECATION")
    mainClassName = "myproxy.Main"
    mainClass.set("myproxy.Main")
}

repositories {
    mavenCentral()
}

dependencies {
    val ktor = "1.6.0"
    api("io.ktor:ktor-server-netty:$ktor")
    api("io.ktor:ktor-client-okhttp:$ktor")
    api("org.slf4j:slf4j-simple:1.7.30")
    api(project(":ksoup"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}