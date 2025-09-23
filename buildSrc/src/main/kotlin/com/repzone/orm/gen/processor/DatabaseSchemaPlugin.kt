package com.repzone.orm.gen.processor

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.api.provider.Property

class DatabaseSchemaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // KSP plugin'ini apply et
        project.plugins.apply("com.google.devtools.ksp")

        // Extension oluştur - project parametresi ile
        val extension = project.extensions.create("databaseSchema", DatabaseSchemaExtension::class.java, project)

        // Default values
        extension.databasePath.convention(project.rootProject.file("database.db").absolutePath)
        extension.packageName.convention("com.repzone.orm.database")

        // KSP configuration
        project.afterEvaluate {
            // KSP arguments set etme
            try {
                val kspExtension = project.extensions.findByName("ksp")
                if (kspExtension != null) {
                    // Reflection ile KSP arg method'unu çağır
                    val kspClass = kspExtension.javaClass
                    val argMethod = kspClass.getMethod("arg", String::class.java, String::class.java)
                    argMethod.invoke(kspExtension, "database.path", extension.databasePath.get())
                    argMethod.invoke(kspExtension, "database.packageName", extension.packageName.get())
                }
            } catch (e: Exception) {
                project.logger.error("Failed to configure KSP arguments: ${e.message}")
            }
        }
    }
}

// Extension sınıfı - Project parameter ile
open class DatabaseSchemaExtension(private val project: Project) {
    val databasePath: Property<String> = project.objects.property(String::class.java)
    val packageName: Property<String> = project.objects.property(String::class.java)
}