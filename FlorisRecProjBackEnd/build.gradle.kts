buildscript {
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:12.3.0")
    }
}

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.jpa") version "2.3.20"
    id("org.flywaydb.flyway") version "12.3.0"
    application
}

group = "com.sirolf2009.grossrecipes"
version = "1.0-SNAPSHOT"

// Lets you start the server with `gradlew run` instead of needing an IDE.
// Kotlin compiles the top-level fun main() in Main.kt into a class called
// MainKt in the same package as the file.
application {
    mainClass.set("com.sirolf2009.grossrecipes.MainKt")
}

repositories {
    mavenCentral()
    mavenLocal()
    maven {
        name = "reposiliteRepositoryReleases"
        url = uri("http://sirolf2009.com:8080/releases")
        isAllowInsecureProtocol = true
    }
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.hibernate.orm:hibernate-core:7.1.3.Final")

    implementation("com.sirolf2009:modulith:0.7")
    implementation("com.sirolf2009.modulith:accounts:0.3")
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}

flyway {
    url = "jdbc:postgresql://localhost:5432/grossrecipes"
    user = "postgres"
    password = "example"
    locations = arrayOf("classpath:db/migration") // Explicitly set the location
    validateMigrationNaming = true
}