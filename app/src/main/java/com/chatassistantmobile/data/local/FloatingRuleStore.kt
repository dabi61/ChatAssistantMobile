package com.chatassistantmobile.data.local

import android.content.Context
import android.content.SharedPreferences

class FloatingRuleStore(context: Context) {
    companion object {
        private const val PREFS_FILE = "floating_rule_prefs"
        private const val KEY_RELATIONSHIP_ROLE = "relationship_role"
        private const val KEY_CUSTOM_RULE = "custom_rule"

        val ROLE_OPTIONS = listOf("crush", "friend", "family", "customer")
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun getRelationshipRole(): String {
        val stored = prefs.getString(KEY_RELATIONSHIP_ROLE, "friend").orEmpty()
        return stored.takeIf { it in ROLE_OPTIONS } ?: "friend"
    }

    fun setRelationshipRole(role: String) {
        val safeRole = role.takeIf { it in ROLE_OPTIONS } ?: "friend"
        prefs.edit().putString(KEY_RELATIONSHIP_ROLE, safeRole).apply()
    }

    fun cycleRelationshipRole(): String {
        val current = getRelationshipRole()
        val index = ROLE_OPTIONS.indexOf(current)
        val next = if (index == -1) ROLE_OPTIONS.first() else ROLE_OPTIONS[(index + 1) % ROLE_OPTIONS.size]
        setRelationshipRole(next)
        return next
    }

    fun getCustomRule(): String = prefs.getString(KEY_CUSTOM_RULE, "").orEmpty()

    fun setCustomRule(rule: String) {
        prefs.edit().putString(KEY_CUSTOM_RULE, rule.trim().take(300)).apply()
    }
}
