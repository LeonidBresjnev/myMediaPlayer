package com.equalizer.mymediaplayer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionDemo() {
    val notificationPermission = rememberPermissionState(
        permission = android.Manifest.permission.READ_MEDIA_AUDIO
    )
    val context = LocalContext.current

    val showRationalDialog = remember { mutableStateOf(false) }

    if (showRationalDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showRationalDialog.value = false
            },
            text = {
                Text(
                    stringResource(R.string.grant_permission_request),
                    fontSize = 16.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationalDialog.value = false
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        context.startActivity(intent)
                        //startActivity(context, intent, null)

                    }) {
                    Text("OK", style = TextStyle(color = Color.Black))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRationalDialog.value = false
                    }) {
                    Text("Cancel", style = TextStyle(color = Color.Black))
                }
            },
        )
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
                    title = { Text(
                        text = "Request Single Permission",
                        color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                scrolledContainerColor = Color.Unspecified,
                navigationIconContentColor = Color.Unspecified,
                titleContentColor = Color.Unspecified,
                actionIconContentColor = Color.Unspecified
            )
        )
    }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    if (!notificationPermission.status.isGranted) {
                        if (notificationPermission.status.shouldShowRationale) {
                            // Show a rationale if needed (optional)
                            showRationalDialog.value = true
                        } else {
                            // Request the permission
                            notificationPermission.launchPermissionRequest()

                        }
                    } else {
                        Toast.makeText(context, "Permission Given Already", Toast.LENGTH_SHORT)
                            .show()
                    }
                }) {
                    Text(text = "Ask for permission")
                }
                Text(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                    text = if (notificationPermission.status.isGranted) {
                        "Permission Granted"
                    } else if (notificationPermission.status.shouldShowRationale) {
                        // If the user has denied the permission but the rationale can be shown,
                        // then gently explain why the app requires this permission
                        "The notification is important for this app. Please grant the permission."
                    } else {
                        // If it's the first time the user lands on this feature, or the user
                        // doesn't want to be asked again for this permission, explain that the
                        // permission is required
                        "The notification permission is required for some functionality."
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}