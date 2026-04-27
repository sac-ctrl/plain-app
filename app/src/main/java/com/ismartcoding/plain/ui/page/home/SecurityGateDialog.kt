package com.ismartcoding.plain.ui.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ismartcoding.plain.preferences.SecurityAnswerPreference
import com.ismartcoding.plain.preferences.SecurityQuestionPreference

@Composable
fun SecurityGateDialog(
    onDismiss: () -> Unit,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var question by remember { mutableStateOf("") }
    var expectedAnswer by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var answerInput by remember { mutableStateOf("") }
    var ratingInput by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        question = SecurityQuestionPreference.getAsync(context)
        expectedAnswer = SecurityAnswerPreference.getAsync(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Quick feedback", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Help us improve PlainApp. Takes 20 seconds.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))

                if (step == 0) {
                    Text("Your name (optional)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = nameInput, onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("e.g., Alex") },
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("How would you rate the app?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        for (i in 1..5) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (i <= ratingInput) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { ratingInput = i },
                                contentAlignment = Alignment.Center,
                            ) { Text("$i", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { step = 1 }) { Text("Next") }
                    }
                } else {
                    Text(question, color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = answerInput, onValueChange = { answerInput = it; error = "" },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Your answer") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        isError = error.isNotEmpty(),
                    )
                    if (error.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { step = 0 }) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            keyboard?.hide()
                            if (answerInput.trim().equals(expectedAnswer.trim(), ignoreCase = true)) {
                                onUnlock()
                            } else {
                                error = "Doesn't match. Try again."
                            }
                        }) { Text("Submit") }
                    }
                }
            }
        }
    }
}
