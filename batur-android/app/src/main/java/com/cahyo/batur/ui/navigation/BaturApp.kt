package com.cahyo.batur.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cahyo.batur.AppContainer
import com.cahyo.batur.ui.agent.AgentScreen
import com.cahyo.batur.ui.history.HistoryDetailScreen
import com.cahyo.batur.ui.history.HistoryScreen
import com.cahyo.batur.ui.models.ModelDetailScreen
import com.cahyo.batur.ui.models.ModelsScreen
import com.cahyo.batur.ui.notes.NotesScreen
import com.cahyo.batur.ui.onboarding.OnboardingScreen
import com.cahyo.batur.ui.profile.ProfileScreen
import com.cahyo.batur.ui.skills.SkillDetailScreen
import com.cahyo.batur.ui.skills.SkillsScreen

@Composable
fun BaturApp(container: AppContainer) {
    // Dibaca sekali: start destination NavHost tidak boleh berubah setelah dibuat.
    val onboardingDone by produceState<Boolean?>(null) { value = container.settings.onboardingDone.first() }
    val done = onboardingDone ?: return

    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = BottomTab.fromRoute(route)

    Scaffold(
        bottomBar = {
            if (route != null && route != Screen.Onboarding.route) NavigationBar {
                BottomTab.entries.forEach { tab ->
                    val selected = tab == currentTab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navController.openTab(tab, alreadySelected = selected) },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        val bottom = PaddingValues(bottom = innerPadding.calculateBottomPadding())
        NavHost(
            navController = navController,
            startDestination = if (done) Screen.Agent.route else Screen.Onboarding.route,
            modifier = Modifier
                .padding(bottom)
                .consumeWindowInsets(bottom),
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onFinish = { downloadSlug ->
                        navController.navigate(Screen.Agent.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                        downloadSlug?.let { navController.navigate(Screen.ModelDetail.createRoute(it)) }
                    },
                )
            }
            composable(Screen.Agent.route) {
                AgentScreen(
                    container = container,
                    onOpenTask = { navController.navigate(Screen.HistoryDetail.createRoute(it)) },
                    onOpenNotes = { navController.navigate(Screen.Notes.route) },
                )
            }
            composable(Screen.Skills.route) {
                SkillsScreen(onSkillClick = { navController.navigate(Screen.SkillDetail.createRoute(it)) })
            }
            composable(
                Screen.SkillDetail.route,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) {
                SkillDetailScreen(
                    onBack = { navController.popBackStack() },
                    onTryExample = { example ->
                        container.agentDraft.value = example
                        navController.openTab(BottomTab.AGENT, alreadySelected = false)
                    },
                )
            }
            composable(Screen.History.route) {
                HistoryScreen(onTaskClick = { navController.navigate(Screen.HistoryDetail.createRoute(it)) })
            }
            composable(
                Screen.HistoryDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.IntType }),
            ) {
                HistoryDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Models.route) {
                ModelsScreen(onModelClick = { navController.navigate(Screen.ModelDetail.createRoute(it)) })
            }
            composable(
                Screen.ModelDetail.route,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) {
                ModelDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Profile.route) {
                ProfileScreen(onOpenNotes = { navController.navigate(Screen.Notes.route) })
            }
            composable(Screen.Notes.route) {
                NotesScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun NavHostController.openTab(tab: BottomTab, alreadySelected: Boolean) {
    // Menekan tab yang sedang aktif dari halaman detail kembali ke daftar.
    // Kalau daftarnya tidak ada di back stack (detail dibuka dari Agent), buka tab seperti biasa.
    if (alreadySelected && popBackStack(tab.screen.route, inclusive = false)) return
    // Tab Agent selalu kembali ke layar chat. Tanpa ini, halaman yang dibuka dari Agent
    // (Catatan, detail riwayat) ikut dipulihkan lewat restoreState dan tab Agent jadi
    // menampilkan Catatan, bahkan sempat kosong.
    if (tab == BottomTab.AGENT && popBackStack(Screen.Agent.route, inclusive = false, saveState = true)) return
    navigate(tab.screen.route) {
        // Agent selalu jadi dasar back stack; start destination graph bisa saja Onboarding yang sudah dibuang.
        popUpTo(Screen.Agent.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
