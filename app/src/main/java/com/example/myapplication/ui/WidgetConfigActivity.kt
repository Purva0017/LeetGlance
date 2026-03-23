package com.example.myapplication.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.example.myapplication.data.LeetCodeRepository
import com.example.myapplication.data.RefreshWorker
import com.example.myapplication.widget.LeetCodeWidget
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// WidgetConfigActivity
// Launched automatically by Android when the user places the widget.
// The user enters their LeetCode username; we fetch data and finish.
// ─────────────────────────────────────────────────────────────────────────────

class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the user cancels, Android removes the widget
        setResult(RESULT_CANCELED)

        val appWidgetId = intent
            .extras
            ?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            ConfigScreen(
                onSave = { username ->
                    handleSave(username, appWidgetId)
                },
                onCancel = { finish() }
            )
        }
    }

    private fun handleSave(username: String, appWidgetId: Int) {
        val context = this
        kotlinx.coroutines.MainScope().launch {
            val repo = LeetCodeRepository(context)

            // 1. Save username
            repo.saveUsername(username.trim())

            // 2. Immediately fetch data so widget isn't blank on first render
            val result = repo.refreshData(username.trim())
            result.onFailure {
                Toast.makeText(context,
                    "Couldn't reach LeetCode — will retry later", Toast.LENGTH_LONG).show()
            }

            // 3. Trigger the widget to redraw
            LeetCodeWidget().updateAll(context)

            // 4. Schedule periodic background refresh
            RefreshWorker.schedule(context)

            // 5. Tell Android we succeeded
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compose UI for the config screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfigScreen(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val darkBg = Color(0xFF1A1A1A)
    val accent = Color(0xFFFFA116)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "LeetCode Widget",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enter your LeetCode username to\ndisplay your contribution graph.",
                color = Color(0xFF8A8A8A),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("LeetCode username", color = Color(0xFF8A8A8A)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (username.isNotBlank()) {
                            isLoading = true
                            onSave(username)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = accent,
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor     = Color.White,
                    unfocusedTextColor   = Color.White,
                    cursorColor          = accent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Save button
            Button(
                onClick = {
                    if (username.isNotBlank()) {
                        isLoading = true
                        onSave(username)
                    }
                },
                enabled = username.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save & Add Widget", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // Cancel
            TextButton(onClick = onCancel) {
                Text("Cancel", color = Color(0xFF8A8A8A))
            }
        }
    }
}