package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.R

@Composable
fun RootCheckScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text(text = "Root Access Required")
            },
            text = {
                Text(
                    text = "This app requires root access to function properly. " +
                           "Please grant root permissions and restart the app.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = { android.os.Process.killProcess(android.os.Process.myPid()) }) {
                    Text("Close App")
                }
            }
        )
    }
}