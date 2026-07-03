package com.grapevine.grapevine

actual object GrapevineNotifications {
    actual fun showMessageNotification(
        roomId: String,
        roomName: String,
        senderName: String,
        preview: String,
    ) {
        println("GV-NOTIFICATION [$roomName] $senderName: $preview")
    }
}
