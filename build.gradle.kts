plugins {
    kotlin("jvm") version "2.3.0"
    application
    id("com.gradleup.shadow") version "8.3.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.google.api-client:google-api-client:2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20220927-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("ersbot.MainKt")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveFileName.set("ers-bot.jar")

    manifest {
        attributes["Main-Class"] = "ersbot.MainKt"
    }

    mergeServiceFiles()
    append("META-INF/kotlin_module")
}

tasks.test {
    useJUnitPlatform()
}
