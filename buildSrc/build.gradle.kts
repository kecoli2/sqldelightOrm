plugins { `kotlin-dsl` }

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
}