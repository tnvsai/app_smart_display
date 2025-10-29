package com.tnvsai.yatramate.notification.registry

import com.tnvsai.yatramate.config.ConfigManager
import com.tnvsai.yatramate.config.models.AppPattern
import android.util.Log

/**
 * Registry for managing app-specific notification parsers
 * Provides plugin-style architecture for supporting multiple navigation apps
 */
object ParserRegistry {
    private const val TAG = "ParserRegistry"
    
    private val parsers = mutableMapOf<String, AppParser>()
    
    /**
     * Register a parser for a specific app ID
     * @param appId Unique identifier for the app (must match config)
     * @param parser AppParser implementation
     */
    fun registerParser(appId: String, parser: AppParser) {
        parsers[appId] = parser
        Log.d(TAG, "Registered parser for app: $appId")
    }
    
    /**
     * Get parser for a specific package name
     * @param packageName Android package name
     * @return AppParser if found, null otherwise
     */
    fun getParserForPackage(packageName: String): AppParser? {
        val appPattern = ConfigManager.getAppForPackage(packageName)
        
        if (appPattern == null) {
            Log.d(TAG, "No app pattern found for package: $packageName")
            return null
        }
        
        val parser = parsers[appPattern.id]
        if (parser == null) {
            Log.w(TAG, "Parser not registered for app: ${appPattern.id}")
        }
        
        return parser
    }
    
    /**
     * Get all enabled apps from configuration
     * @return List of enabled AppPatterns
     */
    fun getAllEnabledApps(): List<AppPattern> {
        return ConfigManager.getAllApps().filter { it.enabled }
    }
    
    /**
     * Get all registered parser IDs
     * @return List of registered app IDs
     */
    fun getRegisteredAppIds(): List<String> {
        return parsers.keys.toList()
    }
    
    /**
     * Check if a parser is registered for the given app ID
     */
    fun isRegistered(appId: String): Boolean {
        return appId in parsers
    }
}
