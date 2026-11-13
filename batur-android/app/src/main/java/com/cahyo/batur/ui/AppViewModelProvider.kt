package com.cahyo.batur.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cahyo.batur.AppContainer
import com.cahyo.batur.BaturApplication
import com.cahyo.batur.ui.agent.AgentViewModel
import com.cahyo.batur.ui.history.HistoryDetailViewModel
import com.cahyo.batur.ui.history.HistoryViewModel
import com.cahyo.batur.ui.models.ModelDetailViewModel
import com.cahyo.batur.ui.models.ModelsViewModel
import com.cahyo.batur.ui.profile.ProfileViewModel
import com.cahyo.batur.ui.skills.SkillDetailViewModel
import com.cahyo.batur.ui.skills.SkillsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SkillsViewModel(container().skillRepository) }
        initializer { SkillDetailViewModel(createSavedStateHandle(), container().skillRepository) }
        initializer { HistoryViewModel(container().taskRepository) }
        initializer { HistoryDetailViewModel(createSavedStateHandle(), container().taskRepository) }
        initializer { ModelsViewModel(container().modelRepository, container().modelStore, container().settings) }
        initializer {
            val c = container()
            ModelDetailViewModel(createSavedStateHandle(), c.modelRepository, c.modelStore, c.settings, c.onDeviceEngine)
        }
        initializer { ProfileViewModel(container()) }
        initializer { AgentViewModel(container().agentOrchestrator, container().settings) }
    }
}

fun CreationExtras.container(): AppContainer =
    (this[APPLICATION_KEY] as BaturApplication).container
