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
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.cahyo.batur.ui.profile.ProfileScreen
import com.cahyo.batur.ui.skills.SkillDetailScreen
import com.cahyo.batur.ui.skills.SkillsScreen

@Composable
fun BaturApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentTab = BottomTab.fromRoute(backStack?.destination?.route)

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            startDestination = Screen.Agent.route,
            modifier = Modifier
                .padding(bottom)
                .consumeWindowInsets(bottom),
        ) {
            composable(Screen.Agent.route) {
                AgentScreen(
                    container = container,
                    onOpenTask = { navController.navigate(Screen.HistoryDetail.createRoute(it)) },
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
                ProfileScreen()
            }
        }
    }
}

private fun NavHostController.openTab(tab: BottomTab, alreadySelected: Boolean) {
    // Menekan tab yang sedang aktif dari halaman detail kembali ke daftar.
    // Kalau daftarnya tidak ada di back stack (detail dibuka dari Agent), buka tab seperti biasa.
    if (alreadySelected && popBackStack(tab.screen.route, inclusive = false)) return
    navigate(tab.screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
