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
import com.cahyo.batur.ui.notes.NotesViewModel
import com.cahyo.batur.ui.onboarding.OnboardingViewModel
import com.cahyo.batur.ui.profile.ProfileViewModel
import com.cahyo.batur.ui.skills.SkillDetailViewModel
import com.cahyo.batur.ui.skills.SkillsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SkillsViewModel(container().skillRepository) }
        initializer { SkillDetailViewModel(createSavedStateHandle(), container().skillRepository) }
        initializer { HistoryViewModel(container().taskRepository) }
        initializer { HistoryDetailViewModel(createSavedStateHandle(), container().taskRepository) }
        initializer { ModelsViewModel(container()) }
        initializer { ModelDetailViewModel(createSavedStateHandle(), container(), application()) }
        initializer { ProfileViewModel(container()) }
        initializer { NotesViewModel(container().noteRepository) }
        initializer { OnboardingViewModel(container(), application()) }
        initializer {
            val c = container()
            AgentViewModel(c.agentOrchestrator, c.settings, c.onDeviceEngine, c.speech)
        }
    }
}

fun CreationExtras.application(): BaturApplication = this[APPLICATION_KEY] as BaturApplication

fun CreationExtras.container(): AppContainer = application().container
