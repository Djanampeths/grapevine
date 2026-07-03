package com.grapevine.grapevine.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import java.net.URLConnection
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return {
        Thread {
            val chooser = JFileChooser()
            val result = chooser.showOpenDialog(null)
            val picked = if (result == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                try {
                    val bytes = file.readBytes()
                    val mime = URLConnection.guessContentTypeFromName(file.name)
                        ?: "application/octet-stream"
                    PickedFile(file.name, mime, bytes)
                } catch (_: Exception) { null }
            } else null
            SwingUtilities.invokeLater { callback.value(picked) }
        }.apply { isDaemon = true }.start()
    }
}
