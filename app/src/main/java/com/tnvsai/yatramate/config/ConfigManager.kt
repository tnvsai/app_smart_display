package com.tnvsai.yatramate.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.tnvsai.yatramate.config.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Central configuration manager for YatraMate
 * Manages loading and saving of all configuration files
 */
object ConfigManager {
    private const val TAG = "ConfigManager"
    private const val PREFS_NAME = "yatramate_config"
    
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private val configMutex = Mutex()
    
    // Cached configurations
    private var navigationKeywords: NavigationKeywords? = null
    private var appPatterns: AppPatternsConfig? = null
    private var deviceProfiles: DeviceProfilesConfig? = null
    private var mcuFormats: MCUFormatsConfig? = null
    private var notificationTypes: NotificationTypesConfig? = null
    
    /**
     * Initialize the ConfigManager with application context
     * Must be called before using any other methods
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load default configurations
        loadDefaultConfigurations()
        
        Log.i(TAG, "ConfigManager initialized")
    }
    
    /**
     * Load default configurations from assets
     */
    private fun loadDefaultConfigurations() {
        navigationKeywords = loadNavigationKeywords()
        appPatterns = loadAppPatterns()
        deviceProfiles = loadDeviceProfiles()
        mcuFormats = loadMCUFormats()
        notificationTypes = loadNotificationTypes()
    }
    
