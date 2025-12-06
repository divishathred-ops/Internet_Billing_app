package com.example.billingapp_2.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomBar(navController: NavController) {
    val items = listOf(
        Pair("Customers", "customerList"),
        Pair("Summary", "summary")
    )
    NavigationBar {
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        items.forEach { (label, route) ->
            NavigationBarItem(
                icon = {
                    when (route) {
                        "customerList" -> Icon(Icons.Filled.People, contentDescription = label)
                        "summary" -> Icon(Icons.Filled.Assessment, contentDescription = label)
                    }
                },
                label = { Text(label) },
                selected = currentRoute == route,
                onClick = {
                    if (currentRoute!= route) {
                        navController.navigate(route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            // on the back stack as users select items
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}