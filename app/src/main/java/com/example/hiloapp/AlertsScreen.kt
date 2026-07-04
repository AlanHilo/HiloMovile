@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.hiloapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlertsScreen(
    alertsViewModel: AlertsViewModel,
    modifier: Modifier = Modifier
) {
    val settings by alertsViewModel.settings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            TopAppBar(
                title = {
                    Column {
                        Text("Alertas", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                        Text("Adjuntos prioritarios", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AlertOptionRow(
            icon = {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
            },
            title = "Notificaciones prioritarias",
            subtitle = "Cuando llegue mensaje con imagen, audio, video o archivo, marcar como prioridad.",
            checked = settings.priorityEnabled,
            onCheckedChange = { alertsViewModel.setPriorityEnabled(it) }
        )

        AlertOptionRow(
            icon = {
                Icon(
                    imageVector = Icons.Default.SaveAlt,
                    contentDescription = null,
                    tint = Color(0xFF10B981)
                )
            },
            title = "Guardar adjuntos automáticamente",
            subtitle = "Descargar y guardar en tu teléfono los adjuntos de mensajes nuevos.",
            checked = settings.autoSaveAttachments,
            onCheckedChange = { alertsViewModel.setAutoSaveAttachments(it) }
        )
    }
}

@Composable
private fun AlertOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6E6E73))
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}
