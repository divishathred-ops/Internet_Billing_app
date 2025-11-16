package com.example.billingapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.billingapp.R // Make sure to import your app's R class
import com.example.billingapp.viewmodel.AuthViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(
    navController: NavController,
    authViewModel: AuthViewModel,
    scope: CoroutineScope,
    drawerState: DrawerState,
    onItemSelected: (String) -> Unit
) {
    val userRole by authViewModel.userRole.collectAsState()
    var selectedItem by remember { mutableStateOf("agents") }

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        DrawerHeader(
            userRole = userRole,
            profilePicturePainter = painterResource(id = R.drawable.profile_picture_edit)
        )

        if (userRole == "admin") {
            DrawerItem(
                painter = painterResource(id = R.drawable.people_agents),
                label = "Agents",
                isSelected = selectedItem == "agents",
                onClick = {
                    selectedItem = "agents"
                    onItemSelected("agents")
                    scope.launch { drawerState.close() }
                }
            )
        }

        // This is the call for the Profile Settings item
        DrawerItem(
            icon = Icons.Default.Settings,
            label = "Profile Settings",
            isSelected = selectedItem == "profileSettings",
            onClick = {
                selectedItem = "profileSettings"
                onItemSelected("profileSettings")
                scope.launch { drawerState.close() }
            }
        )

        Spacer(Modifier.weight(1f))

        DrawerItem(
            painter = painterResource(id = R.drawable.logout),
            label = "Logout",
            isSelected = false,
            onClick = {
                scope.launch {
                    drawerState.close()
                    authViewModel.signOut()
                }
            },
            isLogout = true
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DrawerHeader(userRole: String?, profilePicturePainter: Painter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Image(
            painter = profilePicturePainter,
            contentDescription = "Profile Picture",
            modifier = Modifier
                .size(100.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Role: ${userRole?.replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Divider()
    }
}

// Function for items with PNG icons ("Agents" and "Logout")
@Composable
private fun DrawerItem(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    isLogout: Boolean = false
) {
    val appleRed = Color(0xFFFF3B30)
    // The specific blue color from your image's edit button
    val selectionBlue = Color(0xFF4285F4)

    val textColor = when {
        isLogout -> appleRed
        isSelected -> selectionBlue
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = Color.Unspecified
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// Function for items with vector icons ("Profile Settings")
@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // The specific blue color from your image's edit button
    val selectionBlue = Color(0xFF4285F4)

    // The icon will be tinted blue when selected
    val iconTintColor = if (isSelected) selectionBlue else MaterialTheme.colorScheme.onSurfaceVariant

    // **KEY LOGIC**: The text color is set to the same blue color when selected
    val textColor = if (isSelected) selectionBlue else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTintColor
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = selectionBlue, // The text color is applied here
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}