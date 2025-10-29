package com.tnvsai.yatramate.config

import android.util.Log
import com.tnvsai.yatramate.config.models.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages runtime state of notification type and app enable/disable settings
 * Provides reactive StateFlow for UI updates
 */
object NotificationConfigManager {
    private const val TAG = "NotificationConfigManager"
    
    private lateinit var context: android.content.Context
    
    // Runtime state - tracks which types are enabled
    private val _enabledTypes = MutableStateFlow<Set<String>>(emptySet())
    val enabledTypes: StateFlow<Set<String>> = _enabledTypes.asStateFlow()
    
    // Runtime state - tracks which apps are explicitly enabled per type
    // Format: Map<typeId, Set<packageName>>
    // Empty set means all apps are enabled (use enabledApps if empty)
    private val _enabledApps = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val enabledApps: StateFlow<Map<String, Set<String>>> = _enabledApps.asStateFlow()
    
    // Runtime state - tracks which apps are disabled per type
    // Format: Map<typeId, Set<packageName>>
    private val _disabledApps = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val disabledApps: StateFlow<Map<String, Set<String>>> = _disabledApps.asStateFlow()
    
    /**
     * Initialize and load configuration from persistence
     */
    fun initialize(appContext: android.content.Context? = null) {
        appContext?.let { context = it.applicationContext }
        loadFromConfig()
        Log.i(TAG, "NotificationConfigManager initialized")
    }
    
    /**
     * Check if a notification type is enabled
     */
    fun isTypeEnabled(typeId: String): Boolean {
        val isEnabled = _enabledTypes.value.contains(typeId)
        Log.d(TAG, "isTypeEnabled($typeId) = $isEnabled (enabledTypes=${_enabledTypes.value})")
        return isEnabled
    }
    
    /**
     * Check if an app is enabled for a specific notification type
     * 
     * Logic:
     * 1. If type is disabled, return false
     * 2. If type has enabledApps (non-empty), check if packageName is in it
     * 3. If type has disabledApps (non-empty), check if packageName is NOT in it
     * 4. If no app-specific config, check the NotificationType config from ConfigManager
     * 5. Default to enabled
     */
    fun isAppEnabledForType(typeId: String, packageName: String): Boolean {
        // First check: Is the type enabled? 
        // BUT: If _enabledTypes is empty (first run), allow all types
        val typeEnabled = if (_enabledTypes.value.isEmpty()) {
            Log.d(TAG, "⚠️ _enabledTypes is empty, allowing type $typeId (first run)")
            true
        } else {
            isTypeEnabled(typeId)
        }
        
        if (!typeEnabled) {
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = false (type disabled)")
            return false
        }
        
        // Get the notification type config
        val typeConfig = ConfigManager.getNotificationTypeById(typeId) ?: run {
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = true (type config not found, defaulting)")
            return true
        }
        
        // Runtime overrides (from UI/manual changes)
        val runtimeEnabledApps = _enabledApps.value[typeId]
        val runtimeDisabledApps = _disabledApps.value[typeId]
        
        // Check runtime disabled apps first (highest priority)
        if (runtimeDisabledApps?.contains(packageName) == true) {
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = false (runtime disabled)")
            return false
        }
        
        // Check runtime enabled apps (whitelist)
        if (runtimeEnabledApps != null && runtimeEnabledApps.isNotEmpty()) {
            val isEnabled = runtimeEnabledApps.contains(packageName)
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = $isEnabled (runtime enabled apps check)")
            return isEnabled
        }
        
        // Fall back to config file settings
        if (typeConfig.disabledApps.contains(packageName)) {
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = false (disabled in config)")
            return false
        }
        
        if (typeConfig.enabledApps.isNotEmpty()) {
            val isEnabled = typeConfig.enabledApps.contains(packageName)
            Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = $isEnabled (enabledApps check)")
            return isEnabled
        }
        
        // Default: enabled if no restrictions
        Log.d(TAG, "isAppEnabledForType($typeId, $packageName) = true (default - no restrictions)")
        return true
    }
    
    /**
     * Enable a notification type
     */
    fun enableType(typeId: String) {
        _enabledTypes.update { it + typeId }
        saveToConfig()
        Log.d(TAG, "Enabled notification type: $typeId")
    }
    
