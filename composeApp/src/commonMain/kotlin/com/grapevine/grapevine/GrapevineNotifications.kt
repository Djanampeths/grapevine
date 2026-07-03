package com.grapevine.grapevine

expect object GrapevineNotifications {
    fun showMessageNotification(
        roomId: String,
        roomName: String,
        senderName: String,
        preview: String,
    )
}
