package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import org.koin.core.module.Module

expect suspend fun createRepositoriesModule(): Module
expect fun createMediaStore(): MediaStore
