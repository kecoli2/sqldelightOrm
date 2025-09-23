import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvm("codegen") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        withJava()
    }

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "com.repzone.database"
        compileSdk = 36
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)

    }

    /*compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }*/




    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "databaseKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
                implementation(libs.sqldelight.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.koin.core)
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
                kotlin.srcDir("$buildDir/generated/ormMeta/commonMain")

            }
        }

        androidMain {
            dependencies {
                implementation(libs.koin.android)
                dependencies { implementation(libs.sqldelight.androidDriver) }
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
            }
        }

        iosMain {
            dependencies {
                dependencies { implementation(libs.sqldelight.nativeDriver) }
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }

        val codegenMain by getting {
            dependencies {
                implementation("org.xerial:sqlite-jdbc:3.46.0.0")
                // KotlinPoet istersen:
                // implementation("com.squareup:kotlinpoet:1.16.0")
            }
        }
    }
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.repzone.database")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/schema"))
            migrationOutputDirectory.set(file("src/commonMain/sqldelight/migrations"))
        }
        linkSqlite = true
    }
}

// codegen target & compilation referansları
val codegenTarget = kotlin.targets.getByName("codegen") as KotlinJvmTarget
val codegenCompilation = codegenTarget.compilations.getByName("main")

val generateOrmMeta = tasks.register<JavaExec>("generateOrmMeta") {
    group = "orm"
    description = "Generate ORM meta from SQLDelight schema"

    // Önce codegen kaynaklarını derle
    dependsOn(codegenCompilation.compileTaskProvider)

    // Main class: top-level fun main -> SchemaIntrospectorKt
    mainClass.set("com.repzone.database.tools.schema.SchemaIntrospectorKt")

    // *** ÖNEMLİ: classpath = derleme çıktıları + runtime bağımlılıkları ***
    classpath(
        codegenCompilation.output.allOutputs,        // build/classes/kotlin/codegen/main/...
        codegenCompilation.runtimeDependencyFiles    // sqlite-jdbc vs.
    )

    // Argümanlar
    val sqRoot = layout.projectDirectory.dir("src/commonMain/sqldelight").asFile.absolutePath
    val outDir = layout.buildDirectory
        .dir("generated/ormMeta/commonMain/com/repzone/database/orm/generated")
        .get().asFile.absolutePath
    val pkg = "com.repzone.database.orm.generated"

    args("--sqldelightRoot=$sqRoot", "--out=$outDir", "--pkg=$pkg", "--sqPkg=com.repzone.database")
}

// commonMain derlemesi öncesi meta üretimi çalışsın
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    if (name.contains("CommonMain", ignoreCase = true)) {
        dependsOn(generateOrmMeta)
    }
}