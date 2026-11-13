package com.cahyo.batur.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

/** Rute seluruh halaman. Halaman detail dipisahkan dari tab bottom navigation. */
sealed class Screen(val route: String) {
    data object Agent : Screen("agent")
    data object Skills : Screen("skills")
    data object History : Screen("history")
    data object Models : Screen("models")
    data object Profile : Screen("profile")
    data object Notes : Screen("notes")
    data object Onboarding : Screen("onboarding")

    data object SkillDetail : Screen("skill/{slug}") {
        fun createRoute(slug: String) = "skill/$slug"
    }

    data object HistoryDetail : Screen("history/{id}") {
        fun createRoute(id: Int) = "history/$id"
    }

    data object ModelDetail : Screen("model/{slug}") {
        fun createRoute(slug: String) = "model/$slug"
    }
}

enum class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    AGENT(Screen.Agent, "Agent", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SKILLS(Screen.Skills, "Skills", Icons.Outlined.Extension, Icons.Filled.Extension),
    HISTORY(Screen.History, "Riwayat", Icons.Outlined.History, Icons.Filled.History),
    MODELS(Screen.Models, "Model", Icons.Outlined.Memory, Icons.Filled.Memory),
    PROFILE(Screen.Profile, "Profil", Icons.Outlined.Person, Icons.Filled.Person);

    companion object {
        /** Tab yang menyala, termasuk saat sedang membuka halaman detail milik tab itu. */
        fun fromRoute(route: String?): BottomTab? = when {
            route == null -> null
            route == Screen.Agent.route -> AGENT
            route.startsWith("skill") -> SKILLS
            route.startsWith("history") -> HISTORY
            route.startsWith("model") -> MODELS
            route == Screen.Profile.route || route == Screen.Notes.route -> PROFILE
            else -> null
        }
    }
}
