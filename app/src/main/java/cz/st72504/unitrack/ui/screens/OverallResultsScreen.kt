package cz.st72504.unitrack.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import cz.st72504.unitrack.R
import cz.st72504.unitrack.model.PublicActivityRecord
import cz.st72504.unitrack.model.TeamStatistics
import cz.st72504.unitrack.ui.theme.FacultyColors
import cz.st72504.unitrack.ui.theme.UpceRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun OverallResultsView(
    teamStats: List<TeamStatistics>,
    publicActivities: List<PublicActivityRecord>,
    onRefreshClick: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d. M. yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Celkové výsledky",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

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

        if (teamStats.isEmpty()) {
            Box(Modifier.fillMaxSize().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UpceRed)
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Žebříček",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    teamStats.forEach { team ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${team.rank}.",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                modifier = Modifier.width(36.dp),
                                color = if (team.rank <= 3) UpceRed else MaterialTheme.colorScheme.onSurface
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.team,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Účastníků: ${team.runners_count}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = String.format(java.util.Locale.US, "%.1f km", team.total_distance / 1000),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (teamStats.last() != team) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 0.dp))
                        }
                    }
                }
            }
        }

        if (teamStats.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Podíl kilometrů",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    FacultyDonutChart(teamStats)
                }
            }
        }

        Text(
            text = "Poslední aktivity:",
            modifier = Modifier.padding(bottom = 12.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (publicActivities.isEmpty()) {
            Text(
                text = "Žádné aktivity k zobrazení.",
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 600.dp)) {
                items(publicActivities) { act ->
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = if (act.user_id.isNotEmpty() && act.user_avatar.isNotEmpty()) {
                                    "https://pb.unitrack.fun/api/files/_pb_users_auth_/${act.user_id}/${act.user_avatar}"
                                } else {
                                    null
                                },
                                contentDescription = "Avatar",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop,
                                placeholder = painterResource(id = R.drawable.ic_profile_placeholder),
                                error = painterResource(id = R.drawable.ic_profile_placeholder)
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${act.user_name} [${act.user_team}]",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val displayType = act.activity_type.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                }
                                val formattedDate = LocalDate.parse(act.start_date.substring(0, 10)).format(dateFormatter)

                                Text(
                                    text = "$displayType • $formattedDate",
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

@Composable
fun FacultyDonutChart(teamStats: List<TeamStatistics>) {
    val totalDistance = teamStats.sumOf { it.total_distance }
    val totalDistanceKm = totalDistance / 1000.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1.2f)
                .aspectRatio(1f)
                .wrapContentHeight(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(190.dp)) {
                var startAngle = -90f
                teamStats.forEachIndexed { index, team ->
                    val sweepAngle = if (totalDistance > 0) {
                        (team.total_distance / totalDistance).toFloat() * 360f
                    } else 0f

                    drawArc(
                        color = FacultyColors.getOrElse(index) { Color.Gray },
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Butt)
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                @Suppress("DEPRECATION")
                Text(
                    text = "CELKEM",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.0f km", totalDistanceKm),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(40.dp))

        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            teamStats.forEachIndexed { index, team ->
                val percentage = if (totalDistance > 0) {
                    (team.total_distance.toDouble() / totalDistance * 100)
                } else 0.0

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                color = FacultyColors.getOrElse(index) { Color.Gray },
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${team.team} (${String.format(java.util.Locale.US, "%.1f%%", percentage)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
