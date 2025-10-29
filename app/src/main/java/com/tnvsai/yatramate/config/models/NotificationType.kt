package com.tnvsai.yatramate.config.models

data class NotificationType(
    val id: String,
    val name: String,
    val priority: String,
    val enabled: Boolean = true,  // Enable/disable this notification type
    val keywords: List<String>,
    val apps: List<String>,
    val enabledApps: List<String> = emptyList(),  // Specific apps to enable (if empty, all apps enabled)
    val disabledApps: List<String> = emptyList(),  // Specific apps to disable
    val titlePatterns: List<String>,
    val mcuType: String
)

data class NotificationTypesConfig(
    val version: String,
    val notificationTypes: List<NotificationType>,
    val userTypes: List<NotificationType>,
    val settings: NotificationConfigSettings? = null
)

data class NotificationConfigSettings(
    val enableAllByDefault: Boolean = true,
    val sendToMCUWhenDisabled: Boolean = false
)
