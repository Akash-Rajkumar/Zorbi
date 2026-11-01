package com.zorbi.ui.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zorbi.core.AppGraph
import com.zorbi.core.GameProfile
import com.zorbi.core.MappingType
import com.zorbi.core.TrackerId
import com.zorbi.ui.components.ZorbiEmptyState
import com.zorbi.ui.components.ZorbiErrorCard
import com.zorbi.ui.components.ZorbiFilterChip
import com.zorbi.ui.components.ZorbiIconButton
import com.zorbi.ui.components.ZorbiLoadingIndicator
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.theme.ZorbiBackground
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTheme

enum class LibraryFilter(val label: String) {
    ALL("All Games"),
    ONE_HAND("1-Hand"),
    WHEEL("2-Hand Wheel"),
    POSE_LEAN("Pose Lean"),
    SWIPE("Swipe"),
    SLASH("Slash")
}

/**
 * P3-1 Game Library Screen:
 * - Lists installed profiles using ProfileRepository from AppGraph
 * - Filter chips for motion trackers and mapping types
 * - Empty, Loading, and Error state handling
 * - Direct triggers to Calibrate and Play
 */
@Composable
fun GameLibraryScreen(
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    onNavigateToCalibrate: (String) -> Unit,
    onNavigateToPlay: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToEditor: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var profiles by remember { mutableStateOf<List<GameProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    suspend fun loadProfiles() {
        isLoading = true
        errorMessage = null
        try {
            profiles = appGraph.profiles.list()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load game profiles"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadProfiles()
    }

    // Filter logic
    val filteredProfiles = remember(profiles, selectedFilter) {
        when (selectedFilter) {
            LibraryFilter.ALL -> profiles
            LibraryFilter.ONE_HAND -> profiles.filter { it.tracker == TrackerId.HAND_SINGLE }
            LibraryFilter.WHEEL -> profiles.filter { it.tracker == TrackerId.HAND_DUAL_WHEEL }
            LibraryFilter.POSE_LEAN -> profiles.filter { it.tracker == TrackerId.POSE_LITE }
            LibraryFilter.SWIPE -> profiles.filter { it.mapping == MappingType.LANE_SWIPE }
            LibraryFilter.SLASH -> profiles.filter { it.mapping == MappingType.SLASH }
        }
    }

    ZorbiScaffold(
        title = "Game Library",
        subtitle = "${profiles.size} installed profiles",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        actions = {
            ZorbiIconButton(
                icon = Icons.Default.Share,
                contentDescription = "Community Profiles",
                onClick = onNavigateToCommunity
            )
        },
        modifier = modifier
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Horizontal Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        LibraryFilter.ALL -> profiles.size
                        LibraryFilter.ONE_HAND -> profiles.count { it.tracker == TrackerId.HAND_SINGLE }
                        LibraryFilter.WHEEL -> profiles.count { it.tracker == TrackerId.HAND_DUAL_WHEEL }
                        LibraryFilter.POSE_LEAN -> profiles.count { it.tracker == TrackerId.POSE_LITE }
                        LibraryFilter.SWIPE -> profiles.count { it.mapping == MappingType.LANE_SWIPE }
                        LibraryFilter.SLASH -> profiles.count { it.mapping == MappingType.SLASH }
                    }

                    ZorbiFilterChip(
                        label = filter.label,
                        selected = filter == selectedFilter,
                        count = count,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Body Content: Loading / Error / Empty / List
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                when {
                    isLoading -> {
                        ZorbiLoadingIndicator(
                            message = "Loading game profiles...",
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }

                    errorMessage != null -> {
                        ZorbiErrorCard(
                            title = "Error Loading Profiles",
                            message = errorMessage ?: "Unknown error",
                            fixButtonText = "Retry",
                            onFixClick = {
                                kotlinx.coroutines.MainScope().run {
                                    // reload
                                }
                            },
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }

                    filteredProfiles.isEmpty() -> {
                        ZorbiEmptyState(
                            title = if (profiles.isEmpty()) "No Game Profiles Found" else "No Matching Games",
                            description = if (profiles.isEmpty())
                                "No game profiles are currently installed. Import community profiles or create a custom profile."
                            else
                                "No installed profiles match the selected filter.",
                            icon = Icons.Default.Search,
                            actionButton = {
                                ZorbiSecondaryButton(
                                    text = "BROWSE COMMUNITY PROFILES",
                                    leadingIcon = Icons.Default.Share,
                                    onClick = onNavigateToCommunity
                                )
                            },
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredProfiles, key = { it.packageName }) { profile ->
                                GameCard(
                                    profile = profile,
                                    onPlayClick = { onNavigateToPlay(profile.packageName) },
                                    onCalibrateClick = { onNavigateToCalibrate(profile.packageName) },
                                    onEditClick = { onNavigateToEditor(profile.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
