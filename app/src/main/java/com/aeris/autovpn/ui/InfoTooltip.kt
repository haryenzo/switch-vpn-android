package com.aeris.autovpn.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aeris.autovpn.R

/** Small (i) button that pops up a one-line explanation dialog when tapped. */
@Composable
fun InfoTooltip(text: String) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick = { show = true }, modifier = Modifier.size(22.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text(stringResource(R.string.dialog_ok)) }
            },
        )
    }
}
