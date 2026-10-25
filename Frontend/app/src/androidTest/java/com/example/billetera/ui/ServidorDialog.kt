package com.example.billetera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.billetera.Config

/**
 * Dialogo para cambiar la direccion del backend sin recompilar la app.
 * Ejemplos:
 *  - Emulador:      http://10.0.2.2:3000
 *  - Celular WiFi:  http://192.168.1.15:3000
 *  - Nube:          https://mi-billetera.onrender.com
 */
@Composable
fun ServidorDialog(
    actual: String,
    onCerrar: () -> Unit,
    onGuardar: (String) -> Unit
) {
    var texto by rememberSaveable { mutableStateOf(actual) }

    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        title = { Text("Servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Direccion del backend (REST + WebSocket):",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.trim() },
                    singleLine = true,
                    placeholder = { Text("http://192.168.1.15:3000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "• Emulador: http://10.0.2.2:3000\n" +
                        "• Celular en el mismo WiFi: http://IP-de-la-PC:3000\n" +
                        "• Nube: https://tu-app.onrender.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AssistChip(
                    onClick = { texto = Config.SERVIDOR_POR_DEFECTO },
                    label = { Text("Usar el del emulador") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onGuardar(texto) },
                enabled = texto.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Cancelar") }
        }
    )
}
