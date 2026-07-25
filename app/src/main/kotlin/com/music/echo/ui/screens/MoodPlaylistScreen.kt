package iad1tya.echo.music.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import iad1tya.echo.music.LocalPlayerAwareWindowInsets
import iad1tya.echo.music.R
import iad1tya.echo.music.constants.AiProviderKey
import iad1tya.echo.music.constants.OpenRouterApiKey
import iad1tya.echo.music.constants.OpenRouterBaseUrlKey
import iad1tya.echo.music.constants.OpenRouterModelKey
import iad1tya.echo.music.db.InternalDatabase
import iad1tya.echo.music.db.entities.PlaylistEntity
import iad1tya.echo.music.db.entities.PlaylistSongMap
import iad1tya.echo.music.db.entities.SongEntity
import iad1tya.echo.music.ui.component.IconButton
import iad1tya.echo.music.ui.utils.backToMain
import iad1tya.echo.music.utils.dataStore
import iad1tya.echo.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class MoodOption(
    val emoji: String,
    val label: String,
    val description: String,
    val searchKeywords: List<String>,
    val gradientColors: List<Color>,
)

val MoodOptions = listOf(
    MoodOption(
        emoji = "😊", label = "Happy",
        description = "Upbeat & joyful tracks",
        searchKeywords = listOf("happy upbeat pop songs", "feel good music playlist", "cheerful pop hits"),
        gradientColors = listOf(Color(0xFFFFD93D), Color(0xFFFF6B6B)),
    ),
    MoodOption(
        emoji = "😢", label = "Sad",
        description = "Melancholic & emotional",
        searchKeywords = listOf("sad emotional songs", "heartbreak ballads", "melancholic indie music"),
        gradientColors = listOf(Color(0xFF6A85B6), Color(0xFFBAC8E0)),
    ),
    MoodOption(
        emoji = "😌", label = "Chill",
        description = "Relaxed & laid-back vibes",
        searchKeywords = listOf("chill lofi music", "relaxing acoustic songs", "mellow vibes playlist"),
        gradientColors = listOf(Color(0xFF43C6AC), Color(0xFF191654)),
    ),
    MoodOption(
        emoji = "⚡", label = "Energetic",
        description = "High-energy & pumped up",
        searchKeywords = listOf("high energy workout music", "energetic EDM songs", "pump up playlist"),
        gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
    ),
    MoodOption(
        emoji = "🎯", label = "Focus",
        description = "Deep work & concentration",
        searchKeywords = listOf("focus study music", "concentration instrumental", "deep work ambient"),
        gradientColors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
    ),
    MoodOption(
        emoji = "❤️", label = "Romantic",
        description = "Love & intimate songs",
        searchKeywords = listOf("romantic love songs", "date night music", "intimate ballads"),
        gradientColors = listOf(Color(0xFFDA4453), Color(0xFF89216B)),
    ),
    MoodOption(
        emoji = "🎉", label = "Party",
        description = "Dance & celebrate",
        searchKeywords = listOf("party dance music", "club hits playlist", "dance anthems"),
        gradientColors = listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
    ),
    MoodOption(
        emoji = "🌙", label = "Sleep",
        description = "Calm & soothing sounds",
        searchKeywords = listOf("sleep music calm", "soothing night sounds", "gentle lullaby playlist"),
        gradientColors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedMood by remember { mutableStateOf<MoodOption?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedPlaylistId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Mood Playlist",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { onBack?.invoke() ?: navController.navigateUp() },
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal)
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Subtitle
            item {
                Text(
                    text = "How are you feeling? Pick a mood and Moodsonix will build the perfect playlist for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // Mood grid
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 2000.dp), // unbounded inside LazyColumn
                    userScrollEnabled = false,
                ) {
                    items(MoodOptions) { mood ->
                        MoodCard(
                            mood = mood,
                            isSelected = selectedMood?.label == mood.label,
                            onClick = {
                                selectedMood = if (selectedMood?.label == mood.label) null else mood
                                generatedPlaylistId = null
                                statusMessage = ""
                            }
                        )
                    }
                }
            }

            // Generate button + status
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            val mood = selectedMood ?: return@Button
                            coroutineScope.launch {
                                isGenerating = true
                                statusMessage = "Building your ${mood.label} playlist…"
                                try {
                                    val id = generateMoodPlaylist(context, mood) { log ->
                                        statusMessage = log
                                    }
                                    if (id != null) {
                                        generatedPlaylistId = id
                                        statusMessage = "Playlist ready!"
                                        navController.navigate("local_playlist/$id")
                                    } else {
                                        statusMessage = "Couldn't create playlist. Check your AI API key in Settings → AI."
                                    }
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isGenerating = false
                                }
                            }
                        },
                        enabled = selectedMood != null && !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = if (isGenerating) "Generating…"
                            else if (selectedMood != null) "Build ${selectedMood!!.label} Playlist"
                            else "Pick a mood above",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Text(
                                text = statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    // AI key hint
                    if (selectedMood != null && !isGenerating && statusMessage.isEmpty()) {
                        Text(
                            text = "Requires an AI API key in Settings → AI Settings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodCard(
    mood: MoodOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 8f else 0f,
        label = "elevation",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = elevation }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isSelected)
                    Brush.linearGradient(mood.gradientColors)
                else
                    Brush.linearGradient(
                        mood.gradientColors.map { it.copy(alpha = 0.15f) }
                    )
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Text(text = mood.emoji, fontSize = 32.sp)
            Text(
                text = mood.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = mood.description,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val moodHttpClient = OkHttpClient()

suspend fun generateMoodPlaylist(
    context: Context,
    mood: MoodOption,
    onLog: suspend (String) -> Unit,
): String? = withContext(Dispatchers.IO) {
    val database = InternalDatabase.newInstance(context)

    onLog("Asking AI for ${mood.label} track recommendations…")

    val apiKey = context.dataStore.get(OpenRouterApiKey, "")
    val baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, "https://openrouter.ai/api/v1/chat/completions")
    val model = context.dataStore.get(OpenRouterModelKey, "google/gemini-2.5-flash-lite")

    if (apiKey.isEmpty()) {
        onLog("No API key found. Please add your OpenRouter API key in Settings → AI Settings.")
        return@withContext null
    }

    val systemPrompt = """
        You are a music expert. Recommend 15 songs that perfectly match the mood: "${mood.label}".
        Mood description: "${mood.description}".
        You MUST output ONLY a valid JSON object — no markdown, no explanation, no code block:
        {"name":"Creative Playlist Name","songs":[{"title":"Song Title","artist":"Artist Name"}]}
    """.trimIndent()

    val userMessage = "Create a playlist for mood: ${mood.label}. Keywords: ${mood.searchKeywords.joinToString(", ")}"

    val requestBody = JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
        })
    }.toString().toRequestBody("application/json".toMediaType())

    try {
        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val response = moodHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            onLog("AI request failed: ${response.code}")
            return@withContext null
        }

        val responseString = response.body?.string() ?: return@withContext null
        val responseJson = JSONObject(responseString)
        val choices = responseJson.optJSONArray("choices") ?: return@withContext null
        val content = choices.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: "{}"
        val cleanJson = content.replace("```json", "").replace("```", "").trim()

        val parsed = try { JSONObject(cleanJson) } catch (e: Exception) {
            onLog("Failed to parse AI response.")
            return@withContext null
        }

        val playlistName = parsed.optString("name", "${mood.emoji} ${mood.label} Vibes")
        val songs = parsed.optJSONArray("songs") ?: return@withContext null

        onLog("Got ${songs.length()} recommendations. Searching YouTube Music…")

        val resolvedSongs = mutableListOf<SongItem>()
        val total = songs.length()

        for (i in 0 until total) {
            val item = songs.optJSONObject(i) ?: continue
            val title = item.optString("title")
            val artist = item.optString("artist")
            if (title.isEmpty()) continue
            onLog("Searching [${i + 1}/$total]: $title – $artist")
            val result = YouTube.search("$title $artist", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val topSong = result?.items?.firstOrNull() as? SongItem
            if (topSong != null) resolvedSongs.add(topSong)
        }

        if (resolvedSongs.isEmpty()) {
            onLog("No songs found. Try a different mood or check your connection.")
            return@withContext null
        }

        onLog("Creating playlist with ${resolvedSongs.size} songs…")

        val playlistEntity = PlaylistEntity(
            name = playlistName,
            bookmarkedAt = java.time.LocalDateTime.now(),
            isLocal = true,
            isEditable = true,
        )
        database.insert(playlistEntity)
        val playlistId = playlistEntity.id

        resolvedSongs.forEachIndexed { index, songItem ->
            val songEntity = SongEntity(
                id = songItem.id,
                title = songItem.title,
                duration = songItem.duration ?: 0,
                thumbnailUrl = songItem.thumbnail,
            )
            database.insert(songEntity)
            database.insert(PlaylistSongMap(playlistId = playlistId, songId = songItem.id, position = index))
        }

        onLog("Done!")
        return@withContext playlistId
    } catch (e: Exception) {
        onLog("Error: ${e.message}")
        return@withContext null
    }
}
