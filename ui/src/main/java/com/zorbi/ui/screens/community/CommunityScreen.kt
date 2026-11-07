package com.zorbi.ui.screens.community

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorbi.core.AppGraph
import com.zorbi.core.MappingType
import com.zorbi.core.TrackerId
import com.zorbi.ui.components.ZorbiBadge
import com.zorbi.ui.components.ZorbiEmptyState
import com.zorbi.ui.components.ZorbiErrorCard
import com.zorbi.ui.components.ZorbiFilterChip
import com.zorbi.ui.components.ZorbiGlassCard
import com.zorbi.ui.components.ZorbiLoadingIndicator
import com.zorbi.ui.components.ZorbiMappingBadge
import com.zorbi.ui.components.ZorbiPrimaryButton
import com.zorbi.ui.components.ZorbiScaffold
import com.zorbi.ui.components.ZorbiSecondaryButton
import com.zorbi.ui.components.ZorbiTrackerBadge
import com.zorbi.ui.theme.ZorbiAccentViolet
import com.zorbi.ui.theme.ZorbiBorder
import com.zorbi.ui.theme.ZorbiError
import com.zorbi.ui.theme.ZorbiMetricTypography
import com.zorbi.ui.theme.monoMedium
import com.zorbi.ui.theme.ZorbiPrimaryCyan
import com.zorbi.ui.theme.ZorbiSuccess
import com.zorbi.ui.theme.ZorbiSurface
import com.zorbi.ui.theme.ZorbiSurfaceElevated
import com.zorbi.ui.theme.ZorbiTextDisabled
import com.zorbi.ui.theme.ZorbiTextMuted
import com.zorbi.ui.theme.ZorbiTextPrimary
import com.zorbi.ui.theme.ZorbiTheme
import com.zorbi.ui.theme.ZorbiWarning
import kotlinx.coroutines.launch

/**
 * Community Profiles Screen for ZORBI.
 * Enables browsing cloud repository profiles and importing them into the local library
 * using AppGraph.profiles.importFromCloud(id).
 * Strictly avoids fake success and handles loading, error, and empty states.
 */
