// =====================================================
//  Backend Billetera Movil Distribuida
//  REST (Express) + WebSocket (ws) en el mismo puerto
// =====================================================
const express = require("express");
const http = require("http");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");

const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, "data.json");

// ---------- "Base de datos" del servidor (archivo JSON) ----------
const DATOS_INICIALES = {
  cuentas: {
    "1001": { titular: "Grupo 3", saldo: 100.0 },
    "1002": { titular: "Pepito", saldo: 500.0 },
    "1003": { titular: "Federica", saldo: 250.0 },
  },
  transacciones: [],
};

function cargarDatos() {
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, "utf8"));
  } catch {
    return JSON.parse(JSON.stringify(DATOS_INICIALES));
  }
}
let db = cargarDatos();
function guardarDatos() {
  fs.writeFileSync(DATA_FILE, JSON.stringify(db, null, 2));
}

// ---------- Utilidades ----------
const redondear = (n) => Math.round(n * 100) / 100;
let contador = db.transacciones.length;
const nuevoId = () => `TX-${Date.now()}-${++contador}`;

function validarMonto(monto) {
  const m = Number(monto);
  if (!Number.isFinite(m) || m <= 0) return null;
  return redondear(m);
}

function error(res, status, codigo, mensaje) {
  return res.status(status).json({ ok: false, codigo, mensaje });
}

function registrar(tx) {
  db.transacciones.push(tx);
  guardarDatos();
  console.log(`[TX] ${tx.tipo} ${tx.monto} cuenta=${tx.cuenta}`);
  return tx;
}

// ---------- WebSocket: clientes conectados por cuenta ----------
// Map<numeroCuenta, Set<WebSocket>>
const clientes = new Map();

function notificar(cuenta, mensaje) {
  const sockets = clientes.get(cuenta);
  if (!sockets) return 0;
  const json = JSON.stringify(mensaje);
  let enviados = 0;
  for (const ws of sockets) {
    if (ws.readyState === ws.OPEN) {
      ws.send(json);
      enviados++;
    }
  }
  console.log(`[WS] -> cuenta ${cuenta}: ${mensaje.evento} (${enviados} cliente/s)`);
  return enviados;
}

// ---------- API REST ----------
const app = express();
app.use(express.json());

// Log de cada peticion
app.use((req, _res, next) => {
  console.log(`[HTTP] ${req.method} ${req.url}`, req.body && Object.keys(req.body).length ? req.body : "");
  next();
});

app.get("/", (_req, res) => res.json({ ok: true, servicio: "Billetera Backend", ws: "/ws?cuenta=1001" }));

// Consultar saldo
app.get("/api/cuentas/:id", (req, res) => {
  const c = db.cuentas[req.params.id];
  if (!c) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta no existe");
  res.json({ ok: true, cuenta: req.params.id, titular: c.titular, saldo: c.saldo });
});

// Historial de una cuenta (para pruebas)
app.get("/api/cuentas/:id/transacciones", (req, res) => {
  const id = req.params.id;
  res.json(db.transacciones.filter((t) => t.cuenta === id));
});

// 1) Deposito
app.post("/api/cuentas/:id/deposito", (req, res) => {
  const id = req.params.id;
  const c = db.cuentas[id];
  if (!c) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta no existe");
  const monto = validarMonto(req.body.monto);
  if (!monto) return error(res, 400, "MONTO_INVALIDO", "El monto debe ser mayor a 0");

  c.saldo = redondear(c.saldo + monto);
  const tx = registrar({
    id: nuevoId(), tipo: "DEPOSITO", cuenta: id, monto,
    saldo: c.saldo, fecha: new Date().toISOString(),
  });
  res.status(201).json({ ok: true, transaccion: tx });
});

// 2) Retiro
app.post("/api/cuentas/:id/retiro", (req, res) => {
  const id = req.params.id;
  const c = db.cuentas[id];
  if (!c) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta no existe");
  const monto = validarMonto(req.body.monto);
  if (!monto) return error(res, 400, "MONTO_INVALIDO", "El monto debe ser mayor a 0");
  if (c.saldo < monto) return error(res, 422, "SALDO_INSUFICIENTE", `Saldo insuficiente. Disponible: ${c.saldo}`);

  c.saldo = redondear(c.saldo - monto);
  const tx = registrar({
    id: nuevoId(), tipo: "RETIRO", cuenta: id, monto,
    saldo: c.saldo, fecha: new Date().toISOString(),
  });
  res.status(201).json({ ok: true, transaccion: tx });
});

