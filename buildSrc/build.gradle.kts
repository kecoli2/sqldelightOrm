
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.20-1.0.25")
    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
}

gradlePlugin {
    plugins {
        create("databaseSchemaPlugin") {
            id = "database-schema-generator"
            implementationClass = "DatabaseSchemaPlugin"
        }
    }
}
