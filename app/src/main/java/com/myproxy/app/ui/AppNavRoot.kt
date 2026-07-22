package com.myproxy.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppNavRoot() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomRoutes

    // 应用级 Scaffold 只负责顶层导航；各页面保留自己的内容结构。
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                AppNavigationBar(
                    currentDestination = currentDestination,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.Home,
            modifier = Modifier.padding(innerPadding),
        ) {
            // 首页只承载连接状态、路由模式、流量和连接入口。
            composable(AppRoute.Home) {
                HomeScreen(mainViewModel = mainViewModel)
            }
            // 配置页集中管理节点，避免节点列表挤占首页空间。
            composable(AppRoute.Config) {
                ConfigScreen(
                    mainViewModel = mainViewModel,
                    onOpenImport = {
                        navController.navigate(AppRoute.Import)
                    },
                )
            }
            // 设置是主标签页，承载全局连接偏好。
            composable(AppRoute.Settings) {
                SettingsScreen()
            }
            // 导入页从配置页进入，不出现在底部导航中。
            composable(AppRoute.Import) {
                ImportScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}

@Composable
private fun AppNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (String) -> Unit,
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            bottomItems.forEach { item ->
                NavigationBarItem(
                    selected = currentDestination.isRouteSelected(item.route),
                    onClick = { onNavigate(item.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                        )
                    },
                    label = {
                        androidx.compose.material3.Text(text = item.label)
                    },
                )
            }
        }
    }
}

private fun NavDestination?.isRouteSelected(route: String): Boolean {
    return this?.hierarchy?.any { destination -> destination.route == route } == true
}

private object AppRoute {
    // route 保持稳定，后续页面增加参数时再集中扩展。
    const val Home = "home"
    const val Config = "config"
    const val Settings = "settings"
    const val Import = "config/import"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomItems = listOf(
    BottomItem(route = AppRoute.Home, label = "首页", icon = Icons.Filled.Home),
    BottomItem(route = AppRoute.Config, label = "配置", icon = Icons.AutoMirrored.Filled.List),
    BottomItem(route = AppRoute.Settings, label = "设置", icon = Icons.Filled.Settings),
)

private val bottomRoutes = bottomItems.map { item -> item.route }.toSet()
