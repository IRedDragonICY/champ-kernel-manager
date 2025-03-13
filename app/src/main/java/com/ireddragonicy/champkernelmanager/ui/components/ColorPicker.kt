
package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    
    val currentColor = Color(red, green, blue)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(currentColor)
                        .border(1.dp, Color.Black)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Red")
                Slider(
                    value = red,
                    onValueChange = { red = it },
                    valueRange = 0f..1f
                )
                
                Text("Green")
                Slider(
                    value = green,
                    onValueChange = { green = it },
                    valueRange = 0f..1f
                )
                
                Text("Blue")
                Slider(
                    value = blue,
                    onValueChange = { blue = it },
                    valueRange = 0f..1f
                )
                
                // Predefined colors
                Text("Predefined Colors", modifier = Modifier.padding(top = 16.dp))
                PredefinedColors { predefinedColor ->
                    red = predefinedColor.red
                    green = predefinedColor.green
                    blue = predefinedColor.blue
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onColorSelected(currentColor)
                    onDismiss()
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PredefinedColors(onColorSelected: (Color) -> Unit) {
    val colors = remember {
        listOf(
            Color.Red,
            Color.Green,
            Color.Blue,
            Color.Yellow,
            Color.Cyan,
            Color.Magenta,
            Color(0xFF9C27B0), // Purple
            Color(0xFFFF9800), // Orange
            Color(0xFF795548), // Brown
            Color(0xFF607D8B)  // Blue Grey
        )
    }
    
    Column {
        for (i in 0 until colors.size step 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (j in i until minOf(i + 5, colors.size)) {
                    ColorBox(color = colors[j], onColorClick = onColorSelected)
                }
            }
        }
    }
}

@Composable
fun ColorBox(color: Color, onColorClick: (Color) -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Gray, CircleShape)
            .clickable { onColorClick(color) }
    )
}