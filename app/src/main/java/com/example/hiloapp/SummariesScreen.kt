package com.example.hiloapp

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummariesScreen(
    summariesViewModel: SummariesViewModel,
    monitoredChats: List<Chat> = emptyList(),
    modifier: Modifier = Modifier
) {
    val summaries by summariesViewModel.summaries.collectAsState()
    val isLoading by summariesViewModel.isLoading.collectAsState()
    val error by summariesViewModel.error.collectAsState()
    val generatingGroupId by summariesViewModel.generatingGroupId.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize().background(Color.White)) {
        Surface(color = Color.White, shadowElevation = 2.dp) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Resúmenes Diarios",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "${summaries.size} chats monitoreados en resumen",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    IconButton(
                        onClick = {
                            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                            summariesViewModel.generateAllForMonitored(monitoredChats, date = today) { ok, fail ->
                                val txt = "Resúmenes actualizados: $ok OK, $fail fallidos"
                                Toast.makeText(context, txt, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = generatingGroupId == null && monitoredChats.any { it.isMonitored }
                    ) {
                        if (generatingGroupId != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Actualizar todos",
                                tint = Color(0xFF10B981)
                            )
                        }
                    }
                }
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Cargando resúmenes...", color = Color(0xFF8E8E93), fontSize = 14.sp)
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("⚠️", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(error ?: "", color = Color(0xFF8E8E93), fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { summariesViewModel.clearError() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) { Text("Reintentar", color = Color.White) }
                    }
                }
            }
            summaries.isEmpty() -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("📊", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No hay resúmenes disponibles.\nActiva el monitoreo de un chat y espera al día siguiente.",
                            color = Color(0xFF8E8E93),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFF9F9FB)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(summaries) { _, summary ->
                        SummaryCard(
                            summary = summary,
                            isGenerating = generatingGroupId == summary.groupId,
                            onGenerate = {
                                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                summariesViewModel.generateSummary(summary.groupId, date = today) { success, msg ->
                                    val text = if (success) "Resumen generado correctamente"
                                               else msg ?: "Error al generar resumen"
                                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    summary: SummaryItem,
    isGenerating: Boolean,
    onGenerate: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val aiGradient = Brush.linearGradient(listOf(Color(0xFF1A1A1A), Color(0xFF0C0C0C)))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Group avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(aiGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        summary.groupName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.groupName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            summary.date,
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                        Text(
                            " · ${summary.messageCount} mensajes",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                }
                // Regenerate button
                IconButton(
                    onClick = onGenerate,
                    enabled = !isGenerating,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF10B981)
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Regenerar resumen",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                // Expand/collapse arrow
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir",
                    tint = Color(0xFF8E8E93),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Preview (2 lines) always visible
            if (!expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = summary.content
                        .replace(Regex("##\\s*\\d+\\.?\\s*"), "")
                        .replace(Regex("^-\\s*", RegexOption.MULTILINE), "• ")
                        .trim()
                        .take(140),
                    color = Color(0xFF6E6E73),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Full content (expanded)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    SummaryContent(summary.content)
                }
            }
        }
    }
}

/** Renders AI summary markdown into styled sections. */
@Composable
fun SummaryContent(content: String) {
    val lines = content.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            when {
                line.startsWith("##") -> {
                    val title = line.replace(Regex("^#+\\s*"), "").trim()
                    if (title.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp, 16.dp)
                                    .background(Color(0xFF10B981), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
                line.trimStart().startsWith("-") -> {
                    val bullet = line.trimStart().removePrefix("-").trim()
                    if (bullet.isNotEmpty()) {
                        Row(modifier = Modifier.padding(start = 12.dp)) {
                            Text("•  ", color = Color(0xFF10B981), fontSize = 13.sp)
                            Text(bullet, color = Color(0xFF3C3C43), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
                line.isBlank() -> Spacer(modifier = Modifier.height(2.dp))
                else -> {
                    if (line.trim().isNotEmpty()) {
                        Text(
                            line.trim(),
                            color = Color(0xFF3C3C43),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
