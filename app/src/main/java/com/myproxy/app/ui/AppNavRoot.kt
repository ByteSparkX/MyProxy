package com.myproxy.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomRoutes

    // 应用级 Scaffold 只负责顶层导航；各页面保留自己的内容结构。
    Scaffold(
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
            // 首页是主标签页，承载节点选择与连接入口。
            composable(AppRoute.Home) {
                HomeScreen(
                    onOpenImport = {
                        navController.navigate(AppRoute.Import)
                    },
                )
            }
            // 设置是主标签页，承载全局连接偏好。
            composable(AppRoute.Settings) {
                SettingsScreen()
            }
            // 导入页从首页进入，不出现在底部导航中。
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
    NavigationBar {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentDestination.isRouteSelected(item.route),
                onClick = { onNavigate(item.route) },
                icon = {
                    Text(text = item.symbol)
                },
                label = {
                    Text(text = item.label)
                },
            )
        }
    }
}

private fun NavDestination?.isRouteSelected(route: String): Boolean {
    return this?.hierarchy?.any { destination -> destination.route == route } == true
}

private object AppRoute {
    // route 保持稳定，后续页面增加参数时再集中扩展。
    const val Home = "home"
    const val Settings = "settings"
    const val Import = "home/import"
}

private data class BottomItem(
    val route: String,
    val label: String,
    val symbol: String,
)

private val bottomItems = listOf(
    BottomItem(route = AppRoute.Home, label = "主页", symbol = "⌂"),
    BottomItem(route = AppRoute.Settings, label = "设置", symbol = "⚙"),
)

private val bottomRoutes = bottomItems.map { item -> item.route }.toSet()
