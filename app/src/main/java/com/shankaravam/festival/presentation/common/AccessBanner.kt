package com.shankaravam.festival.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shankaravam.festival.core.i18n.appStrings
import com.shankaravam.festival.data.local.SessionPrefs

/**
 * Polite, non-blocking banner when the temple head has revoked this
 * counter's cloud access (feature #1). The local ledger keeps working —
 * this only explains why sync stopped. Renders nothing otherwise.
 *
 * Snapshot read: [SessionPrefs.myStatus] is synchronous, so this re-reads
 * whenever [eventId] changes or the screen re-enters composition (a
 * revocation lands via sync, which always round-trips through navigation).
 */
@Composable
fun RevokedAccessBanner(
    eventId: String?,
    modifier: Modifier = Modifier
) {
    if (eventId == null) return
    val container = rememberContainer()
    val strings = appStrings()
    val revoked = remember(eventId) {
        container.sessionPrefs.myStatus(eventId) == SessionPrefs.STATUS_REVOKED
    }
    if (!revoked) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    strings.accessRevokedTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    strings.accessRevokedBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
