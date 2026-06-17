package com.lagradost.quicknovel.ui

import com.lagradost.quicknovel.ui.theme.LoadingIndicator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.TranslationEnginesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TranslationSheetCompose(
    originalText: String,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    var engineName by remember { mutableStateOf("Loading...") }
    var translationResult by remember { mutableStateOf<Resource<String>?>(null) }

    LaunchedEffect(originalText) {
        val engine = TranslationEnginesManager.getActiveEngine(context)
        engineName = engine?.name ?: "No engine"
        
        withContext(Dispatchers.IO) {
            val result = TranslationEnginesManager.translate(context, originalText)
            withContext(Dispatchers.Main) {
                translationResult = result
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Drag handle indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismiss() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .glassCard(shape = MaterialTheme.shapes.small)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.translation),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = engineName,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Original Text
        Text(
            text = "Original",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            Text(
                text = originalText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Translation Result
        Text(
            text = "Translated",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard()
                .padding(16.dp)
        ) {
            when (val res = translationResult) {
                null -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(32.dp))
                    }
                }
                is Resource.Success -> {
                    Text(
                        text = res.value,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
                is Resource.Failure -> {
                    Text(
                        text = res.errorString ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Actions
        if (translationResult is Resource.Success) {
            val translatedText = (translationResult as Resource.Success<String>).value
            Button(
                onClick = { onCopy(translatedText) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_content_copy_24),
                    contentDescription = "Copy",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Translation")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
