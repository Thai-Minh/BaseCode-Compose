package com.looper.base.equalizer.data

import com.looper.base.base.storage.eqSettings
import com.looper.base.base.storage.updateEqSettings
import com.looper.base.database.dao.EqPresetDao
import com.looper.base.database.data.EqPresetEntity
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.model.BuiltInEqPresets
import com.looper.base.equalizer.model.EqPreset
import com.looper.base.equalizer.model.EqSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface EqPresetResult {
    data class Success(val preset: EqPreset) : EqPresetResult
    data object EmptyName : EqPresetResult
    data object DuplicateName : EqPresetResult
}

@OptIn(FlowPreview::class)
class EqRepository(
    private val presetDao: EqPresetDao,
    private val effectController: AudioEffectController,
) {

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 150L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingPersist = MutableStateFlow<EqSettings?>(null)

    val settings: Flow<EqSettings> = eqSettings

    val presets: Flow<List<EqPreset>> = presetDao.observeAll().map { entities ->
        BuiltInEqPresets.all + entities.map {
            it.toDomain()
        }
    }

    init {
        scope.launch {
            pendingPersist
                .filterNotNull()
                .debounce(timeoutMillis = PERSIST_DEBOUNCE_MS)
                .collect {
                    updateEqSettings(value = it)
                }
        }
    }

    fun apply(settings: EqSettings) {
        val normalized = settings.normalized()
        effectController.apply(settings = normalized)
        pendingPersist.value = normalized
    }

    suspend fun savePreset(name: String, settings: EqSettings): EqPresetResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return EqPresetResult.EmptyName
        }
        if (isNameTaken(trimmed)) {
            return EqPresetResult.DuplicateName
        }

        val normalized = settings.normalized()
        val entity = EqPresetEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            bandLevelsMb = normalized.bandLevelsMb.joinToString(separator = ","),
            bassBoost = normalized.bassBoost,
            virtualizer = normalized.virtualizer,
        )
        presetDao.upsert(entity)
        return EqPresetResult.Success(preset = entity.toDomain())
    }

    suspend fun renamePreset(id: String, name: String): EqPresetResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return EqPresetResult.EmptyName
        }
        if (isNameTaken(name = trimmed, excludeId = id)) {
            return EqPresetResult.DuplicateName
        }

        presetDao.rename(id = id, name = trimmed)
        val updated = presetDao.get(id) ?: return EqPresetResult.EmptyName
        return EqPresetResult.Success(preset = updated.toDomain())
    }

    suspend fun deletePreset(id: String) {
        presetDao.delete(id = id)
    }

    private suspend fun isNameTaken(name: String, excludeId: String = ""): Boolean {
        val clashesBuiltIn = BuiltInEqPresets.all.any {
            it.name.equals(other = name, ignoreCase = true)
        }
        return clashesBuiltIn || presetDao.countByName(name = name, excludeId = excludeId) > 0
    }

    private fun EqPresetEntity.toDomain() = EqPreset(
        id = id,
        name = name,
        bandLevelsMb = bandLevelsMb.split(',').mapNotNull {
            it.trim().toIntOrNull()
        },
        bassBoost = bassBoost,
        virtualizer = virtualizer,
        isBuiltIn = false,
    )
}
