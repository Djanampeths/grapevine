package com.grapevine.grapevine.matrix

import com.grapevine.grapevine.AppContext
import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import net.folivo.trixnity.client.store.repository.createInMemoryRepositoriesModule
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import java.io.File

// Trixnity's Room-backed repository (trixnity-client-repository-room) has never had a
// stable release for the 3.6.0 line this project is pinned to (only 4.x-SNAPSHOT builds
// exist), so there's no persistent store available for Android yet. Falling back to the
// in-memory repository module means Android sessions don't survive an app restart — JVM
// desktop is unaffected and keeps its Exposed/H2-backed persistent store. Swap this back
// to createRoomRepositoriesModule() once Trixnity ships a stable release with Room support.
actual suspend fun createRepositoriesModule(): Module = createInMemoryRepositoriesModule()

actual fun createMediaStore(): MediaStore =
    OkioMediaStore(File(AppContext.get().filesDir, "media").absolutePath.toPath())
