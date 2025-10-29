package com.tnvsai.yatramate.config.models

/**
 * Data class representing navigation keywords configuration
 */
data class NavigationKeywords(
    val version: String,
    val directions: Map<String, List<String>>,
    val maneuvers: List<String>,
    val distanceUnits: List<String>,
    val navigationKeywords: List<String>,
    val userAdditions: List<String>
)

/**
 * Helper class for working with direction keywords
 */
data class DirectionKeywords(
    val direction: String,
    val keywords: List<String>
)
