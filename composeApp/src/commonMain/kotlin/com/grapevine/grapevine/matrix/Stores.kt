package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import org.koin.core.module.Module

fun createRepositoriesModule(): Module = createInMemoryRepositoriesModule()

expect fun createMediaStore(): MediaStore
