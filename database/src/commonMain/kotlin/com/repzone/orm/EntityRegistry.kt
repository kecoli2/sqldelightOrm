package com.repzone.orm

object EntityRegistry {
    private val metadataMap = mutableMapOf<String, EntityMetadata<*>>()

    internal fun <T> registerInternal(className: String, metadata: EntityMetadata<T>) {
        metadataMap[className] = metadata
    }

    private inline fun <reified T> getMetadata(): EntityMetadata<T> {
        val className = T::class.simpleName!!
        return metadataMap[className] as? EntityMetadata<T>
            ?: throw IllegalArgumentException("No metadata found for $className")
    }

    fun initialize() {
        //com.repzone.orm.database.Generated_EntityRegistration.registerAll()
    }
}