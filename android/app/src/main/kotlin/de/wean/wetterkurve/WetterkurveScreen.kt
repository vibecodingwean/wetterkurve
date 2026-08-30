package de.wean.wetterkurve

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.wean.wetterkurve.chart.ChartRenderer

private val Background = Color(0xFF202A3A)
private val Card = Color(0x33080C16)
private val Active = Color(0xCC499DFF)
private val Border = Color(0x33FFFFFF)
private val White = Color(0xFFFFFFFF)
private val Muted = Color(0xB8EBF5FF)
private val ButtonBg = Color(0xFF2E6BB8)

@Composable
fun WetterkurveScreen(state: UiState, model: WetterkurveViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Wetterkurve", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(model.t("savedLocations"), color = Muted, fontSize = 16.sp)
        state.locations.forEachIndexed { index, location ->
            val active = index == state.activeLocation
            Text(
                location.label.ifBlank { location.name },
                color = White,
                fontSize = 20.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Active else Card)
                    .border(1.dp, if (active) Color(0x88499DFF) else Border, RoundedCornerShape(16.dp))
                    .clickable { model.selectLocation(index) }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            if (state.locations.size < WeatherService.MAX_LOCATIONS) {
                BigButton(model.t("addLocation"), Modifier.weight(1f), outlined = true) {
                    model.showSearch()
                }
            }
            if (state.locations.size > 1) {
                BigButton("−  ${model.t("remove")}", Modifier.weight(1f), outlined = true) {
                    model.removeActiveLocation()
                }
            }
        }
        if (state.searchVisible) {
            SearchBox(state, model)
        }
        CurrentCard(state)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            BigButton(model.t("clouds"), Modifier.weight(1f), outlined = !state.showClouds) {
                model.toggleClouds()
            }
            BigButton(model.t("wind"), Modifier.weight(1f), outlined = !state.showWind) {
                model.toggleWind()
            }
        }
        ChartLegend(model)
        Text(model.t("chartRainScale"), color = Muted, fontSize = 14.sp)
        BigButton(model.t("refreshWeather"), Modifier.fillMaxWidth()) {
            model.refresh(true)
        }
        Text(state.status, color = Muted, fontSize = 14.sp)
        Text(model.t("addWidgetHint"), color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun SearchBox(state: UiState, model: WetterkurveViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = model::onSearchQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = White),
            placeholder = { Text(model.t("searchLocation"), color = Muted, fontSize = 18.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                cursorColor = White,
                focusedBorderColor = Color(0x6699CCFF),
                unfocusedBorderColor = Border,
            ),
        )
        Text(state.searchHint, color = Muted, fontSize = 15.sp)
        state.searchResults.forEach { location ->
            val saved = state.locations.any { it.id == location.id }
            Text(
                if (saved) model.t("saved", mapOf("location" to location.label)) else location.label,
                color = White,
                fontSize = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (saved) Color.Transparent else Color(0x22499DFF))
                    .clickable(enabled = !saved) { model.addLocation(location) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        OutlinedButton(
            onClick = model::hideSearch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
        ) {
            Text("×", fontSize = 22.sp)
        }
    }
}

@Composable
private fun ChartLegend(model: WetterkurveViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(model.t("temperature"), ChartRenderer.LegendSample.Temperature)
                LegendRow(model.t("rainChance"), ChartRenderer.LegendSample.Chance)
                LegendRow(model.t("clouds"), ChartRenderer.LegendSample.Clouds)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(model.t("feelsLike"), ChartRenderer.LegendSample.FeelsLike)
                LegendRow(model.t("rainAmount"), ChartRenderer.LegendSample.Amount)
                LegendRow(model.t("wind"), ChartRenderer.LegendSample.Wind)
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, sample: ChartRenderer.LegendSample) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(48.dp, 20.dp)) {
            ChartRenderer.paintLegendSample(
                drawContext.canvas.nativeCanvas,
                sample,
                size.width,
                size.height,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = White, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun CurrentCard(state: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .border(1.dp, Border, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconDrawable(state.icon)),
            contentDescription = state.condition,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(state.title, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(state.condition, color = Muted, fontSize = 16.sp)
        }
        Text(state.temperature, color = White, fontSize = 40.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun BigButton(
    label: String,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White),
        ) {
            Text(label, fontSize = 18.sp)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ButtonBg, contentColor = White),
        ) {
            Text(label, fontSize = 18.sp)
        }
    }
}
