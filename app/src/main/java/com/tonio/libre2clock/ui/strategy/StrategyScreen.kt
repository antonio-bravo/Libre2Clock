package com.tonio.libre2clock.ui.strategy

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonio.libre2clock.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrategyScreen(
    onBack: () -> Unit
) {
    val strategies = remember {
        listOf(
            TrendStrategy(
                icon = Icons.Default.KeyboardDoubleArrowUp,
                color = Color.Red,
                directionRes = R.string.trend_rising_very_fast,
                insulinRes = R.string.adj_rising_very_fast,
                hcRes = R.string.desc_rising_very_fast
            ),
            TrendStrategy(
                icon = Icons.Default.KeyboardArrowUp,
                color = Color(0xFFFF5722), // Deep Orange
                directionRes = R.string.trend_rising_fast,
                insulinRes = R.string.adj_rising_fast,
                hcRes = R.string.desc_rising_fast
            ),
            TrendStrategy(
                icon = Icons.Default.NorthEast,
                color = Color(0xFFFF9800), // Orange
                directionRes = R.string.trend_rising_slowly,
                insulinRes = R.string.adj_rising_slowly,
                hcRes = R.string.desc_rising_slowly
            ),
            TrendStrategy(
                icon = Icons.Default.East,
                color = Color.Gray,
                directionRes = R.string.trend_stable,
                insulinRes = R.string.adj_stable,
                hcRes = R.string.desc_stable
            ),
            TrendStrategy(
                icon = Icons.Default.SouthEast,
                color = Color(0xFF8BC34A), // Light Green
                directionRes = R.string.trend_falling_slowly,
                insulinRes = R.string.adj_falling_slowly,
                hcRes = R.string.desc_falling_slowly
            ),
            TrendStrategy(
                icon = Icons.Default.KeyboardArrowDown,
                color = Color(0xFF4CAF50), // Green
                directionRes = R.string.trend_falling_fast,
                insulinRes = R.string.adj_falling_fast,
                hcRes = R.string.desc_falling_fast
            ),
            TrendStrategy(
                icon = Icons.Default.KeyboardDoubleArrowDown,
                color = Color(0xFF2E7D32), // Dark Green
                directionRes = R.string.trend_falling_very_fast,
                insulinRes = R.string.adj_falling_very_fast,
                hcRes = R.string.desc_falling_very_fast
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.strategy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            strategies.forEach { strategy ->
                StrategyCard(strategy)
            }
        }
    }
}

@Composable
private fun StrategyCard(strategy: TrendStrategy) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = strategy.icon,
                contentDescription = null,
                tint = strategy.color,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(strategy.directionRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.strategy_insulin_adj, stringResource(strategy.insulinRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(strategy.hcRes),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

private data class TrendStrategy(
    val icon: ImageVector,
    val color: Color,
    val directionRes: Int,
    val insulinRes: Int,
    val hcRes: Int
)
