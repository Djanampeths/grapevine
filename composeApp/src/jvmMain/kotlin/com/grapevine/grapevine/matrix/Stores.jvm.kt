package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.store.repository.exposed.createExposedRepositoriesModule
import okio.Path.Companion.toPath
import org.jetbrains.exposed.sql.Database
import org.koin.core.module.Module
import java.io.File

actual suspend fun createRepositoriesModule(): Module {
    val appDir = File(System.getProperty("user.home"), ".grapevine")
    appDir.mkdirs()
    val db = Database.connect(
        url = "jdbc:h2:file:${appDir.absolutePath}/session",
        driver = "org.h2.Driver",
    )
    return createExposedRepositoriesModule(db)
}

actual fun createMediaStore(): MediaStore {
    val mediaDir = File(System.getProperty("user.home"), ".grapevine/media")
    mediaDir.mkdirs()
    return OkioMediaStore(mediaDir.absolutePath.toPath())
}