    /**
     * Disable a notification type
     */
    fun disableType(typeId: String) {
        _enabledTypes.update { it - typeId }
        saveToConfig()
        Log.d(TAG, "Disabled notification type: $typeId")
    }
    
    /**
     * Enable a specific app for a notification type
     */
    fun enableAppForType(typeId: String, packageName: String) {
        _enabledApps.update { current ->
            val currentSet = current[typeId] ?: emptySet()
            current + (typeId to (currentSet + packageName))
        }
        // Remove from disabled if present
        _disabledApps.update { current ->
            val currentSet = current[typeId] ?: emptySet()
            if (currentSet.isEmpty()) {
                current
            } else {
                current + (typeId to (currentSet - packageName))
            }
        }
        saveToConfig()
        Log.d(TAG, "Enabled app $packageName for type $typeId")
    }
    
    /**
     * Disable a specific app for a notification type
     */
    fun disableAppForType(typeId: String, packageName: String) {
        _disabledApps.update { current ->
            val currentSet = current[typeId] ?: emptySet()
            current + (typeId to (currentSet + packageName))
        }
        // Remove from enabled if present
        _enabledApps.update { current ->
            val currentSet = current[typeId] ?: emptySet()
            if (currentSet.isEmpty()) {
                current
            } else {
                current + (typeId to (currentSet - packageName))
            }
        }
        saveToConfig()
        Log.d(TAG, "Disabled app $packageName for type $typeId")
    }
    
    /**
     * Reset app configuration for a type (clear runtime overrides)
     */
    fun resetAppConfigForType(typeId: String) {
        _enabledApps.update { it - typeId }
        _disabledApps.update { it - typeId }
        saveToConfig()
        Log.d(TAG, "Reset app config for type $typeId")
    }
    
