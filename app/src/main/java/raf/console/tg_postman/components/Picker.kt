package raf.console.tg_postman.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextAlign

@Composable
fun FrequencyPicker(
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val timeUnits = listOf("секунда", "минута", "час", "день", "неделя", "месяц", "год")
    val selectedIndex = timeUnits.indexOf(selectedUnit).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {
        /*Text(
            "Единица",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )*/

        Box(
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .background(
                    color = if (enabled) MaterialTheme.colorScheme.surface else Color.LightGray.copy(
                        alpha = 0.2f
                    ),
                    shape = RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!enabled) {
                Text(
                    "Отключено",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .scrollable(
                            state = rememberScrollState(),
                            orientation = Orientation.Vertical,
                            enabled = false,
                            interactionSource = remember { MutableInteractionSource() }
                        ),
                    contentPadding = PaddingValues(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(timeUnits.size) { index ->
                        val unit = timeUnits[index]
                        val isSelected = listState.firstVisibleItemIndex == index

                        Text(
                            text = unit,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .padding(4.dp)
                                .alpha(if (isSelected) 1f else 0.4f)
                        )
                    }
                }

                // Обновление выбранного значения при остановке скролла
                LaunchedEffect(listState.isScrollInProgress) {
                    if (!listState.isScrollInProgress) {
                        val index = listState.firstVisibleItemIndex
                        if (index in timeUnits.indices) {
                            onUnitSelected(timeUnits[index])
                        }
                    }
                }

                // Автопрокрутка к новому значению
                LaunchedEffect(selectedUnit) {
                    val index = timeUnits.indexOf(selectedUnit)
                    if (index != listState.firstVisibleItemIndex) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                }
            }
        }
    }
}
