package cz.st72504.unitrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.st72504.unitrack.R
import cz.st72504.unitrack.model.ActivityRecord
import cz.st72504.unitrack.model.UserAchievements
import cz.st72504.unitrack.model.UserStatistics
import cz.st72504.unitrack.ui.components.StatCard
import cz.st72504.unitrack.ui.theme.ProgressTeal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun MyResultsView(
    userName: String,
    userTeam: String,
    userAvatarUrl: String,
    onSettingsClick: () -> Unit,
    onRankingClick: () -> Unit,
    onDistanceClick: () -> Unit,
    onTimeClick: () -> Unit,
    onAchievementsClick: () -> Unit,
    activities: List<ActivityRecord>,
    userStats: UserStatistics?,
    userAchievements: UserAchievements?,
    onRefreshClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d. M. yyyy", Locale.getDefault()) }
    val lastActivities = activities.take(100)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = userName, fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(text = userTeam, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AsyncImage(
                model = userAvatarUrl,
                contentDescription = "Avatar",
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                error = painterResource(id = R.drawable.ic_profile_placeholder)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text("Nastavení", fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Synchronizovat"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LevelStatCard(
                userStats = userStats,
                onClick = { }
            )
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Celková vzdálenost",
                    value = if (userStats != null) String.format(java.util.Locale.US, "%.2f km", userStats.total_distance / 1000) else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onDistanceClick
                )
                StatCard(
                    label = "Celková doba",
                    value = if (userStats != null) "${userStats.total_time / 3600}h ${(userStats.total_time % 3600) / 60}m" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onTimeClick
                )
            }
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Umístění",
                    value = if (userStats != null && userStats.rank > 0) userStats.rank.toString() else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onRankingClick
                )
                StatCard(
                    label = "Počet odznaků",
                    value = if (userAchievements != null) "${userAchievements.earnedCount()} / ${userAchievements.totalCount()}" else "0 / 8",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onAchievementsClick
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Moje aktivity:",
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (lastActivities.isEmpty()) {
            Text(
                text = "Žádné aktivity k zobrazení.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 300.dp)) {
                items(lastActivities) { act ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = act.name,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val formattedType = act.activity_type.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                }
                                val formattedDate = LocalDate.parse(act.start_date.substring(0, 10)).format(dateFormatter)

                                Text(
                                    text = "$formattedType • $formattedDate",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${String.format("%.2f", act.distance / 1000.0)} km",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${act.duration / 60} min",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelStatCard(userStats: UserStatistics?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(0.6f, fill = false)
                ) {
                    Text(
                        text = "AKTUÁLNÍ ÚROVEŇ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Úroveň ${userStats?.level ?: "?"}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(0.4f, fill = false)
                ) {
                    Text(
                        text = "ZKUŠENOSTI (XP)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (userStats != null) "${userStats.current_level_xp} / ${userStats.xp_for_next_level} XP" else "Načítání...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProgressTeal
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val progress = if (userStats != null && userStats.xp_for_next_level > 0) {
                userStats.current_level_xp.toFloat() / userStats.xp_for_next_level.toFloat()
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = ProgressTeal,
                trackColor = ProgressTeal.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚡", fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Za každý naběhaný kilometr získáš 100 XP.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
