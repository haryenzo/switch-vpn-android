package com.aeris.autovpn.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class RuleRepository(context: Context) {
    private val dao = AppDatabase.get(context).ruleDao()
    private val customTargetDao = AppDatabase.get(context).customVpnTargetDao()

    fun observeAll(): Flow<List<AutomationRule>> = dao.observeAll()

    suspend fun getEnabled(): List<AutomationRule> = dao.getEnabled()

    suspend fun save(rule: AutomationRule) {
        dao.upsert(rule)
    }

    suspend fun setEnabled(rule: AutomationRule, enabled: Boolean) {
        dao.update(rule.copy(enabled = enabled))
    }

    suspend fun delete(rule: AutomationRule) {
        dao.delete(rule)
    }

    fun observeCustomTargets(): Flow<List<CustomVpnTarget>> = customTargetDao.observeAll()

    suspend fun saveCustomTarget(target: CustomVpnTarget): Long = customTargetDao.insert(target)

    suspend fun deleteCustomTarget(target: CustomVpnTarget) {
        customTargetDao.delete(target)
    }

    // Built-ins plus whatever the user has added themselves — the single source of truth for
    // resolving a rule's targetAppId back into something DeepLinkController can act on.
    suspend fun allVpnTargets(): List<VpnAppTarget> =
        BUILTIN_VPN_TARGETS + customTargetDao.getAll().map { it.toVpnAppTarget() }
}
