package com.repzone.orm.gen

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

// 1) Prune task'ımız
abstract class PruneSqlDelightSchemas : DefaultTask() {

    @get:InputDirectory
    abstract val schemaRoot: DirectoryProperty

    // Migration dosyalarının kökü (1.sqm, 2.sqm, ...)
    @get:InputDirectory
    abstract val migrationsRoot: DirectoryProperty
    @get:Input
    abstract val dryRun: Property<Boolean>

    @TaskAction
    fun prune() {
        val schemaRootDir = schemaRoot.get().asFile
        val migrationsRootDir = migrationsRoot.get().asFile

        if (!schemaRootDir.exists()) {
            logger.lifecycle("Prune: schema klasoru yok: $schemaRootDir — atliyorum.")
            return
        }

        // Şema kökü altındaki DB adları (her DB için ayrı klasör)
        val dbDirs = schemaRootDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
        if (dbDirs.isEmpty()) {
            logger.lifecycle("Prune: schema altında DB klasoru yok — atlıyorum.")
            return
        }

        fun maxMigrationVersion(dbName: String): Int {
            val dbMigDir = File(migrationsRootDir, dbName)
            if (!dbMigDir.exists()) return -1
            val versions = dbMigDir
                .listFiles { f -> f.isFile && f.extension.equals("sqm", ignoreCase = true) }
                ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
                .orEmpty()
            return versions.maxOrNull() ?: -1
        }

        val dbToVersion = dbDirs.associate { it.name to maxMigrationVersion(it.name) }
        val winner = dbToVersion.maxByOrNull { it.value }?.key ?: dbDirs.first().name
        val winnerVer = dbToVersion[winner] ?: -1

        logger.lifecycle("Prune: tutulacak DB = $winner (max migration = $winnerVer)")
        dbDirs.filter { it.name != winner }.forEach { dir ->
            if (dryRun.getOrElse(false)) {
                logger.lifecycle("Prune (dry-run): silinecek -> ${dir.absolutePath}")
            } else {
                dir.deleteRecursively()
                logger.lifecycle("Prune: silindi -> ${dir.absolutePath}")
            }
        }
    }
}