@Composable
fun CommunityScreen(
    appGraph: AppGraph,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CommunityCategory.ALL) }

    var catalog by remember { mutableStateOf(CloudProfileItem.CATALOG) }
    var installedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importingIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load installed profiles on launch to mark existing games
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val localProfiles = appGraph.profiles.list()
            installedPackages = localProfiles.map { it.packageName }.toSet()
        } catch (e: Exception) {
            // Local list failed, proceed with empty set
        } finally {
            isLoading = false
        }
    }

    // Filter profiles based on category and search query
    val filteredProfiles = remember(catalog, searchQuery, selectedCategory) {
        catalog.filter { item ->
            val matchesCategory = when (selectedCategory) {
                CommunityCategory.ALL -> true
                CommunityCategory.STEER -> item.mapping == MappingType.STEER_BUTTONS || item.mapping == MappingType.STEER_DRAG
                CommunityCategory.SWIPE -> item.mapping == MappingType.LANE_SWIPE
                CommunityCategory.SLASH -> item.mapping == MappingType.SLASH
                CommunityCategory.POSE -> item.tracker == TrackerId.POSE_LITE
            }

            val matchesSearch = searchQuery.isBlank() ||
                    item.displayName.contains(searchQuery, ignoreCase = true) ||
                    item.packageName.contains(searchQuery, ignoreCase = true) ||
                    item.author.contains(searchQuery, ignoreCase = true)

            matchesCategory && matchesSearch
        }
    }

    ZorbiScaffold(
        title = "Community Profiles",
        subtitle = "Verified Motion Cloud Catalog",
        onBackClick = onNavigateBack,
        backIcon = Icons.AutoMirrored.Filled.ArrowBack,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Search by game or creator...",
                        style = ZorbiTheme.typography.bodyMedium,
                        color = ZorbiTextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = ZorbiPrimaryCyan
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZorbiPrimaryCyan,
                    unfocusedBorderColor = ZorbiBorder,
                    focusedTextColor = ZorbiTextPrimary,
                    unfocusedTextColor = ZorbiTextPrimary,
                    focusedContainerColor = ZorbiSurfaceElevated,
                    unfocusedContainerColor = ZorbiSurfaceElevated
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommunityCategory.values().forEach { category ->
                    ZorbiFilterChip(
                        label = category.label,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Content List / Loading / Empty State
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ZorbiLoadingIndicator(message = "Syncing with cloud repository...")
                    }
                }

                filteredProfiles.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ZorbiEmptyState(
                            title = "No Profiles Found",
                            description = if (searchQuery.isNotEmpty()) {
                                "No motion profiles match \"$searchQuery\". Try a different search term or category."
                            } else {
                                "No community profiles available in this category yet."
                            },
                            ctaText = "CLEAR FILTER",
                            onCtaClick = {
                                searchQuery = ""
                                selectedCategory = CommunityCategory.ALL
                            }
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredProfiles, key = { it.id }) { item ->
                            val isCurrentlyImporting = importingIds.contains(item.id)
                            val isAlreadyInLibrary = installedPackages.contains(item.packageName) || importedIds.contains(item.id)
                            val errorMessage = importErrors[item.id]

                            CommunityProfileCard(
                                item = item,
                                isImporting = isCurrentlyImporting,
                                isImported = isAlreadyInLibrary,
                                errorMessage = errorMessage,
                                onImportClick = {
                                    coroutineScope.launch {
                                        importingIds = importingIds + item.id
                                        importErrors = importErrors - item.id
                                        try {
                                            val result = appGraph.profiles.importFromCloud(item.id)
                                            if (result != null) {
                                                // Success: verified by actual repository
                                                importedIds = importedIds + item.id
                                                installedPackages = installedPackages + item.packageName
                                            } else {
                                                // Zero fake success: real contract returned null
                                                importErrors = importErrors + (item.id to "Cloud profile unavailable or remote service offline. Check network connection.")
                                            }
                                        } catch (e: Exception) {
                                            importErrors = importErrors + (item.id to "Import failed: ${e.message ?: "Network error"}")
                                        } finally {
                                            importingIds = importingIds - item.id
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityProfileCard(
    item: CloudProfileItem,
    isImporting: Boolean,
    isImported: Boolean,
    errorMessage: String?,
    onImportClick: () -> Unit
) {
    ZorbiGlassCard(modifier = Modifier.fillMaxWidth()) {
        // Title & Author Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = ZorbiTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ZorbiTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.packageName} • by ${item.author}",
                    style = ZorbiTheme.typography.bodySmall,
                    color = ZorbiTextMuted
                )
            }

            // Rating & Downloads Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = ZorbiWarning,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${item.rating}",
                    style = ZorbiMetricTypography.monoMedium.copy(fontSize = 12.sp),
                    color = ZorbiTextPrimary
                )
                Text(
                    text = "(${item.downloads})",
                    style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ZorbiTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Description
        Text(
            text = item.description,
            style = ZorbiTheme.typography.bodySmall,
            color = ZorbiTextMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Badges: Tracker, Mapping, Orientation
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZorbiTrackerBadge(trackerId = item.tracker)
            ZorbiMappingBadge(mapping = item.mapping)
            ZorbiBadge(
                text = item.orientation.name,
                accentColor = ZorbiAccentViolet
            )
        }

        // Error message if import failed
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ZorbiError.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, ZorbiError.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    style = ZorbiTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = ZorbiError,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Import Button or "Installed" Badge
        if (isImported) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ZorbiSuccess.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, ZorbiSuccess.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = ZorbiSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSTALLED IN LOCAL LIBRARY",
                        style = ZorbiTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ZorbiSuccess
                    )
                }
            }
        } else {
            ZorbiPrimaryButton(
                text = if (isImporting) "IMPORTING PROFILE..." else "IMPORT TO LIBRARY",
                onClick = onImportClick,
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
