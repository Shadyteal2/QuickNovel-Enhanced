package com.lagradost.quicknovel.ui

import com.lagradost.quicknovel.ui.theme.LoadingIndicator

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.DictionaryHelper
import com.lagradost.quicknovel.util.DictionaryResponse
import kotlinx.coroutines.launch

@Composable
fun DictionarySheetCompose(
    word: String,
    onDismiss: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var dictResult by remember { mutableStateOf<Resource<List<DictionaryResponse>>>(Resource.Loading()) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    LaunchedEffect(word) {
        dictResult = Resource.Loading()
        dictResult = DictionaryHelper.fetchDefinition(word)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
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

        when (val res = dictResult) {
            is Resource.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            is Resource.Failure -> {
                Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                    Text(
                        text = res.errorString ?: "Could not fetch definition",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 16.sp
                    )
                }
            }
            is Resource.Success -> {
                val results = res.value
                if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                        Text(
                            text = "No definition found",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    val firstResult = results[0]
                    val phonetic = firstResult.phonetic ?: firstResult.phonetics?.firstOrNull { !it.text.isNullOrBlank() }?.text ?: ""
                    val audioUrl = firstResult.phonetics?.firstOrNull { !it.audio.isNullOrBlank() }?.audio

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = firstResult.word,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (phonetic.isNotEmpty()) {
                                Text(
                                    text = phonetic,
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        
                        if (!audioUrl.isNullOrBlank()) {
                            IconButton(onClick = {
                                val fullUrl = if (audioUrl.startsWith("//")) "https:$audioUrl" else audioUrl
                                try {
                                    mediaPlayer?.release()
                                    mediaPlayer = MediaPlayer().apply {
                                        setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .build()
                                        )
                                        setDataSource(fullUrl)
                                        prepareAsync()
                                        setOnPreparedListener { start() }
                                        setOnErrorListener { _, _, _ ->
                                            showToast("Error playing audio")
                                            true
                                        }
                                    }
                                } catch (e: Exception) {
                                    showToast("Error initializing audio")
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_volume_up_24),
                                    contentDescription = "Play pronunciation",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val allMeanings = results.flatMap { it.meanings ?: emptyList() }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allMeanings) { meaning ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassCard()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = meaning.partOfSpeech ?: "",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                
                                meaning.definitions?.forEach { def ->
                                    Text(
                                        text = "• ${def.definition}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    if (!def.example.isNullOrBlank()) {
                                        Text(
                                            text = "\"${def.example}\"",
                                            color = Color.Gray,
                                            fontSize = 14.sp,
                                            fontStyle = FontStyle.Italic,
                                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
