package com.equalizer.mymediaplayer

import android.Manifest
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MultiPermissionRequest(setPermissionsOk: (Boolean) -> Unit) {
    val permissions = listOf(
        Manifest.permission.READ_MEDIA_AUDIO
    )

    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    var showRationaleDialog by remember { mutableStateOf(false) }

    if (!multiplePermissionsState.allPermissionsGranted) {

            Log.d("permissionrequest", "Ask for all permissions1")

            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                Log.d("permissionrequest", "Ask for all permissions2")



                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        text =
                            "This area typically contains the supportive text " +
                                    "which presents the details regarding the Dialog's purpose."
                    )
                    Button(
                        onClick = {
                            multiplePermissionsState.launchMultiplePermissionRequest()
                            Log.d("permissionrequest", "Ask for all permissions3")
                            Log.d("permissionrequest", multiplePermissionsState.permissions.joinToString(", ") { it.permission})
                            Log.d("permissionrequest", multiplePermissionsState.permissions.joinToString(", ") { it.status.isGranted.toString() })
                            showRationaleDialog = !multiplePermissionsState.allPermissionsGranted
                            setPermissionsOk(multiplePermissionsState.allPermissionsGranted)
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Confirm")
                    }
                }

            }
        }
    else setPermissionsOk(multiplePermissionsState.allPermissionsGranted)


}


