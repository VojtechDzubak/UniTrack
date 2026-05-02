package cz.st72504.unitrack.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.st72504.unitrack.model.UserAchievements
import cz.st72504.unitrack.ui.theme.ProgressTeal
import cz.st72504.unitrack.ui.theme.UpceRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(achievements: UserAchievements?, onBack: () -> Unit) {
    BackHandler { onBack() }

    val metadataList = listOf(
        AchievementMetadata("badge_run_50", "Vytrvalostní běžec", "Uběhni celkem alespoň 50 km.", 500),
        AchievementMetadata("badge_walk_20", "Univerzitní chodec", "Ujdi celkem alespoň 20 km.", 400),
        AchievementMetadata("badge_ride_100", "Král cyklostezek", "Najezdi na kole celkem alespoň 100 km.", 600),
        AchievementMetadata("badge_total_150", "Multisportovec", "Dosáhni celkem 150 km v součtu všech aktivit.", 1000),
        AchievementMetadata("badge_single_20", "Železná vůle", "Absolvuj jednu aktivitu delší než 20 km.", 800),
        AchievementMetadata("badge_morning", "Ranní ptáče", "Zahaj aktivitu před 6:00 ráno.", 300),
        AchievementMetadata("badge_sprinter", "Rychlík", "Běhej s tempem pod 4:00 min/km (aspoň 1 km).", 500),
        AchievementMetadata("badge_weekend", "Víkendový bojovník", "Zaznamenej aktivitu v sobotu i v neděli.", 250)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje odznaky") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(metadataList) { meta ->
                val isEarned = when (meta.key) {
                    "badge_run_50" -> (achievements?.badge_run_50 ?: 0) > 0
                    "badge_walk_20" -> (achievements?.badge_walk_20 ?: 0) > 0
                    "badge_ride_100" -> (achievements?.badge_ride_100 ?: 0) > 0
                    "badge_total_150" -> (achievements?.badge_total_150 ?: 0) > 0
                    "badge_single_20" -> (achievements?.badge_single_20 ?: 0) > 0
                    "badge_morning" -> (achievements?.badge_morning ?: 0) > 0
                    "badge_sprinter" -> (achievements?.badge_sprinter ?: 0) > 0
                    "badge_weekend" -> (achievements?.badge_weekend ?: 0) > 0
                    else -> false
                }

                AchievementItem(meta, isEarned)
            }
        }
    }
}

@Composable
fun AchievementItem(meta: AchievementMetadata, isEarned: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isEarned) BorderStroke(2.dp, UpceRed) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        if (isEarned) UpceRed.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (isEarned) "🏅" else "🔒", fontSize = 24.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (isEarned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = meta.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "+${meta.xp} XP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isEarned) ProgressTeal else Color.Gray
                )
            }
        }
    }
}

data class AchievementMetadata(
    val key: String,
    val name: String,
    val description: String,
    val xp: Int
)
