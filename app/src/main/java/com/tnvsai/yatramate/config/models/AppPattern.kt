package com.tnvsai.yatramate.config.models

/**
 * Data class representing an app pattern for navigation detection
 */
data class AppPattern(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val packageNames: List<String>,
    val detectionKeywords: List<String>,
    val titlePatterns: List<String>
)

/**
 * Data class for the complete app patterns configuration
 */
data class AppPatternsConfig(
    val version: String,
    val apps: List<AppPattern>,
    val userApps: List<AppPattern>
) {
    fun getAllApps(): List<AppPattern> = apps + userApps
    
    fun getEnabledApps(): List<AppPattern> = getAllApps().filter { it.enabled }
}
