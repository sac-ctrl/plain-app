package com.ismartcoding.plain.ui.page.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ismartcoding.plain.preferences.SecurityAnswerPreference
import com.ismartcoding.plain.preferences.SecurityQuestionPreference
import com.ismartcoding.plain.ui.base.PScaffold
import com.ismartcoding.plain.ui.base.PTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQAPage(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        question = SecurityQuestionPreference.getAsync(context)
        answer = SecurityAnswerPreference.getAsync(context)
    }

    PScaffold(topBar = {
        PTopAppBar(navController = navController, title = "Security question")
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Used to unlock the dashboard from the disguised feedback survey.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text("Question", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = question, onValueChange = { question = it; saved = false },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Spacer(Modifier.height(14.dp))
            Text("Answer (case-insensitive)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = answer, onValueChange = { answer = it; saved = false },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        SecurityQuestionPreference.putAsync(context, question.trim())
                        SecurityAnswerPreference.putAsync(context, answer.trim())
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (saved) {
                Spacer(Modifier.height(8.dp))
                Text("Saved.", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}
