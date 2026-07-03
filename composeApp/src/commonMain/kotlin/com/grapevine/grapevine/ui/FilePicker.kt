package com.grapevine.grapevine.ui

import androidx.compose.runtime.Composable

data class PickedFile(val name: String, val mimeType: String, val bytes: ByteArray)

@Composable
expect fun rememberFilePicker(onPicked: (PickedFile?) -> Unit): () -> Unit
