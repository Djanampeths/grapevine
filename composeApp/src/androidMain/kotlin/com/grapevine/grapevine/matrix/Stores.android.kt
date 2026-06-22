package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import okio.Path.Companion.toPath
import org.koin.core.module.Module

actual suspend fun createRepositoriesModule(): Module = createInMemoryRepositoriesModule()

actual fun createMediaStore(): MediaStore =
    OkioMediaStore("/data/data/com.grapevine.grapevine/files/media".toPath())
