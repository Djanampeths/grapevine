package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import okio.Path.Companion.toPath
import java.io.File

actual fun createMediaStore(): MediaStore {
    val mediaDir = File(System.getProperty("user.home"), ".grapevine/media")
    mediaDir.mkdirs()
    return OkioMediaStore(mediaDir.absolutePath.toPath())
}
