import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

group = "net.mamoe"
version = "1.0-SNAPSHOT"


repositories {
    mavenCentral()
}

dependencies {
    val ktor = "1.6.0"
    api(project(":ksoup"))
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}