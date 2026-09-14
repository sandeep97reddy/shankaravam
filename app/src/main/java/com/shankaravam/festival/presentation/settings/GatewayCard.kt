package com.shankaravam.festival.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.core.theme.DeepMaroon
import com.shankaravam.festival.core.theme.TempleSaffron
import com.shankaravam.festival.domain.model.VoiceEngineMode
import com.shankaravam.festival.presentation.common.rememberContainer

/**
 * Reusable Temple Media Gateway configuration card.
 *
 * Connects the app to the private Cloudflare Worker (`shankaravam-gateway`)
 * and R2 bucket (`shankaravam-media`):
 * - Enables Sarvam AI cloud voice (Shubh / Pooja) with ZERO API keys on phones.
 * - Enables multi-counter expense receipt photo uploads & viewing.
 * - If empty: app stays 100% offline-first with Android native TTS (te-IN).
 */
@Composable
fun GatewayCard(
    modifier: Modifier = Modifier
) {
    val container = rememberContainer()
    val prefs = container.sessionPrefs
    val activeGatewayUrl by prefs.gatewayBaseUrlFlow.collectAsState()
    var urlDraft by remember(activeGatewayUrl) { mutableStateOf(activeGatewayUrl) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = TempleSaffron,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Temple Media Gateway",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = DeepMaroon
                    )
                }

                if (activeGatewayUrl.isNotBlank()) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Connected",
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = "Connects to your Cloudflare Worker gateway for cloud Telugu voice (Sarvam AI) and shared receipt photos. No Sarvam API key required on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = urlDraft,
                onValueChange = {
                    urlDraft = it
                    saveMessage = null
                },
                label = { Text("Gateway URL") },
                placeholder = { Text("https://shankaravam-gateway.<subdomain>.workers.dev") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val cleaned = urlDraft.trim().trimEnd('/')
                        prefs.gatewayBaseUrl = cleaned
                        if (cleaned.isNotBlank()) {
                            prefs.voiceEngineMode = VoiceEngineMode.SARVAM_CLOUD
                            saveMessage = "✓ Gateway saved! Cloud voice (Sarvam AI) is now active."
                        } else {
                            saveMessage = "Gateway removed. Reverted to offline mode."
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TempleSaffron,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save Gateway URL", fontWeight = FontWeight.Bold)
                }

                if (activeGatewayUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            prefs.gatewayBaseUrl = ""
                            urlDraft = ""
                            saveMessage = "Gateway disconnected. Operating offline."
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disconnect")
                    }
                }

                if (activeGatewayUrl != com.shankaravam.festival.data.local.SessionPrefs.DEFAULT_GATEWAY_URL) {
                    OutlinedButton(
                        onClick = {
                            prefs.gatewayBaseUrl = com.shankaravam.festival.data.local.SessionPrefs.DEFAULT_GATEWAY_URL
                            urlDraft = com.shankaravam.festival.data.local.SessionPrefs.DEFAULT_GATEWAY_URL
                            prefs.voiceEngineMode = VoiceEngineMode.SARVAM_CLOUD
                            saveMessage = "✓ Restored default Temple Gateway."
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset Default")
                    }
                }
            }

            AnimatedVisibility(visible = saveMessage != null) {
                saveMessage?.let { msg ->
                    Surface(
                        color = if (msg.startsWith("✓")) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = if (msg.startsWith("✓")) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