    /**
     * Load configuration from persistence and ConfigManager
     */
    fun loadFromConfig() {
        try {
            Log.i(TAG, "=== loadFromConfig() called ===")
            
            // Load from persistence (runtime changes)
            val persistedEnabledTypes = ConfigPersistence.loadEnabledTypes()
            Log.d(TAG, "Persisted enabled types: $persistedEnabledTypes (size=${persistedEnabledTypes.size})")
            
            val persistedEnabledApps = ConfigPersistence.loadEnabledApps()
            val persistedDisabledApps = ConfigPersistence.loadDisabledApps()
            
            // Load from ConfigManager (default config from JSON)
            val allTypes = ConfigManager.getAllNotificationTypes()
            Log.i(TAG, "Loaded ${allTypes.size} total types from ConfigManager")
            
            if (allTypes.isEmpty()) {
                Log.e(TAG, "❌ ConfigManager returned 0 types! Initialization issue!")
                _enabledTypes.value = emptySet()
                _enabledApps.value = emptyMap()
                _disabledApps.value = emptyMap()
                return
            }
            
            val configSettings = ConfigManager.getNotificationTypesConfig()?.settings
            Log.d(TAG, "Config settings: enableAllByDefault=${configSettings?.enableAllByDefault}")
            
            // Determine which types are enabled
            val enabledTypes = mutableSetOf<String>()
            
            // CRITICAL FIX: On first run (no persistence), use config file's enabled values
            // The key insight: emptySet() from loadEnabledTypes() means either:
            //   1. Key doesn't exist (first run) -> use config defaults
            //   2. Key exists but JSON is empty/invalid -> treat as first run
            //   3. Key exists with valid empty array -> user disabled everything (rare)
            // We distinguish by checking if the key exists at all
            
            val hasExplicitPersistence = if (::context.isInitialized) {
                try {
                    val prefsName = "yatramate_notification_config"
                    val prefs = context.getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
                    val keyExists = prefs.contains("enabled_types")
                    Log.d(TAG, "Persistence key 'enabled_types' exists: $keyExists")
                    keyExists
                } catch (e: Exception) {
                    Log.w(TAG, "Could not check persistence: ${e.message}")
                    false
                }
            } else {
                Log.w(TAG, "Context not initialized, treating as first run (no persistence)")
                false
            }
            
            Log.d(TAG, "hasExplicitPersistence=$hasExplicitPersistence, persistedEnabledTypes.size=${persistedEnabledTypes.size}")
            
            allTypes.forEach { type ->
                // Check if type should be enabled by default
                val shouldBeEnabled = when {
                    // Case 1: Key exists AND has non-empty values -> use persistence (user explicitly configured)
                    hasExplicitPersistence && persistedEnabledTypes.isNotEmpty() -> {
                        val inPersisted = persistedEnabledTypes.contains(type.id)
                        Log.d(TAG, "  ${type.id}: inPersisted=$inPersisted (explicit user config)")
                        inPersisted
                    }
                    // Case 2: Key exists but is empty -> User may have cleared all, but treat as first run for safety
                    // OR Case 3: Key doesn't exist -> First run, use config defaults
                    else -> {
                        val configEnabled = type.enabled
                        Log.d(TAG, "  ${type.id}: configEnabled=$configEnabled (using config file default)")
                        configEnabled
                    }
                }
                
                if (shouldBeEnabled) {
                    enabledTypes.add(type.id)
                    Log.d(TAG, "  ✅ ENABLED: ${type.id}")
                } else {
                    Log.d(TAG, "  ❌ DISABLED: ${type.id}")
                }
            }
            
            _enabledTypes.value = enabledTypes
            _enabledApps.value = persistedEnabledApps
            _disabledApps.value = persistedDisabledApps
            
            Log.i(TAG, "✅ Loaded configuration: ${enabledTypes.size} enabled types out of ${allTypes.size} total")
            Log.i(TAG, "✅ Enabled types: $enabledTypes")
            if (enabledTypes.isEmpty() && allTypes.isNotEmpty()) {
                Log.e(TAG, "⚠️ WARNING: All types are disabled! This will break classification!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading configuration: ${e.message}", e)
            e.printStackTrace()
            // Fallback: enable all types
            try {
                val allTypes = ConfigManager.getAllNotificationTypes()
                if (allTypes.isNotEmpty()) {
                    _enabledTypes.value = allTypes.map { it.id }.toSet()
                    Log.i(TAG, "✅ Fallback: Enabled all ${allTypes.size} types")
                } else {
                    Log.e(TAG, "❌ Fallback failed: ConfigManager returned 0 types")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Fallback also failed: ${e2.message}", e2)
            }
        }
    }
    
    /**
     * Save current state to persistence
     */
    fun saveToConfig() {
        try {
            ConfigPersistence.saveEnabledTypes(_enabledTypes.value)
            ConfigPersistence.saveEnabledApps(_enabledApps.value)
            ConfigPersistence.saveDisabledApps(_disabledApps.value)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving configuration: ${e.message}", e)
        }
    }
    
    /**
     * Get all enabled notification types from ConfigManager
     */
    fun getEnabledNotificationTypes(): List<NotificationType> {
        val allTypes = ConfigManager.getAllNotificationTypes()
        Log.d(TAG, "getEnabledNotificationTypes: ${allTypes.size} total types")
        
        val enabledTypes = allTypes.filter { isTypeEnabled(it.id) }
        Log.d(TAG, "getEnabledNotificationTypes: ${enabledTypes.size} enabled after filtering")
        Log.d(TAG, "  _enabledTypes.value = ${_enabledTypes.value}")
        Log.d(TAG, "  Enabled type IDs: ${enabledTypes.map { it.id }}")
        
        return enabledTypes
    }
    
    /**
     * Get all active notification types (enabled and with valid app configuration)
     */
    fun getActiveNotificationTypes(): List<NotificationType> {
        return getEnabledNotificationTypes()
    }
    
    /**
     * Reset to defaults (from config files)
     */
    fun resetToDefaults() {
        Log.i(TAG, "Resetting configuration to defaults")
        ConfigPersistence.resetToDefaults()
        
        // Force reload - clear current state first
        _enabledTypes.value = emptySet()
        _enabledApps.value = emptyMap()
        _disabledApps.value = emptyMap()
        
        loadFromConfig()
        Log.i(TAG, "Reset complete: ${_enabledTypes.value.size} types enabled")
    }
    
    /**
     * Force reload configuration (useful for debugging)
     */
    fun forceReload() {
        Log.i(TAG, "Force reloading configuration")
        loadFromConfig()
    }
}

