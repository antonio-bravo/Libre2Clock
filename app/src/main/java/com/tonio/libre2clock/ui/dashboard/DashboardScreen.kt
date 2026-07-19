package com.tonio.libre2clock.ui.dashboard

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonio.libre2clock.data.model.GlucoseMeasurement
import com.tonio.libre2clock.data.model.SensorStatus
import com.tonio.libre2clock.data.repository.GlucoseProcessor
import com.tonio.libre2clock.util.TimestampParser
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit
) {
    val currentGlucose by viewModel.currentGlucose.collectAsStateWithLifecycle()
    val sensorStatus by viewModel.sensorStatus.collectAsStateWithLifecycle()
    val historicalData by viewModel.historicalData.collectAsStateWithLifecycle()
    val isHistoryRefreshing by viewModel.isHistoryRefreshing.collectAsStateWithLifecycle()
    val dashboardMetrics = remember(historicalData) { calculateDashboardMetrics(historicalData) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Libre2Clock") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlucoseCard(currentGlucose, dashboardMetrics)
            Spacer(modifier = Modifier.height(16.dp))
            SensorHealthCard(sensorStatus, onRefresh = viewModel::refresh)
            Spacer(modifier = Modifier.height(16.dp))
            DashboardSlidesCard(
                metrics = dashboardMetrics,
                isRefreshing = isHistoryRefreshing,
                onRefresh = viewModel::refreshHistoryWindow
            )
            Spacer(modifier = Modifier.height(12.dp))
            InteractiveTrendGraph(
                measurements = historicalData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            )
        }
    }
}

