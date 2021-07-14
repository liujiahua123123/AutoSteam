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
    implementation("org.apache.sshd:sshd-scp:2.7.0")
    implementation("org.apache.sshd:sshd-sftp:2.7.0")
    implementation("org.apache.sshd:sshd-core:2.7.0")
    implementation("io.ktor:ktor-server-netty:1.6.0")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}