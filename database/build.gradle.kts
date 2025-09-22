import com.repzone.orm.gen.GenerateOrmRegistryFromSchema
import com.repzone.orm.gen.PruneSqlDelightSchemas
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.sqldelight)
}

kotlin {

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
                kotlin.srcDir("$buildDir/generated/source/ormregistry/commonMain")
                // Add KMP dependencies here
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
    }

    // Şema kökü
    val schemaDirProvider = layout.projectDirectory.dir("src/commonMain/sqldelight/schema")

    // En büyük n.db'yi bulan yardımcı
    fun findLatestDbOrNull(): File? {
        val root = schemaDirProvider.asFile
        if (!root.exists()) return null
        return root.walkTopDown()
            .filter { it.isFile && it.extension.equals("db", true) && it.nameWithoutExtension.all(Char::isDigit) }
            .maxByOrNull { it.nameWithoutExtension.toLong() }
    }

// RegularFile Provider: varsa gerçek dosya, yoksa "placeholder"
    val latestDbRegularFile: Provider<RegularFile> =
        providers.provider { findLatestDbOrNull() ?: File(schemaDirProvider.asFile, ".placeholder-missing.db") }
            .flatMap { f -> layout.file(providers.provider { f }) }

// ——— TASK ———
    val registryTask = tasks.register<GenerateOrmRegistryFromSchema>("generateOrmRegistryFromSchema") {
        // 1) Her durumda bir değer ata (Gradle memnun olur)
        schemaDb.set(latestDbRegularFile)

        // 2) Dosya yoksa task'ı çalıştırma
        onlyIf {
            val exists = latestDbRegularFile.get().asFile.exists()
            if (!exists) logger.lifecycle("[ORM] skip: no *.db found under ${schemaDirProvider.asFile}")
            exists
        }

        // Diğer ayarlar
        outputKotlinFile.set(
            layout.buildDirectory.file(
                "generated/source/ormregistry/commonMain/com/repzone/orm/generated/OrmRegistry_Generated.kt"
            )
        )

        // (opsiyonel) tip ipuçları
        typeHints.putAll(
            mapOf(
                "User.age" to "Int",
                "User.isActive" to "Bool"
            )
        )

        // (opsiyonel) tablo adı != sınıf adı ise
        ctorNameHints.putAll(
            mapOf(
                "User" to "User" // gerek yoksa boş bırak
            )
        )

        customConverters.putAll(
            mapOf(
                "User.birthDay" to "kotlinx.datetime.Instant.parse(%s)"
            )
        )

        generatedPackage.set("com.repzone.orm.generated")
        rowTypesPackage.set("com.repzone.db")
    }

// 4b) varsa SQLDelight interface task’larına da bağımlı yap (ad tabanlı, güvenli)
    val interfaceTasks = tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Interface") }
// TaskCollection'ı direkt dependsOn'a verebilirsin (lazy)
    registryTask.configure { dependsOn(interfaceTasks) }

// 5) derleme zinciri
    tasks.named("build").configure { dependsOn(registryTask) }
    tasks.named("assemble").configure { dependsOn(registryTask) }
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