@Composable
fun SensorHealthCard(
    status: SensorStatus?,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sensor Health",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (status != null) {
                    Row {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh sensor info",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = """
                                    Sensor SN: ${status.serialNumber}
                                    ${status.startDate}
                                    ${status.expiryDate}
                                    Remaining: ${status.daysRemaining} days
                                """.trimIndent()
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Sensor Info", text)))
                                }
                                Toast.makeText(context, "Sensor info copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy sensor info",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = status.daysRemaining,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = status.startDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = status.expiryDate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = "SN: ${status.serialNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                    )
                }
            } else {
                Text(
                    text = "No sensor data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun GlucoseCard(measurement: GlucoseMeasurement?, metrics: DashboardMetrics) {
    // Format timestamp to yyyy-MM-dd HH:mm:ss using TimestampParser for flexibility
    val lastSyncText = measurement?.timestamp?.let { timestamp ->
        TimestampParser.parseFlexibleInstant(timestamp)?.let { instant ->
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        } ?: timestamp
    } ?: "------ --:--:--"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CornerMetric(
                    title = "Estimated HbA1c (90d)",
                    primary = metrics.estimatedA1c.primary,
                    secondary = metrics.estimatedA1c.secondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                CornerMetric(
                    title = "Avg Glucose",
                    primary = metrics.todayAvg.primary,
                    secondary = metrics.todayAvg.secondary,
                    alignEnd = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (measurement != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val displayValue = GlucoseProcessor.formatDualValue(measurement.value, measurement.calibratedValue)
                        Text(
                            text = displayValue,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (displayValue.length > 6) 48.sp else 64.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "mg/dL",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            TrendIcon(measurement.trendArrow)
                        }
                    }
                    Text(
                        // text = "Last sync: ${measurement.timestamp}",
                        text = "Last sync: ${lastSyncText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    CircularProgressIndicator()
                    Text("Fetching data...")
                }
            }
        }
    }
}

@Composable
private fun CornerMetric(
    title: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            maxLines = 1
        )
        Text(
            text = primary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
        if (secondary.isNotEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardSlidesCard(
    metrics: DashboardMetrics,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val pageTitle = when (pagerState.currentPage) {
        0 -> "Avg Glucose"
        1 -> "Avg Glucose Last Month"
        else -> "Hypos Last Month"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { index ->
                            val active = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (active) Color(0xFF0B57D0) else Color(0xFFD6DCE5),
                                        shape = RoundedCornerShape(50)
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh historical data"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MetricsRow(
                        first = metrics.yesterdayAvg,
                        second = metrics.weekAvg,
                        third = metrics.monthAvg,
                        firstLabel = "Yesterday",
                        secondLabel = "Week",
                        thirdLabel = "Month"
                    )

                    1 -> MetricsRow(
                        first = metrics.breakfastMonthAvg,
                        second = metrics.lunchMonthAvg,
                        third = metrics.dinnerMonthAvg,
                        firstLabel = "Breakfast",
                        secondLabel = "Lunch",
                        thirdLabel = "Dinner"
                    )

                    else -> HyposRow(
                        breakfast = metrics.breakfastHypos,
                        lunch = metrics.lunchHypos,
                        dinner = metrics.dinnerHypos
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsRow(
    first: DisplayMetric,
    second: DisplayMetric,
    third: DisplayMetric,
    firstLabel: String,
    secondLabel: String,
    thirdLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCell(metric = first, label = firstLabel, modifier = Modifier.weight(1f))
        MetricCell(metric = second, label = secondLabel, modifier = Modifier.weight(1f))
        MetricCell(metric = third, label = thirdLabel, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HyposRow(
    breakfast: CountMetric,
    lunch: CountMetric,
    dinner: CountMetric
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HypoCell(metric = breakfast, label = "Breakfast", modifier = Modifier.weight(1f))
        HypoCell(metric = lunch, label = "Lunch", modifier = Modifier.weight(1f))
        HypoCell(metric = dinner, label = "Dinner", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(metric: DisplayMetric, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = metric.primary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (metric.secondary.isNotEmpty()) {
            Text(
                text = metric.secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun HypoCell(metric: CountMetric, label: String, modifier: Modifier = Modifier) {
    val rawCount = metric.count - metric.offset
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$rawCount(${metric.count})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

private data class DisplayMetric(
    val primary: String,
    val secondary: String
)

private data class CountMetric(
    val count: Int,
    val offset: Int
)

private data class DashboardMetrics(
    val estimatedA1c: DisplayMetric,
    val todayAvg: DisplayMetric,
    val yesterdayAvg: DisplayMetric,
    val weekAvg: DisplayMetric,
    val monthAvg: DisplayMetric,
    val breakfastMonthAvg: DisplayMetric,
    val lunchMonthAvg: DisplayMetric,
    val dinnerMonthAvg: DisplayMetric,
    val breakfastHypos: CountMetric,
    val lunchHypos: CountMetric,
    val dinnerHypos: CountMetric
)

private enum class MealSlot { BREAKFAST, LUNCH, DINNER }

private fun calculateDashboardMetrics(measurements: List<GlucoseMeasurement>): DashboardMetrics {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val startOfToday = today.atStartOfDay(zone).toInstant()
    val startOfYesterday = today.minusDays(1).atStartOfDay(zone).toInstant()
    val startOfWeekWindow = now.minusSeconds(7L * 24L * 60L * 60L)
    val startOfMonthWindow = now.minusSeconds(30L * 24L * 60L * 60L)
    val startOfA1cWindow = now.minusSeconds(90L * 24L * 60L * 60L)

    val dated = measurements.mapNotNull { m ->
        parseMeasurementInstant(m)?.let { instant -> instant to m }
    }

    val todayItems = dated.filter { (instant, _) -> !instant.isBefore(startOfToday) }.map { it.second }
    val yesterdayItems = dated.filter { (instant, _) -> instant >= startOfYesterday && instant < startOfToday }.map { it.second }
    val weekItems = dated.filter { (instant, _) -> !instant.isBefore(startOfWeekWindow) }.map { it.second }
    val monthItems = dated.filter { (instant, _) -> !instant.isBefore(startOfMonthWindow) }.map { it.second }

    val breakfastMonth = dated.filter { (instant, _) ->
        !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.BREAKFAST
    }.map { it.second }
    val lunchMonth = dated.filter { (instant, _) ->
        !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.LUNCH
    }.map { it.second }
    val dinnerMonth = dated.filter { (instant, _) ->
        !instant.isBefore(startOfMonthWindow) && mealSlotOf(instant, zone) == MealSlot.DINNER
    }.map { it.second }

    val a1cWindowItems = dated.filter { (instant, _) -> !instant.isBefore(startOfA1cWindow) }.map { it.second }
    val allForA1c = if (a1cWindowItems.isNotEmpty()) a1cWindowItems else measurements
    
    val avgRawForA1c = if (allForA1c.isNotEmpty()) allForA1c.map { it.value }.average() else 0.0
    val avgCalibratedForA1c = if (allForA1c.isNotEmpty()) allForA1c.map { it.calibratedValue }.average() else 0.0
    
    val estimatedA1c = if (avgCalibratedForA1c > 0.0) {
        val a1cRaw = (avgRawForA1c + 46.7) / 28.7
        val a1cCalibrated = (avgCalibratedForA1c + 46.7) / 28.7
        DisplayMetric(
            primary = String.format(Locale.US, "%.1f%%(%.1f%%)", a1cRaw, a1cCalibrated),
            secondary = ""
        )
    } else {
        DisplayMetric("--", "")
    }

    return DashboardMetrics(
        estimatedA1c = estimatedA1c,
        todayAvg = buildDisplayMetric(todayItems),
        yesterdayAvg = buildDisplayMetric(yesterdayItems),
        weekAvg = buildDisplayMetric(weekItems),
        monthAvg = buildDisplayMetric(monthItems),
        breakfastMonthAvg = buildDisplayMetric(breakfastMonth),
        lunchMonthAvg = buildDisplayMetric(lunchMonth),
        dinnerMonthAvg = buildDisplayMetric(dinnerMonth),
        breakfastHypos = buildHypoCountMetric(breakfastMonth),
        lunchHypos = buildHypoCountMetric(lunchMonth),
        dinnerHypos = buildHypoCountMetric(dinnerMonth)
    )
}

private fun buildDisplayMetric(measurements: List<GlucoseMeasurement>): DisplayMetric {
    if (measurements.isEmpty()) {
        return DisplayMetric(primary = "--", secondary = "")
    }

    val rawValues = measurements.map { it.value }
    val calibratedValues = measurements.map { it.calibratedValue }
    
    val avgRaw = rawValues.average().roundToInt()
    val avgCalibrated = calibratedValues.average().roundToInt()
    
    val maxRaw = rawValues.maxOrNull() ?: avgRaw
    val minRaw = rawValues.minOrNull() ?: avgRaw
    val oscRaw = maxOf(maxRaw - avgRaw, avgRaw - minRaw).coerceAtLeast(0)

    val maxCal = calibratedValues.maxOrNull() ?: avgCalibrated
    val minCal = calibratedValues.minOrNull() ?: avgCalibrated
    val oscCal = maxOf(maxCal - avgCalibrated, avgCalibrated - minCal).coerceAtLeast(0)

    return DisplayMetric(
        primary = "Avg $avgRaw ± $oscRaw",
        secondary = "(avg $avgCalibrated ± $oscCal)"
    )
}

private fun buildHypoCountMetric(measurements: List<GlucoseMeasurement>): CountMetric {
    if (measurements.isEmpty()) return CountMetric(count = 0, offset = 0)

    val calibratedHypoCount = measurements.count { it.calibratedValue < 70 }
    val rawHypoCount = measurements.count { it.value < 70 }
    return CountMetric(
        count = calibratedHypoCount,
        offset = calibratedHypoCount - rawHypoCount
    )
}

private fun mealSlotOf(instant: Instant, zone: ZoneId): MealSlot? {
    val time = instant.atZone(zone).toLocalTime()
    return when {
        time >= LocalTime.of(5, 0) && time < LocalTime.of(12, 0) -> MealSlot.BREAKFAST
        time >= LocalTime.of(12, 0) && time < LocalTime.of(17, 0) -> MealSlot.LUNCH
        time >= LocalTime.of(17, 0) -> MealSlot.DINNER
        else -> null
    }
}

private fun parseMeasurementInstant(measurement: GlucoseMeasurement): Instant? {
    return parseFlexibleInstant(measurement.factoryTimestamp) ?: parseFlexibleInstant(measurement.timestamp)
}

private fun parseFlexibleInstant(timestamp: String): Instant? {
    return TimestampParser.parseFlexibleInstant(timestamp)
}

@Composable
fun TrendIcon(trend: Int?) {
    val symbol = GlucoseProcessor.getTrendArrowSymbol(trend)
    val color = when (trend) {
        1, 2 -> Color.Red
        4, 5 -> Color.Green
        else -> Color.Gray
    }
    Text(
        text = symbol,
        color = color,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold
    )
}
