package com.tnvsai.yatramate.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.tnvsai.yatramate.config.models.NotificationTypesConfig
import org.json.JSONObject

/**
 * Handles persistence of notification configuration state
 */
object ConfigPersistence {
    private const val TAG = "ConfigPersistence"
    private const val PREFS_NAME = "yatramate_notification_config"
    private const val KEY_ENABLED_TYPES = "enabled_types"
    private const val KEY_ENABLED_APPS = "enabled_apps"
    private const val KEY_DISABLED_APPS = "disabled_apps"
    private const val KEY_CONFIG_VERSION = "config_version"
    
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    
    /**
     * Initialize with application context
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "ConfigPersistence initialized")
    }
    
    /**
     * Save enabled notification types
     */
    fun saveEnabledTypes(types: Set<String>) {
        try {
            val jsonArray = gson.toJson(types.toList())
            prefs.edit().putString(KEY_ENABLED_TYPES, jsonArray).apply()
            Log.d(TAG, "Saved enabled types: ${types.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving enabled types: ${e.message}", e)
        }
    }
    
    /**
     * Load enabled notification types
     */
    fun loadEnabledTypes(): Set<String> {
        return try {
            val jsonArray = prefs.getString(KEY_ENABLED_TYPES, null)
            if (jsonArray != null) {
                val list = gson.fromJson(jsonArray, Array<String>::class.java).toSet()
                Log.d(TAG, "Loaded enabled types: ${list.size}")
                list
            } else {
                emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading enabled types: ${e.message}", e)
            emptySet()
        }
    }
    
    /**
     * Save enabled apps per notification type
     * Format: Map<typeId, Set<packageName>>
     */
    fun saveEnabledApps(apps: Map<String, Set<String>>) {
        try {
            val jsonObject = JSONObject()
            apps.forEach { (typeId, packageNames) ->
                jsonObject.put(typeId, gson.toJson(packageNames.toList()))
            }
            prefs.edit().putString(KEY_ENABLED_APPS, jsonObject.toString()).apply()
            Log.d(TAG, "Saved enabled apps for ${apps.size} types")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving enabled apps: ${e.message}", e)
        }
    }
    
    /**
     * Load enabled apps per notification type
     */
    fun loadEnabledApps(): Map<String, Set<String>> {
        return try {
            val jsonString = prefs.getString(KEY_ENABLED_APPS, null)
            if (jsonString != null) {
                val jsonObject = JSONObject(jsonString)
                val result = mutableMapOf<String, Set<String>>()
                jsonObject.keys().forEach { typeId ->
                    val appsJson = jsonObject.getString(typeId)
                    val apps = gson.fromJson(appsJson, Array<String>::class.java).toSet()
                    result[typeId] = apps
                }
                Log.d(TAG, "Loaded enabled apps for ${result.size} types")
                result
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading enabled apps: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Save disabled apps per notification type
     */
    fun saveDisabledApps(apps: Map<String, Set<String>>) {
        try {
            val jsonObject = JSONObject()
            apps.forEach { (typeId, packageNames) ->
                jsonObject.put(typeId, gson.toJson(packageNames.toList()))
            }
            prefs.edit().putString(KEY_DISABLED_APPS, jsonObject.toString()).apply()
            Log.d(TAG, "Saved disabled apps for ${apps.size} types")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving disabled apps: ${e.message}", e)
        }
    }
    
    /**
     * Load disabled apps per notification type
     */
    fun loadDisabledApps(): Map<String, Set<String>> {
        return try {
            val jsonString = prefs.getString(KEY_DISABLED_APPS, null)
            if (jsonString != null) {
                val jsonObject = JSONObject(jsonString)
                val result = mutableMapOf<String, Set<String>>()
                jsonObject.keys().forEach { typeId ->
                    val appsJson = jsonObject.getString(typeId)
                    val apps = gson.fromJson(appsJson, Array<String>::class.java).toSet()
                    result[typeId] = apps
                }
                Log.d(TAG, "Loaded disabled apps for ${result.size} types")
                result
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading disabled apps: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Export full configuration as JSON string
     */
    fun exportConfiguration(): String {
        return try {
            val config = mapOf(
                "enabledTypes" to loadEnabledTypes(),
                "enabledApps" to loadEnabledApps(),
                "disabledApps" to loadDisabledApps(),
                "version" to (prefs.getString(KEY_CONFIG_VERSION, "1.0") ?: "1.0")
            )
            gson.toJson(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting configuration: ${e.message}", e)
            "{}"
        }
    }
    
    /**
     * Import configuration from JSON string
     */
    fun importConfiguration(json: String) {
        try {
            val config = gson.fromJson(json, Map::class.java) as? Map<*, *>
            if (config != null) {
                config["enabledTypes"]?.let {
                    val types = (it as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
                    saveEnabledTypes(types)
                }
                config["enabledApps"]?.let {
                    val apps = (it as? Map<*, *>)?.mapNotNull { entry ->
                        val typeId = entry.key as? String
                        val packageNames = (entry.value as? List<*>)?.mapNotNull { it as? String }?.toSet()
                        if (typeId != null && packageNames != null) typeId to packageNames else null
                    }?.toMap() ?: emptyMap()
                    saveEnabledApps(apps)
                }
                config["disabledApps"]?.let {
                    val apps = (it as? Map<*, *>)?.mapNotNull { entry ->
                        val typeId = entry.key as? String
                        val packageNames = (entry.value as? List<*>)?.mapNotNull { it as? String }?.toSet()
                        if (typeId != null && packageNames != null) typeId to packageNames else null
                    }?.toMap() ?: emptyMap()
                    saveDisabledApps(apps)
                }
                Log.i(TAG, "Configuration imported successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error importing configuration: ${e.message}", e)
        }
    }
    
    /**
     * Reset configuration to defaults
     */
    fun resetToDefaults() {
        try {
            prefs.edit().clear().apply()
            Log.i(TAG, "Configuration reset to defaults")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting configuration: ${e.message}", e)
        }
    }
    
    /**
     * Clear all persisted configuration
     */
    fun clear() {
        resetToDefaults()
    }
}

