const { app, BrowserWindow, ipcMain, dialog, shell, Notification, Menu, Tray, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');
const https = require('https');
const crypto = require('crypto');

const GITHUB_OWNER = 'lennymuriuki';
const GITHUB_REPO = 'decan-movir';
const CURRENT_VERSION = app.getVersion();
const USER_DATA = app.getPath('userData');
const SETTINGS_FILE = path.join(USER_DATA, 'settings.json');
const HISTORY_FILE = path.join(USER_DATA, 'history.json');
const WATCHLIST_FILE = path.join(USER_DATA, 'watchlist.json');
let mainWindow = null;
let tray = null;
let quitting = false;

function readJson(file, fallback) {
  try { return JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return fallback; }
}
function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2), 'utf8');
}
function settings() {
  return readJson(SETTINGS_FILE, { downloadDir: path.join(app.getPath('downloads'), 'DecanMovie'), autoCheckUpdates: true, minimizeToTray: false });
}
function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1440, height: 900, minWidth: 1050, minHeight: 680,
    backgroundColor: '#09090b',
    show: false,
    title: 'Decan Movie',
    icon: path.join(__dirname, '../assets/decan-movie.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, nodeIntegration: false }
  });
  mainWindow.loadFile(path.join(__dirname, 'index.html'));
  mainWindow.once('ready-to-show', () => mainWindow.show());
  mainWindow.on('close', (event) => {
    if (!quitting && settings().minimizeToTray && tray) { event.preventDefault(); mainWindow.hide(); }
  });
  mainWindow.on('closed', () => { mainWindow = null; });
}
function setupTray() {
  const icon = nativeImage.createFromPath(path.join(__dirname, '../assets/decan-movie.png'));
  tray = new Tray(icon);
  tray.setToolTip('Decan Movie');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open Decan Movie', click: () => { mainWindow?.show(); mainWindow?.focus(); } },
    { type: 'separator' },
    { label: 'Quit', click: () => { quitting = true; app.quit(); } }
  ]));
  tray.on('double-click', () => { mainWindow?.show(); mainWindow?.focus(); });
}
function requestJson(url) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers: { 'User-Agent': 'DecanMovie-Windows', 'Accept': 'application/vnd.github+json' } }, res => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', c => body += c);
      res.on('end', () => {
        try { resolve({ status: res.statusCode, data: JSON.parse(body) }); } catch { reject(new Error(`Invalid server response (${res.statusCode})`)); }
      });
    });
    req.on('error', reject);
    req.setTimeout(15000, () => req.destroy(new Error('Request timed out')));
  });
}
function downloadFile(url, destination, expectedSha256, progressCb) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(destination), { recursive: true });
    const temp = `${destination}.part`;
    const file = fs.createWriteStream(temp);
    const hash = crypto.createHash('sha256');
    const request = https.get(url, { headers: { 'User-Agent': 'DecanMovie-Windows' } }, res => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        file.close(); fs.rmSync(temp, { force: true });
        return downloadFile(res.headers.location, destination, expectedSha256, progressCb).then(resolve, reject);
      }
      if (res.statusCode !== 200) { file.close(); fs.rmSync(temp, { force: true }); return reject(new Error(`Download failed (${res.statusCode})`)); }
      const total = Number(res.headers['content-length'] || 0); let received = 0;
      res.on('data', chunk => { received += chunk.length; hash.update(chunk); progressCb?.(total ? received / total : 0, received, total); });
      res.pipe(file);
      file.on('finish', () => file.close(() => {
        const digest = hash.digest('hex');
        if (expectedSha256 && digest.toLowerCase() !== expectedSha256.toLowerCase()) { fs.rmSync(temp, { force: true }); return reject(new Error('SHA-256 checksum verification failed.')); }
        fs.renameSync(temp, destination); resolve({ destination, sha256: digest });
      }));
    });
    request.on('error', err => { file.close(); fs.rmSync(temp, { force: true }); reject(err); });
  });
}
function isWindowsAsset(asset) {
  const n = String(asset?.name || '').toLowerCase();
  return n.endsWith('.exe') || n.endsWith('.zip') || n.endsWith('.yml') || n.includes('windows');
}
function findWindowsAssets(release) {
  return (release.assets || []).filter(isWindowsAsset).filter(a => !String(a.name).toLowerCase().includes('.apk'));
}

ipcMain.handle('window:minimize', () => mainWindow?.minimize());
ipcMain.handle('window:toggleMaximize', () => { if (!mainWindow) return false; if (mainWindow.isMaximized()) mainWindow.unmaximize(); else mainWindow.maximize(); return mainWindow.isMaximized(); });
ipcMain.handle('window:close', () => mainWindow?.close());
ipcMain.handle('app:info', () => ({ version: CURRENT_VERSION, userData: USER_DATA, platform: process.platform, arch: process.arch }));
ipcMain.handle('settings:get', () => settings());
ipcMain.handle('settings:save', (_, next) => { const merged = { ...settings(), ...next }; writeJson(SETTINGS_FILE, merged); return merged; });
ipcMain.handle('dialog:choose-directory', async () => {
  const result = await dialog.showOpenDialog(mainWindow, { properties: ['openDirectory', 'createDirectory'] });
  return result.canceled ? null : result.filePaths[0];
});
ipcMain.handle('shell:open', (_, target) => shell.openExternal(String(target)));
ipcMain.handle('notification:show', (_, title, body) => { if (Notification.isSupported()) new Notification({ title, body }).show(); });
ipcMain.handle('history:get', () => readJson(HISTORY_FILE, []));
ipcMain.handle('history:save', (_, items) => { writeJson(HISTORY_FILE, Array.isArray(items) ? items.slice(0, 300) : []); return true; });
ipcMain.handle('watchlist:get', () => readJson(WATCHLIST_FILE, []));
ipcMain.handle('watchlist:save', (_, items) => { writeJson(WATCHLIST_FILE, Array.isArray(items) ? items : []); return true; });
ipcMain.handle('updates:latest', async () => {
  const result = await requestJson(`https://api.github.com/repos/${GITHUB_OWNER}/${GITHUB_REPO}/releases/latest`);
  if (result.status !== 200) throw new Error(`GitHub returned ${result.status}`);
  const release = result.data;
  const assets = findWindowsAssets(release);
  return { tagName: release.tag_name, name: release.name, body: release.body || '', publishedAt: release.published_at, htmlUrl: release.html_url, assets, hasWindowsBuild: assets.some(a => /\.exe$/i.test(a.name)) };
});
ipcMain.handle('updates:download', async (_, asset, destination) => {
  if (!asset?.browser_download_url || !isWindowsAsset(asset)) throw new Error('Invalid Windows release asset.');
  if (/\.apk$/i.test(asset.name)) throw new Error('Android APKs cannot be installed as Windows updates.');
  const dest = path.resolve(destination);
  if (!dest.toLowerCase().endsWith('.exe') && !dest.toLowerCase().endsWith('.zip')) throw new Error('Choose a Windows installer or portable ZIP destination.');
  return await downloadFile(asset.browser_download_url, dest, asset.sha256 || null, (fraction, received, total) => mainWindow?.webContents.send('updates:progress', { fraction, received, total, name: asset.name }));
});
ipcMain.handle('updates:open-installer', (_, file) => shell.openPath(path.resolve(file)));

app.whenReady().then(() => { createWindow(); setupTray(); app.on('activate', () => mainWindow?.show()); });
app.on('window-all-closed', () => { if (process.platform !== 'darwin' && !tray) app.quit(); });
app.on('before-quit', () => { quitting = true; });
