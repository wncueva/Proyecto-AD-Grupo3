package com.example.billetera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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

/** Datos que captura el dialogo. */
data class DatosOperacion(
    val monto: Double,
    val destino: String = "",
    val servicio: String = "",
    val contrato: String = ""
)

private val SERVICIOS = listOf("LUZ", "AGUA", "INTERNET", "TELEFONO")

/**
 * Dialogo (modal) para ingresar el monto y, segun la accion,
 * la cuenta destino o el codigo de contrato. Valida antes de enviar.
 */
@Composable
fun OperacionDialog(
    accion: Accion,
    onCerrar: () -> Unit,
    onConfirmar: (DatosOperacion) -> Unit
) {
    var montoTexto by rememberSaveable { mutableStateOf("") }
    var destino by rememberSaveable { mutableStateOf("") }
    var servicio by rememberSaveable { mutableStateOf(SERVICIOS.first()) }
    var contrato by rememberSaveable { mutableStateOf("") }
    var intentoEnviar by rememberSaveable { mutableStateOf(false) }

    // ---------- Validaciones ----------
    val monto = montoTexto.replace(',', '.').toDoubleOrNull()
    val errorMonto = when {
        monto == null -> "Ingresa un monto valido"
        monto <= 0 -> "El monto debe ser mayor a 0"
        else -> null
    }
    val errorDestino = when {
        accion != Accion.TRANSFERIR -> null
        destino.isBlank() -> "Ingresa la cuenta destino"
        destino == Config.CUENTA -> "No puedes transferirte a ti mismo"
        else -> null
    }
    val errorContrato = when {
        accion != Accion.PAGAR -> null
        contrato.trim().length < 4 -> "Minimo 4 caracteres"
        else -> null
    }
    val valido = errorMonto == null && errorDestino == null && errorContrato == null

    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Icon(accion.icono, contentDescription = null) },
        title = { Text(accion.titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (accion == Accion.TRANSFERIR) {
                    OutlinedTextField(
                        value = destino,
                        onValueChange = { destino = it.filter(Char::isDigit) },
                        label = { Text("Cuenta destino (ej. 1002)") },
                        singleLine = true,
                        isError = intentoEnviar && errorDestino != null,
                        supportingText = { if (intentoEnviar && errorDestino != null) Text(errorDestino) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (accion == Accion.PAGAR) {
                    Text("Servicio")
                    SERVICIOS.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fila.forEach { s ->
                                FilterChip(
                                    selected = servicio == s,
                                    onClick = { servicio = s },
                                    label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) },
                                    leadingIcon = { Icon(iconoServicio(s), contentDescription = null) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = contrato,
                        onValueChange = { contrato = it.uppercase() },
                        label = { Text("Codigo de contrato") },
                        singleLine = true,
                        isError = intentoEnviar && errorContrato != null,
                        supportingText = { if (intentoEnviar && errorContrato != null) Text(errorContrato) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = montoTexto,
                    onValueChange = { nuevo ->
                        if (nuevo.count { it == '.' || it == ',' } <= 1 &&
                            nuevo.all { it.isDigit() || it == '.' || it == ',' }
                        ) montoTexto = nuevo
                    },
                    label = { Text("Monto") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    isError = intentoEnviar && errorMonto != null,
                    supportingText = { if (intentoEnviar && errorMonto != null) Text(errorMonto) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                intentoEnviar = true
                if (valido && monto != null) {
                    onConfirmar(DatosOperacion(monto, destino, servicio, contrato.trim()))
                }
            }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) { Text("Cancelar") }
        }
    )
}