// 3) Transferencia P2P (REST para el emisor + WebSocket para el receptor)
app.post("/api/transferencias", (req, res) => {
  const origen = String(req.body.origen ?? "");
  const destino = String(req.body.destino ?? "");
  const co = db.cuentas[origen];
  const cd = db.cuentas[destino];
  if (!co) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta de origen no existe");
  if (!cd) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta de destino no existe");
  if (origen === destino) return error(res, 400, "MISMA_CUENTA", "No puedes transferirte a ti mismo");
  const monto = validarMonto(req.body.monto);
  if (!monto) return error(res, 400, "MONTO_INVALIDO", "El monto debe ser mayor a 0");
  if (co.saldo < monto) return error(res, 422, "SALDO_INSUFICIENTE", `Saldo insuficiente. Disponible: ${co.saldo}`);

  co.saldo = redondear(co.saldo - monto);
  cd.saldo = redondear(cd.saldo + monto);
  const fecha = new Date().toISOString();

  const txEnviada = registrar({
    id: nuevoId(), tipo: "TRANSFERENCIA_ENVIADA", cuenta: origen, contraparte: destino,
    monto, saldo: co.saldo, fecha,
  });
  const txRecibida = registrar({
    id: nuevoId(), tipo: "TRANSFERENCIA_RECIBIDA", cuenta: destino, contraparte: origen,
    monto, saldo: cd.saldo, fecha,
  });

  // Empuje en tiempo real al receptor
  notificar(destino, { evento: "TRANSFERENCIA_RECIBIDA", transaccion: txRecibida });

  res.status(201).json({ ok: true, transaccion: txEnviada });
});

// 4) Pago de servicios basicos
const SERVICIOS = ["LUZ", "AGUA", "INTERNET", "TELEFONO"];
app.post("/api/servicios/pagar", (req, res) => {
  const cuenta = String(req.body.cuenta ?? "");
  const c = db.cuentas[cuenta];
  if (!c) return error(res, 404, "CUENTA_NO_EXISTE", "La cuenta no existe");
  const servicio = String(req.body.servicio ?? "").toUpperCase();
  if (!SERVICIOS.includes(servicio)) return error(res, 400, "SERVICIO_INVALIDO", `Servicio debe ser: ${SERVICIOS.join(", ")}`);
  const contrato = String(req.body.codigoContrato ?? "").trim();
  if (contrato.length < 4) return error(res, 400, "CONTRATO_INVALIDO", "Codigo de contrato invalido (min. 4 caracteres)");
  const monto = validarMonto(req.body.monto);
  if (!monto) return error(res, 400, "MONTO_INVALIDO", "El monto debe ser mayor a 0");
  if (c.saldo < monto) return error(res, 422, "SALDO_INSUFICIENTE", `Saldo insuficiente. Disponible: ${c.saldo}`);

  c.saldo = redondear(c.saldo - monto);
  const tx = registrar({
    id: nuevoId(), tipo: "PAGO_SERVICIO", cuenta, servicio, codigoContrato: contrato,
    monto, saldo: c.saldo, fecha: new Date().toISOString(),
  });
  res.status(201).json({ ok: true, transaccion: tx });
});

// Reiniciar datos (util antes de la defensa)
app.post("/api/admin/reset", (_req, res) => {
  db = JSON.parse(JSON.stringify(DATOS_INICIALES));
  guardarDatos();
  res.json({ ok: true, mensaje: "Datos reiniciados" });
});

// ---------- Servidor HTTP + WebSocket ----------
const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: "/ws" });

wss.on("connection", (ws, req) => {
  const url = new URL(req.url, "http://localhost");
  const cuenta = url.searchParams.get("cuenta");
  if (!cuenta || !db.cuentas[cuenta]) {
    ws.send(JSON.stringify({ evento: "ERROR", mensaje: "Cuenta invalida" }));
    return ws.close(4001, "Cuenta invalida");
  }
  if (!clientes.has(cuenta)) clientes.set(cuenta, new Set());
  clientes.get(cuenta).add(ws);
  ws.isAlive = true;
  console.log(`[WS] conectado cuenta ${cuenta} (total: ${clientes.get(cuenta).size})`);

  ws.send(JSON.stringify({ evento: "CONECTADO", cuenta, saldo: db.cuentas[cuenta].saldo }));

  ws.on("pong", () => (ws.isAlive = true));
  ws.on("close", () => {
    clientes.get(cuenta)?.delete(ws);
    console.log(`[WS] desconectado cuenta ${cuenta}`);
  });
});

// Heartbeat: mantiene el tunel abierto y limpia conexiones muertas
setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) return ws.terminate();
    ws.isAlive = false;
    ws.ping();
  });
}, 25000);

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Backend escuchando en http://0.0.0.0:${PORT}`);
  console.log(`WebSocket en ws://0.0.0.0:${PORT}/ws?cuenta=1001`);
});
