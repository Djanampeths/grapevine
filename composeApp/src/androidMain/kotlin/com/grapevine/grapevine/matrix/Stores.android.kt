package com.grapevine.grapevine.matrix

import net.folivo.trixnity.client.media.MediaStore
import net.folivo.trixnity.client.media.okio.OkioMediaStore
import okio.Path.Companion.toPath

actual fun createMediaStore(): MediaStore =
    OkioMediaStore("/data/data/com.grapevine.grapevine/files/media".toPath())
