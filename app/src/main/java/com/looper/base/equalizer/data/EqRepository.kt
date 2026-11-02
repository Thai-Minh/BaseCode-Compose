package com.looper.base.equalizer.data

import com.looper.base.base.storage.eqSettings
import com.looper.base.base.storage.tenBandEqSettings
import com.looper.base.base.storage.updateEqSettings
import com.looper.base.base.storage.updateTenBandEqSettings
import com.looper.base.database.dao.EqPresetDao
import com.looper.base.database.data.EqPresetEntity
import com.looper.base.equalizer.audio.AudioEffectController
import com.looper.base.equalizer.audio.TenBandEqualizerProcessor
import com.looper.base.equalizer.model.BuiltInEqPresets
import com.looper.base.equalizer.model.EqEngine
import com.looper.base.equalizer.model.EqPreset
import com.looper.base.equalizer.model.EqSettings
import com.looper.base.equalizer.model.TenBandEqSettings
import com.looper.base.equalizer.model.forAudioFx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
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
    private val tenBandProcessor: TenBandEqualizerProcessor,
) {

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 150L
    }

    private val scope = CoroutineScope(context = SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingPersist = MutableStateFlow<EqSettings?>(value = null)
    private val pendingTenBandPersist = MutableStateFlow<TenBandEqSettings?>(value = null)
    private var currentSettings = EqSettings()
    private var currentTenBand = TenBandEqSettings()

    val settings: Flow<EqSettings> = eqSettings

    val tenBandSettings: Flow<TenBandEqSettings> = tenBandEqSettings

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
        scope.launch {
            pendingTenBandPersist
                .filterNotNull()
                .debounce(timeoutMillis = PERSIST_DEBOUNCE_MS)
                .collect {
                    updateTenBandEqSettings(value = it)
                }
        }
        scope.launch {
            combine(flow = eqSettings, flow2 = tenBandEqSettings) { eq, tenBand ->
                eq to tenBand
            }.collect { (eq, tenBand) ->
                currentSettings = eq
                currentTenBand = tenBand
                syncTenBandProcessor()
            }
        }
    }

    fun apply(settings: EqSettings) {
        val normalized = settings.normalized()
        currentSettings = normalized
        effectController.apply(settings = normalized.forAudioFx(tenBand = currentTenBand))
        syncTenBandProcessor()
        pendingPersist.value = normalized
    }

    fun applyTenBand(settings: TenBandEqSettings) {
        val normalized = settings.normalized()
        currentTenBand = normalized
        effectController.apply(settings = currentSettings.forAudioFx(tenBand = normalized))
        syncTenBandProcessor()
        pendingTenBandPersist.value = normalized
    }

    private fun syncTenBandProcessor() {
        tenBandProcessor.update(
            enabled = currentSettings.enabled && currentTenBand.engine == EqEngine.BUILT_IN,
            settings = currentTenBand,
        )
    }

    suspend fun savePreset(name: String, settings: EqSettings): EqPresetResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return EqPresetResult.EmptyName
        }
        if (isNameTaken(name = trimmed)) {
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
        presetDao.upsert(preset = entity)
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
