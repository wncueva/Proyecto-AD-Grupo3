package com.example.billetera.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billetera.Config
import com.example.billetera.data.local.TipoOperacion
import com.example.billetera.data.local.TransaccionEntity
import com.example.billetera.data.remote.EstadoConexion
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------- Paleta "fintech" ----------
val VerdeIngreso = Color(0xFF16A34A)
val RojoEgreso = Color(0xFFDC2626)
private val Ambar = Color(0xFFF59E0B)
private val AzulOscuro = Color(0xFF1E3A8A)
private val Indigo = Color(0xFF4F46E5)

/** Que dialogo esta abierto (null = ninguno). */
enum class Accion(val titulo: String, val icono: ImageVector) {
    DEPOSITAR("Depositar", Icons.Filled.ArrowDownward),
    RETIRAR("Retirar", Icons.Filled.ArrowUpward),
    TRANSFERIR("Transferir", Icons.Filled.SwapHoriz),
    PAGAR("Pagar servicios", Icons.Filled.Receipt)
}

@Composable
fun BilleteraScreen(vm: BilleteraViewModel = viewModel()) {
    // collectAsStateWithLifecycle: la UI "escucha" los StateFlow del ViewModel
    val saldo by vm.saldo.collectAsStateWithLifecycle()
    val historial by vm.historial.collectAsStateWithLifecycle()
    val conexion by vm.conexion.collectAsStateWithLifecycle()
    val procesando by vm.procesando.collectAsStateWithLifecycle()

    var accionAbierta by rememberSaveable { mutableStateOf<Accion?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var ultimoAvisoExito by remember { mutableStateOf(true) }

    // Avisos de una sola vez (exito, error, transferencia recibida)
    LaunchedEffect(Unit) {
        vm.avisos.collect { aviso ->
            ultimoAvisoExito = aviso.exito
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(aviso.texto)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (ultimoAvisoExito) VerdeIngreso else RojoEgreso,
                    contentColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (procesando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            TarjetaSaldo(
                saldo = saldo,
                conexion = conexion,
                onReconectar = vm::reconectar,
                modifier = Modifier.padding(16.dp)
            )

            PanelAcciones(
                habilitado = !procesando,
                onAccion = { accionAbierta = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = "Movimientos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
            )

            Historial(
                items = historial,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // ---------- Dialogos ----------
    accionAbierta?.let { accion ->
        OperacionDialog(
            accion = accion,
            onCerrar = { accionAbierta = null },
            onConfirmar = { datos ->
                accionAbierta = null
                when (accion) {
                    Accion.DEPOSITAR -> vm.depositar(datos.monto)
                    Accion.RETIRAR -> vm.retirar(datos.monto)
                    Accion.TRANSFERIR -> vm.transferir(datos.destino, datos.monto)
                    Accion.PAGAR -> vm.pagarServicio(datos.servicio, datos.contrato, datos.monto)
                }
            }
        )
    }
}

// =====================================================================
//  Cabecera: saldo + indicador del WebSocket
// =====================================================================
@Composable
private fun TarjetaSaldo(
    saldo: Double,
    conexion: EstadoConexion,
    onReconectar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AzulOscuro, Indigo)))
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Mi Billetera",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IndicadorConexion(conexion, onReconectar)
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Saldo actual",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "$ ${dinero(saldo)}",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Cuenta N.° ${Config.CUENTA}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun IndicadorConexion(estado: EstadoConexion, onReconectar: () -> Unit) {
    val color by animateColorAsState(
        targetValue = when (estado) {
            EstadoConexion.CONECTADO -> Color(0xFF22C55E)
            EstadoConexion.CONECTANDO -> Ambar
            EstadoConexion.DESCONECTADO -> Color(0xFFEF4444)
        },
        label = "colorConexion"
    )
    // El punto "late" mientras esta en linea
    val pulso by rememberInfiniteTransition(label = "pulso").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha"
    )
    val texto = when (estado) {
        EstadoConexion.CONECTADO -> "En linea"
        EstadoConexion.CONECTANDO -> "Conectando..."
        EstadoConexion.DESCONECTADO -> "Desconectado"
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.15f),
        onClick = { if (estado == EstadoConexion.DESCONECTADO) onReconectar() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .alpha(if (estado == EstadoConexion.CONECTADO) pulso else 1f)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(texto, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// =====================================================================
//  Panel de acciones (grid 2x2)
// =====================================================================
@Composable
private fun PanelAcciones(
    habilitado: Boolean,
    onAccion: (Accion) -> Unit,
    modifier: Modifier = Modifier
) {
    val filas = Accion.entries.chunked(2)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        filas.forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEach { accion ->
                    BotonAccion(
                        accion = accion,
                        habilitado = habilitado,
                        onClick = { onAccion(accion) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BotonAccion(
    accion: Accion,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorIcono = when (accion) {
        Accion.DEPOSITAR -> VerdeIngreso
        Accion.RETIRAR -> RojoEgreso
        Accion.TRANSFERIR -> Indigo
        Accion.PAGAR -> Ambar
    }
    Card(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.height(92.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colorIcono.copy(alpha = 0.15f))
            ) {
                Icon(accion.icono, contentDescription = accion.titulo, tint = colorIcono)
            }
            Spacer(Modifier.height(6.dp))
            Text(accion.titulo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// =====================================================================
//  Historial reactivo (LazyColumn alimentada por Room)
// =====================================================================
@Composable
private fun Historial(items: List<TransaccionEntity>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Aun no tienes movimientos",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        items(items, key = { it.id }) { tx ->
            FilaTransaccion(tx)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

private val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

@Composable
private fun FilaTransaccion(tx: TransaccionEntity) {
    val color = if (tx.esIngreso) VerdeIngreso else RojoEgreso
    val (titulo, icono) = when (tx.tipo) {
        TipoOperacion.DEPOSITO -> "Deposito" to Icons.Filled.ArrowDownward
        TipoOperacion.RETIRO -> "Retiro" to Icons.Filled.ArrowUpward
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "Transferencia enviada" to Icons.Filled.NorthEast
        TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Transferencia recibida" to Icons.Filled.SouthWest
        TipoOperacion.PAGO_SERVICIO -> "Pago ${tx.servicio?.lowercase() ?: "servicio"}" to iconoServicio(tx.servicio)
        else -> tx.tipo to Icons.Filled.Receipt
    }
    val detalle = when (tx.tipo) {
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "A cuenta ${tx.contraparte}"
        TipoOperacion.TRANSFERENCIA_RECIBIDA -> "De cuenta ${tx.contraparte}"
        TipoOperacion.PAGO_SERVICIO -> "Contrato ${tx.codigoContrato}"
        else -> null
    }
    // Pagos de servicios: icono ambar y etiqueta "Servicio Basico"
    val fondoIcono = if (tx.tipo == TipoOperacion.PAGO_SERVICIO) Ambar else color

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(fondoIcono.copy(alpha = 0.15f))
        ) {
            Icon(icono, contentDescription = titulo, tint = fondoIcono)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titulo.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (tx.tipo == TipoOperacion.PAGO_SERVICIO) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        tx.categoria,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ambar,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ambar.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                listOfNotNull(formatoFecha.format(Date(tx.fecha)), detalle).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            (if (tx.esIngreso) "+ $" else "- $") + dinero(tx.monto),
            color = color,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun iconoServicio(servicio: String?): ImageVector = when (servicio) {
    "LUZ" -> Icons.Filled.Lightbulb
    "AGUA" -> Icons.Filled.WaterDrop
    "INTERNET" -> Icons.Filled.Wifi
    "TELEFONO" -> Icons.Filled.Phone
    else -> Icons.Filled.Receipt
}
