'use strict';

const express = require('express');
const { spawn } = require('node:child_process');
const path = require('node:path');

const app = express();
const HOST = '127.0.0.1';
const PORT = Number(process.env.PORT || 3000);
const ADB = process.env.ADB_PATH || 'adb';
const MAX_OUTPUT = 64 * 1024;
const appRoot = __dirname;

app.disable('x-powered-by');
app.use(express.json({ limit: '8kb' }));
app.use(express.static(path.join(appRoot, 'public'), { index: 'index.html' }));

function parseTarget(body) {
  const ip = String(body?.ip || '').trim();
  const port = Number(body?.port || 5555);
  const validIp = /^(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)$/.test(ip);
  if (!validIp) throw new Error('Introduce una dirección IPv4 válida.');
  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('Puerto ADB inválido.');
  return { ip, port, serial: `${ip}:${port}` };
}

function runAdb(args, timeoutMs = 12000) {
  return new Promise((resolve) => {
    const child = spawn(ADB, args, { shell: false, windowsHide: true });
    let stdout = '';
    let stderr = '';
    let settled = false;
    const finish = (result) => {
      if (settled) return;
      settled = true;
      resolve(result);
    };
    const timer = setTimeout(() => {
      child.kill('SIGKILL');
      finish({ success: false, code: null, stdout, stderr: 'Tiempo de espera agotado.' });
    }, timeoutMs);
    child.stdout.on('data', (chunk) => { stdout = (stdout + chunk.toString()).slice(-MAX_OUTPUT); });
    child.stderr.on('data', (chunk) => { stderr = (stderr + chunk.toString()).slice(-MAX_OUTPUT); });
    child.on('error', (error) => {
      clearTimeout(timer);
      finish({ success: false, code: null, stdout, stderr: error.message });
    });
    child.on('close', (code) => {
      clearTimeout(timer);
      finish({ success: code === 0, code, stdout: stdout.trim(), stderr: stderr.trim() });
    });
  });
}

async function deviceCommand(target, command, timeoutMs) {
  return runAdb(['-s', target.serial, 'shell', ...command], timeoutMs);
}

function parseMeminfo(output) {
  const match = output.match(/MemAvailable:\s+(\d+)\s+kB/i) || output.match(/MemFree:\s+(\d+)\s+kB/i);
  return match ? Math.round(Number(match[1]) / 1024) : null;
}

app.get('/api/health', (_req, res) => res.json({ ok: true, host: HOST, port: PORT }));

app.post('/api/connect', async (req, res) => {
  let target;
  try { target = parseTarget(req.body); } catch (error) { return res.status(400).json({ error: error.message }); }
  const result = await runAdb(['connect', target.serial], 15000);
  const text = `${result.stdout}\n${result.stderr}`.trim();
  const status = /connected to|already connected/i.test(text) ? 'connected'
    : /unauthorized/i.test(text) ? 'unauthorized' : 'error';
  res.status(status === 'error' ? 502 : 200).json({ status, message: text || 'ADB no devolvió información.', raw: result });
});

app.post('/api/diagnose', async (req, res) => {
  let target;
  try { target = parseTarget(req.body); } catch (error) { return res.status(400).json({ error: error.message }); }
  const checks = [
    ['Memoria', ['cat', '/proc/meminfo']],
    ['Procesos principales', ['top', '-b', '-n', '1']],
    ['Almacenamiento', ['df', '-h', '/data']],
    ['Versión Android', ['getprop', 'ro.build.version.release']],
    ['Fabricante / modelo', ['getprop', 'ro.product.manufacturer']],
    ['Modelo', ['getprop', 'ro.product.model']],
    ['Paquetes de terceros', ['pm', 'list', 'packages', '-3']]
  ];
  const results = [];
  for (const [name, command] of checks) {
    const result = await deviceCommand(target, command, 15000);
    results.push({ name, ...result });
  }
  const mem = results.find(x => x.name === 'Memoria');
  res.json({
    status: 'completed',
    target: target.serial,
    availableRamMb: mem?.success ? parseMeminfo(mem.stdout) : null,
    checks: results
  });
});

app.post('/api/apply-safe-profile', async (req, res) => {
  let target;
  try { target = parseTarget(req.body); } catch (error) { return res.status(400).json({ error: error.message }); }

  const keys = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
  const previous = {};
  for (const key of keys) {
    const read = await deviceCommand(target, ['settings', 'get', 'global', key]);
    if (!read.success) return res.status(502).json({ error: `No se pudo leer ${key}; no se aplicaron cambios.`, details: read });
    previous[key] = read.stdout.trim() === 'null' ? '1' : read.stdout.trim();
  }

  const applied = [];
  for (const key of keys) {
    const result = await deviceCommand(target, ['settings', 'put', 'global', key, '0.5']);
    applied.push({ key, ...result });
    if (!result.success) {
      for (const restoreKey of keys) {
        if (Object.hasOwn(previous, restoreKey)) {
          await deviceCommand(target, ['settings', 'put', 'global', restoreKey, previous[restoreKey]]);
        }
      }
      return res.status(502).json({ error: `Falló ${key}; se intentó restaurar el estado anterior.`, previous, applied });
    }
  }
  res.json({ status: 'applied', message: 'Perfil reversible aplicado: animaciones a 0.5x.', previous, applied });
});

app.post('/api/restore-profile', async (req, res) => {
  let target;
  try { target = parseTarget(req.body); } catch (error) { return res.status(400).json({ error: error.message }); }
  const previous = req.body?.previous;
  const keys = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'];
  if (!previous || !keys.every(key => typeof previous[key] === 'string' && /^[0-9.]+$/.test(previous[key]))) {
    return res.status(400).json({ error: 'No se recibieron los valores anteriores válidos.' });
  }
  const results = [];
  for (const key of keys) results.push({ key, ...(await deviceCommand(target, ['settings', 'put', 'global', key, previous[key]])) });
  const failed = results.some(item => !item.success);
  res.status(failed ? 502 : 200).json({ status: failed ? 'partial' : 'restored', results });
});

app.post('/api/remote-key', async (req, res) => {
  let target;
  try { target = parseTarget(req.body); } catch (error) { return res.status(400).json({ error: error.message }); }
  const keycode = Number(req.body?.keycode);
  const allowed = new Set([3, 4, 19, 20, 21, 22, 23, 24, 25, KEYCODE_BACK]);
  if (!Number.isInteger(keycode) || !allowed.has(keycode)) return res.status(400).json({ error: 'Tecla no permitida.' });
  const result = await deviceCommand(target, ['input', 'keyevent', String(keycode)]);
  res.status(result.success ? 200 : 502).json(result);
});

app.listen(PORT, HOST, () => {
  console.log(`Liamliz TV Hack disponible en http://${HOST}:${PORT}`);
});

const KEYCODE_BACK = 4;