    /**
     * Load navigation keywords from assets or SharedPreferences
     */
    private fun loadNavigationKeywords(): NavigationKeywords? {
        return try {
            // Try to load from SharedPreferences first (user modifications)
            val prefsJson = prefs.getString("navigation_keywords", null)
            if (prefsJson != null) {
                gson.fromJson(prefsJson, NavigationKeywords::class.java).also {
                    Log.d(TAG, "Loaded navigation keywords from preferences")
                }
            } else {
                // Load from assets
                val json = context.assets.open("config/navigation_keywords.json")
                    .bufferedReader().use { it.readText() }
                val config = gson.fromJson(json, NavigationKeywords::class.java)
                
                // Save to preferences for future use
                prefs.edit().putString("navigation_keywords", json).apply()
                
                config.also { Log.d(TAG, "Loaded navigation keywords from assets") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading navigation keywords: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load app patterns from assets or SharedPreferences
     */
    private fun loadAppPatterns(): AppPatternsConfig? {
        return try {
            val prefsJson = prefs.getString("app_patterns", null)
            if (prefsJson != null) {
                gson.fromJson(prefsJson, AppPatternsConfig::class.java).also {
                    Log.d(TAG, "Loaded app patterns from preferences")
                }
            } else {
                val json = context.assets.open("config/app_patterns.json")
                    .bufferedReader().use { it.readText() }
                val config = gson.fromJson(json, AppPatternsConfig::class.java)
                
                prefs.edit().putString("app_patterns", json).apply()
                
                config.also { Log.d(TAG, "Loaded app patterns from assets") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading app patterns: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load device profiles from assets or SharedPreferences
     */
    private fun loadDeviceProfiles(): DeviceProfilesConfig? {
        return try {
            val prefsJson = prefs.getString("device_profiles", null)
            if (prefsJson != null) {
                gson.fromJson(prefsJson, DeviceProfilesConfig::class.java).also {
                    Log.d(TAG, "Loaded device profiles from preferences")
                }
            } else {
                val json = context.assets.open("config/device_profiles.json")
                    .bufferedReader().use { it.readText() }
                val config = gson.fromJson(json, DeviceProfilesConfig::class.java)
                
                prefs.edit().putString("device_profiles", json).apply()
                
                config.also { Log.d(TAG, "Loaded device profiles from assets") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading device profiles: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load MCU formats from assets or SharedPreferences
     */
    private fun loadMCUFormats(): MCUFormatsConfig? {
        return try {
            val prefsJson = prefs.getString("mcu_formats", null)
            if (prefsJson != null) {
                gson.fromJson(prefsJson, MCUFormatsConfig::class.java).also {
                    Log.d(TAG, "Loaded MCU formats from preferences")
                }
            } else {
                val json = context.assets.open("config/mcu_formats.json")
                    .bufferedReader().use { it.readText() }
                val config = gson.fromJson(json, MCUFormatsConfig::class.java)
                
                prefs.edit().putString("mcu_formats", json).apply()
                
                config.also { Log.d(TAG, "Loaded MCU formats from assets") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MCU formats: ${e.message}", e)
            null
        }
    }
    
    /**
     * Load notification types from assets or SharedPreferences
     */
    private fun loadNotificationTypes(): NotificationTypesConfig? {
        return try {
            // Always load from assets to get latest version (for development)
            // In production, you might want to check for updates or use preferences cache
            val json = context.assets.open("config/notification_types.json")
                .bufferedReader().use { it.readText() }
            val config = gson.fromJson(json, NotificationTypesConfig::class.java)
            
            // Save to preferences for reference
            prefs.edit().putString("notification_types", json).apply()
            
            val typesCount = config.notificationTypes.size + config.userTypes.size
            Log.i(TAG, "✅ Loaded notification types from assets: $typesCount types")
            config.notificationTypes.forEach { type ->
                Log.d(TAG, "  - ${type.id}: ${type.apps.size} apps, ${type.keywords.size} keywords")
            }
            
            config
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading notification types: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get navigation keywords configuration
     */
    fun getNavigationKeywords(): NavigationKeywords {
        return navigationKeywords ?: NavigationKeywords(
            version = "1.0",
            directions = emptyMap(),
            maneuvers = emptyList(),
            distanceUnits = emptyList(),
            navigationKeywords = emptyList(),
            userAdditions = emptyList()
        )
    }
    
    /**
     * Get all app patterns
     */
    fun getAllApps(): List<AppPattern> {
        return appPatterns?.getAllApps() ?: emptyList()
    }
    
    /**
     * Get app pattern for a specific package name
     */
    fun getAppForPackage(packageName: String): AppPattern? {
        return appPatterns?.getAllApps()?.firstOrNull { app ->
            app.packageNames.contains(packageName) && app.enabled
        }
    }
    
    /**
     * Get all device profiles
     */
    fun getAllDeviceProfiles(): List<DeviceProfile> {
        return deviceProfiles?.profiles ?: emptyList()
    }
    
    /**
     * Get active device profile
     */
    fun getActiveDeviceProfile(): DeviceProfile? {
        val activeProfileId = deviceProfiles?.activeProfile
        
        return when (activeProfileId) {
            "auto_detect" -> autoDetectDeviceProfile()
            null -> autoDetectDeviceProfile()
            else -> deviceProfiles?.profiles?.find { it.id == activeProfileId }
        }
    }
    
    /**
     * Auto-detect device profile based on manufacturer
     */
    fun autoDetectDeviceProfile(): DeviceProfile? {
        val manufacturer = Build.MANUFACTURER
        Log.d(TAG, "Auto-detecting device profile for manufacturer: $manufacturer")
        
        return deviceProfiles?.profiles?.firstOrNull { profile ->
            profile.manufacturers.any { it.equals(manufacturer, ignoreCase = true) }
        } ?: deviceProfiles?.profiles?.find { it.id == "generic" }
    }
    
    /**
     * Get profile for specific manufacturer
     */
    fun getProfileForManufacturer(manufacturer: String): DeviceProfile? {
        return deviceProfiles?.profiles?.firstOrNull { profile ->
            profile.manufacturers.any { it.equals(manufacturer, ignoreCase = true) }
        }
    }
    
    /**
     * Set active device profile
     */
    fun setActiveProfile(profileId: String) {
        deviceProfiles?.let {
            val updated = it.copy(activeProfile = profileId)
            saveDeviceProfiles(updated)
            deviceProfiles = updated
            Log.i(TAG, "Set active profile to: $profileId")
        }
    }
    
    /**
     * Get active MCU format configuration
     */
    fun getActiveMCUFormat(): MCUFormat? {
        val activeFormatId = mcuFormats?.activeFormat
        return mcuFormats?.formats?.get(activeFormatId)
    }
    
    /**
     * Get active transformer instance based on configured MCU format
     */
    fun getActiveTransformer(): com.tnvsai.yatramate.mcu.DataTransformer {
        val format = getActiveMCUFormat() ?: run {
            Log.w(TAG, "No active MCU format found, using default ESP32 format")
            mcuFormats?.formats?.values?.firstOrNull()
        }
        
        return if (format != null) {
            com.tnvsai.yatramate.mcu.transformers.ESP32Transformer(format)
        } else {
            // Fallback to default transformer with empty config
            com.tnvsai.yatramate.mcu.transformers.ESP32Transformer(
                MCUFormat(
                    name = "Default",
                    maxPayload = 512,
                    directionMapping = emptyMap(),
                    callStateMapping = emptyMap()
                )
            )
        }
    }
    
    /**
     * Save device profiles configuration
     */
    private fun saveDeviceProfiles(config: DeviceProfilesConfig) {
        try {
            val json = gson.toJson(config)
            prefs.edit().putString("device_profiles", json).apply()
            Log.d(TAG, "Saved device profiles")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving device profiles: ${e.message}", e)
        }
    }
    
    /**
     * Save navigation keywords configuration
     */
    fun saveNavigationKeywords(config: NavigationKeywords) {
        try {
            val json = gson.toJson(config)
            prefs.edit().putString("navigation_keywords", json).apply()
            navigationKeywords = config
            Log.d(TAG, "Saved navigation keywords")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving navigation keywords: ${e.message}", e)
        }
    }
    
    /**
     * Add user keyword to navigation configuration
     */
    fun addUserKeyword(direction: String, keyword: String) {
        val motions = getNavigationKeywords()
        val directionKeywords = motions.directions[direction] ?: emptyList()
        val updatedKeywords = directionKeywords + keyword
        
        val updatedDirections = motions.directions + mapOf(direction to updatedKeywords)
        val updated = motions.copy(directions = updatedDirections)
        
        saveNavigationKeywords(updated)
        Log.i(TAG, "Added user keyword: $keyword for direction: $direction")
    }
    
    /**
     * Get all notification types (built-in + user-defined)
     */
    fun getAllNotificationTypes(): List<NotificationType> {
        // Defensive check: Ensure we're initialized
        if (!::context.isInitialized) {
            Log.e(TAG, "❌ ConfigManager.getAllNotificationTypes() called before initialize()!")
            Log.e(TAG, "   Attempting to load notification types directly from assets...")
            
            // Last resort: try to load directly if we have a context somehow
            return emptyList()  // Can't load without context
        }
        
        // CRITICAL: If notificationTypes is null, try loading it now
        if (notificationTypes == null) {
            Log.w(TAG, "⚠️ notificationTypes is null! Attempting to reload...")
            notificationTypes = loadNotificationTypes()
            if (notificationTypes == null) {
                Log.e(TAG, "❌ Failed to load notification types even after retry!")
                return emptyList()
            }
            Log.i(TAG, "✅ Successfully loaded notification types on demand")
        }
        
        val builtInTypes = notificationTypes?.notificationTypes ?: emptyList()
        val userTypes = notificationTypes?.userTypes ?: emptyList()
        val allTypes = builtInTypes + userTypes
        
        Log.i(TAG, "getAllNotificationTypes: ${builtInTypes.size} built-in + ${userTypes.size} user = ${allTypes.size} total")
        
        if (allTypes.isEmpty()) {
            Log.e(TAG, "❌ WARNING: getAllNotificationTypes returned empty list!")
            Log.e(TAG, "   notificationTypes is null: ${notificationTypes == null}")
            Log.e(TAG, "   builtInTypes.size: ${builtInTypes.size}")
            Log.e(TAG, "   userTypes.size: ${userTypes.size}")
        }
        
        return allTypes
    }
    
    /**
     * Get notification types configuration
     */
    fun getNotificationTypesConfig(): NotificationTypesConfig? {
        return notificationTypes
    }
    
    /**
     * Get enabled notification types (from config files)
     * Note: Runtime enable/disable state is managed by NotificationConfigManager
     */
    fun getEnabledNotificationTypes(): List<NotificationType> {
        return getAllNotificationTypes().filter { it.enabled }
    }
    
    /**
     * Get active notification types (enabled and respecting app filters)
     * This respects both config file settings and runtime state
     */
    fun getActiveNotificationTypes(): List<NotificationType> {
        return getEnabledNotificationTypes() // Base filtering
            // Further filtering by NotificationConfigManager at runtime
    }
    
    /**
     * Get notification type by ID
     */
    fun getNotificationTypeById(id: String): NotificationType? {
        return getAllNotificationTypes().find { it.id == id }
    }
    
    /**
     * Update notification type enabled state in config
     */
    fun updateNotificationTypeEnabled(typeId: String, enabled: Boolean) {
        val currentConfig = notificationTypes ?: return
        val allTypes = getAllNotificationTypes()
        
        val updatedBuiltInTypes = currentConfig.notificationTypes.map { type ->
            if (type.id == typeId) type.copy(enabled = enabled) else type
        }
        
        val updatedUserTypes = currentConfig.userTypes.map { type ->
            if (type.id == typeId) type.copy(enabled = enabled) else type
        }
        
        val updatedConfig = currentConfig.copy(
            notificationTypes = updatedBuiltInTypes,
            userTypes = updatedUserTypes
        )
        
        saveNotificationTypes(updatedConfig)
        notificationTypes = updatedConfig
        Log.i(TAG, "Updated notification type $typeId enabled=$enabled")
    }
    
    /**
     * Update app enabled/disabled state for a notification type
     */
    fun updateAppEnabledForType(typeId: String, packageName: String, enabled: Boolean) {
        val currentConfig = notificationTypes ?: return
        val allTypes = getAllNotificationTypes()
        val type = allTypes.find { it.id == typeId } ?: return
        
        val updatedType = if (enabled) {
            // Add to enabledApps, remove from disabledApps
            val newEnabledApps = if (type.enabledApps.isEmpty()) {
                listOf(packageName) // First enabled app creates whitelist
            } else {
                type.enabledApps + packageName
            }
            type.copy(
                enabledApps = newEnabledApps.distinct(),
                disabledApps = type.disabledApps - packageName
            )
        } else {
            // Add to disabledApps, remove from enabledApps
            val newDisabledApps = type.disabledApps + packageName
            type.copy(
                disabledApps = newDisabledApps.distinct(),
                enabledApps = type.enabledApps - packageName
            )
        }
        
        // Update in appropriate list
        val updatedBuiltInTypes = if (currentConfig.notificationTypes.any { it.id == typeId }) {
            currentConfig.notificationTypes.map { if (it.id == typeId) updatedType else it }
        } else {
            currentConfig.notificationTypes
        }
        
        val updatedUserTypes = if (currentConfig.userTypes.any { it.id == typeId }) {
            currentConfig.userTypes.map { if (it.id == typeId) updatedType else it }
        } else {
            currentConfig.userTypes
        }
        
        val updatedConfig = currentConfig.copy(
            notificationTypes = updatedBuiltInTypes,
            userTypes = updatedUserTypes
        )
        
        saveNotificationTypes(updatedConfig)
        notificationTypes = updatedConfig
        Log.i(TAG, "Updated app $packageName for type $typeId enabled=$enabled")
    }
    
    /**
     * Add user-defined notification type
     */
    fun addUserNotificationType(type: NotificationType) {
        val currentConfig = notificationTypes ?: NotificationTypesConfig(
            version = "1.0",
            notificationTypes = emptyList(),
            userTypes = emptyList()
        )
        
        val updatedUserTypes = currentConfig.userTypes + type
        val updatedConfig = currentConfig.copy(userTypes = updatedUserTypes)
        
        saveNotificationTypes(updatedConfig)
        notificationTypes = updatedConfig
        Log.i(TAG, "Added user notification type: ${type.name}")
    }
    
    /**
     * Save notification types configuration
     */
    private fun saveNotificationTypes(config: NotificationTypesConfig) {
        try {
            val json = gson.toJson(config)
            prefs.edit().putString("notification_types", json).apply()
            Log.d(TAG, "Saved notification types")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving notification types: ${e.message}", e)
        }
    }
    
    /**
     * Reload configurations (useful after external modifications)
     */
    fun reloadConfigurations() {
        loadDefaultConfigurations()
        Log.i(TAG, "Reloaded all configurations")
    }
}
