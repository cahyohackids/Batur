package com.cahyo.batur.ui.skills

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cahyo.batur.data.remote.SkillDetail
import com.cahyo.batur.data.repository.SkillRepository
import com.cahyo.batur.ui.common.UiState
import com.cahyo.batur.ui.common.toState
import com.cahyo.batur.data.remote.asObjectOrNull
import com.google.gson.JsonElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ParamInfo(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean,
)

class SkillDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: SkillRepository,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _state = MutableStateFlow<UiState<SkillDetail>>(UiState.Loading)
    val state: StateFlow<UiState<SkillDetail>> = _state.asStateFlow()

    private val _toggling = MutableStateFlow(false)
    val toggling: StateFlow<Boolean> = _toggling.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = repository.getSkill(slug).toState()
        }
    }

    fun setActive(active: Boolean) {
        val current = (_state.value as? UiState.Success)?.data ?: return
        if (_toggling.value) return
        viewModelScope.launch {
            _toggling.value = true
            repository.setActive(slug, active)
                .onSuccess { result ->
                    _state.value = UiState.Success(current.copy(isActive = result.isActive))
                    _message.value = if (result.isActive) "Skill diaktifkan" else "Skill dinonaktifkan. Agent tidak akan memakainya."
                }
                .onFailure { _message.value = it.message }
            _toggling.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}

fun parseParameters(parameters: JsonElement?): List<ParamInfo> {
    val schema = parameters.asObjectOrNull() ?: return emptyList()
    val properties = schema.get("properties").asObjectOrNull() ?: return emptyList()
    val required = schema.get("required")?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }?.toSet().orEmpty()
    return properties.entrySet().map { (name, element) ->
        val prop = element.asObjectOrNull()
        ParamInfo(
            name = name,
            type = prop?.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "-",
            description = prop?.get("description")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            required = name in required,
        )
    }
}
