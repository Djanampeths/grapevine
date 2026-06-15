package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.okio.createOkioMediaStoreModule
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module

actual suspend fun createRepositoriesModule(): Module {
    val db = Database.connect(
        url = "jdbc:h2:mem:grapevine;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver"
    )
    return createExposedRepositoriesModule(db)
}

actual fun createMediaStoreModule(): Module =
    createOkioMediaStoreModule("/data/data/com.grapevine.grapevine/files/media".toPath())
