package cz.st72504.unitrack2.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.scroll.rememberChartScrollSpec
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import cz.st72504.unitrack2.model.UserDailyActivity
import cz.st72504.unitrack2.model.UserStatistics
import cz.st72504.unitrack2.ui.theme.UpceBlue
import cz.st72504.unitrack2.ui.components.StatCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeStatsScreen(
    userStats: UserStatistics?,
    onBack: () -> Unit,
    dailyActivities: List<UserDailyActivity>
) {
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistiky času") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCard(
                    label = "Průměrná doba",
                    value = if (userStats != null) "${(userStats.avg_time / 60).toInt()}m ${(userStats.avg_time % 60).toInt()}s" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
                StatCard(
                    label = "Nejdelší doba",
                    value = if (userStats != null) "${userStats.longest_time / 3600}h ${(userStats.longest_time % 3600) / 60}m" else "Načítání...",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {}
                )
            }
            DailyTimeChart(dailyActivities)
        }
    }
}

@Composable
fun DailyTimeChart(activities: List<UserDailyActivity>) {
    val currentLocale = remember { java.util.Locale.getDefault() }
    val dayFormatter = DateTimeFormatter.ofPattern("EEE", currentLocale)

    val today = LocalDate.now()
    val startDate = today.minusDays(6)

    val activitiesByDate = activities.associateBy { LocalDate.parse(it.day.substring(0, 10)) }

    val chartEntries = (0..6).map { i ->
        val date = startDate.plusDays(i.toLong())
        val duration = activitiesByDate[date]?.total_duration ?: 0
        entryOf(i.toFloat(), (duration.toFloat() / 60f))
    }

    val maxTimeMinutes = chartEntries.maxOfOrNull { it.y } ?: 0f
    val targetMax = maxTimeMinutes * 1.2f

    val dynamicMaxY = when {
        targetMax <= 0f -> 30f
        targetMax <= 30f -> 30f
        targetMax <= 60f -> 60f
        targetMax <= 90f -> 90f
        targetMax <= 120f -> 120f
        else -> (ceil(targetMax / 30.0) * 30).toFloat()
    }

    val yStep = when {
        dynamicMaxY <= 30f -> 5f
        dynamicMaxY <= 60f -> 10f
        dynamicMaxY <= 120f -> 20f
        else -> 30f
    }
    val yAxisItemCount = (dynamicMaxY / yStep).toInt() + 1

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }
    LaunchedEffect(chartEntries) {
        chartEntryModelProducer.setEntries(chartEntries)
    }

    val bottomAxisValueFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        val date = startDate.plusDays(value.toLong())
        dayFormatter.format(date).replaceFirstChar { it.titlecase(currentLocale) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Minuty za posledních 7 dní",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Chart(
                chart = columnChart(
                    columns = listOf(
                        remember(UpceBlue) {
                            com.patrykandpatrick.vico.core.component.shape.LineComponent(
                                color = UpceBlue.toArgb(),
                                thicknessDp = 24f,
                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(allPercent = 30)
                            )
                        }
                    )
                ).apply {
                    axisValuesOverrider = com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider.fixed(
                        minY = 0f,
                        maxY = dynamicMaxY
                    )
                },
                chartModelProducer = chartEntryModelProducer,
                startAxis = rememberStartAxis(
                    itemPlacer = remember(yAxisItemCount) {
                        com.patrykandpatrick.vico.core.axis.AxisItemPlacer.Vertical.default(
                            maxItemCount = yAxisItemCount
                        )
                    },
                    valueFormatter = { value, _ -> String.format(java.util.Locale.US, "%.0f min", value) }
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = bottomAxisValueFormatter
                ),
                chartScrollSpec = rememberChartScrollSpec(isScrollEnabled = false),
                modifier = Modifier
                    .height(200.dp)
                    .fillMaxWidth()
            )
        }
    }
}
