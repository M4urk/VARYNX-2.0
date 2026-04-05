// VARYNX 2.0 — Control Center UI
// Fully ES5-safe for JavaFX WebView compatibility

var ws = null;
var currentScreen = 'dashboard';
var reqId = 0;

// ── Global error handler — prevents JS exceptions from killing WebView tabs ──

window.onerror = function(msg, url, line, col, err) {
    try {
        var detail = msg + ' at ' + (url || 'unknown') + ':' + line + ':' + col;
        console.error('[VARYNX] Uncaught error: ' + detail);
        showToast('UI error — refreshing data', 'error');
    } catch (_) {}
    return true; // suppress default browser error handling
};

/** Safe wrapper — calls fn inside try/catch so one tab crash doesn't cascade. */
function safeCall(name, fn) {
    try { fn(); } catch (e) {
        console.error('[VARYNX] ' + name + ' failed: ' + (e.message || e));
    }
}

// ── WebSocket ──

function connect() {
    // Close any existing socket before reconnecting
    if (ws) { try { ws.close(); } catch(_){} ws = null; }
    var token = window.__VARYNX_TOKEN || '';
    var wsUrl = 'ws://' + location.host + '/ws';
    if (token) wsUrl += '?token=' + encodeURIComponent(token);
    ws = new WebSocket(wsUrl);

    ws.onopen = function() {
        document.getElementById('ws-status').classList.add('connected');
        send({ type: 'GetDashboard' });
        send({ type: 'GetIdentity' });
        send({ type: 'GetDevices' });
        send({ type: 'GetMeshStatus' });
        send({ type: 'GetModules' });
        send({ type: 'GetNetwork' });
        send({ type: 'GetHealth' });
        send({ type: 'GetSettings' });
        send({ type: 'GetThreats', limit: 50 });
        send({ type: 'GetTopology' });
        send({ type: 'GetDeviceRoles' });
    };

    ws.onmessage = function(e) {
        try {
            var env = JSON.parse(e.data);
            if (env.response) onResponse(env.response);
            if (env.event) onEvent(env.event);
        } catch (_) {}
    };

    ws.onclose = function() {
        document.getElementById('ws-status').classList.remove('connected');
        setTimeout(connect, 3000);
    };

    ws.onerror = function() { ws.close(); };
}

function send(request) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return;
    ws.send(JSON.stringify({ id: String(++reqId), request: request }));
}

// ── Response dispatch ──

function onResponse(r) {
    switch (r.type) {
        case 'Dashboard': safeCall('updateDashboard', function() { updateDashboard(r.data); }); break;
        case 'Identity': safeCall('updateIdentity', function() { updateIdentity(r.data); }); break;
        case 'Devices': safeCall('updateDevices', function() { updateDevices(r.trusted, r.discovered); }); break;
        case 'MeshStatus': safeCall('updateMesh', function() { updateMesh(r.data); }); break;
        case 'Network': safeCall('updateNetwork', function() { updateNetwork(r.data); }); break;
        case 'Threats': safeCall('updateThreats', function() { updateThreats(r.events); }); break;
        case 'Health': safeCall('updateHealth', function() { updateHealth(r.data); }); break;
        case 'Settings': safeCall('updateSettings', function() { updateSettings(r.data); }); break;
        case 'Modules': safeCall('updateModules', function() { updateModules(r.modules); }); break;
        case 'PairingStarted': safeCall('showPairing', function() { showPairing(r.code); }); break;
        case 'Topology': safeCall('updateTopology', function() { updateTopology(r.data); }); break;
        case 'DeviceRoles': safeCall('updateRoles', function() { updateRoles(r.roles); }); break;
        case 'ActionResult': showToast(r.message, r.success ? 'success' : 'error'); break;
        case 'SecurityScanResult': safeCall('updateSecurityScan', function() { updateSecurityScan(r.data); }); break;
        case 'SkimmerScanResult': safeCall('updateSkimmerScan', function() { updateSkimmerScan(r.data); }); break;
        case 'QrScanResult': safeCall('updateQrScan', function() { updateQrScan(r.data); }); break;
        case 'Error': showToast(r.message || 'An error occurred', 'error'); break;
    }
}

// ── Event dispatch ──

function onEvent(ev) {
    switch (ev.type) {
        case 'DashboardUpdated': safeCall('updateDashboard', function() { updateDashboard(ev.data); }); break;
        case 'MeshUpdated': safeCall('updateMesh', function() { updateMesh(ev.data); }); break;
        case 'ThreatCreated': safeCall('prependThreat', function() { prependThreat(ev.event); }); break;
        case 'HealthUpdated': safeCall('updateHealth', function() { updateHealth(ev.data); }); break;
        case 'DevicesUpdated': safeCall('updateDevices', function() { updateDevices(ev.trusted, ev.discovered); }); break;
        case 'PairingCode': safeCall('showPairing', function() { showPairing(ev.code); }); break;
        case 'PairingComplete': closePairing(); showToast('Device paired successfully: ' + ev.deviceName, 'success'); break;
        case 'PairingFailed': closePairing(); showToast('Pairing failed: ' + ev.reason, 'error'); break;
        case 'ModuleChanged': send({ type: 'GetModules' }); break;
        case 'Log': safeCall('prependLog', function() { prependLog(ev.entry); }); break;
        case 'LockdownStateChanged': safeCall('updateLockdown', function() { updateLockdown(ev.active, ev.initiator); }); break;
        case 'TopologyUpdated': safeCall('updateTopology', function() { updateTopology(ev.data); }); break;
    }
}

// ── UI updates ──

function updateDashboard(d) {
    setText('dashboard-threat-level', d.threatLevel.toUpperCase());
    setText('dashboard-mode', d.guardianMode + ' Mode');
    setText('dash-modules', d.activeModules + '/' + d.totalModules);
    setText('dash-peers', d.meshPeers);
    setText('dash-cycles', d.cycleCount);
    setText('dash-uptime', fmtUp(d.uptime));
    setMesh(d.meshPeers > 0, d.syncStatus);
    setAlerts(d.alertCount);
    var modeEl = document.getElementById('status-mode');
    if (modeEl) modeEl.querySelector('span').textContent = 'Mode: ' + d.guardianMode;
    var consBadge = document.getElementById('dash-consensus');
    if (consBadge) { consBadge.textContent = d.consensusThreatLevel || 'NONE'; consBadge.className = 'threat-badge ' + (d.consensusThreatLevel || 'none').toLowerCase(); }
    setText('dash-leader', d.meshLeader ? d.meshLeader.substring(0, 12) + '\u2026' : '\u2014');
    setText('dash-quorum', d.quorumThreatConfirmed ? 'CONFIRMED' : '\u2014');
    updateLockdown(d.lockdownActive, d.lockdownInitiator);
    // Feed About diagnostics
    updateAboutDiagnostics({
        activeModuleCount: d.activeModules,
        totalModuleCount: d.totalModules,
        meshPeerCount: d.meshPeers,
        cycleCount: d.cycleCount
    }, null);
}

function updateIdentity(d) {
    setText('id-device-id', d.deviceId);
    setText('id-display-name', d.displayName);
    setText('id-role', d.role);
    setText('id-capabilities', d.capabilities.join(', '));
    setText('id-created', new Date(d.createdAt).toLocaleString());
    setText('id-key-exchange', d.publicKeyExchange);
    setText('id-key-signing', d.publicKeySigning);
    setText('role-name', d.role);
    setText('status-id', d.displayName);
}

function updateDevices(trusted, discovered) {
    setText('trusted-count', trusted.length);
    setText('discovered-count', discovered.length);
    var t = document.getElementById('trusted-devices-list');
    if (trusted.length === 0) {
        t.innerHTML = '<p class="empty-state">No trusted peers \u2014 pair a device to add</p>';
    } else {
        var html = '';
        for (var i = 0; i < trusted.length; i++) html += deviceHtml(trusted[i], true);
        t.innerHTML = html;
    }
    var disc = document.getElementById('discovered-devices-list');
    if (discovered.length === 0) {
        disc.innerHTML = '<p class="empty-state">Scanning LAN\u2026</p>';
    } else {
        var html2 = '';
        for (var j = 0; j < discovered.length; j++) html2 += deviceHtml(discovered[j], false);
        disc.innerHTML = html2;
    }
}

function updateMesh(d) {
    setText('mesh-active', d.isActive ? 'Active' : 'Inactive');
    setText('mesh-local-id', d.localDeviceId.substring(0, 12) + '\u2026');
    setText('mesh-role', d.role);
    setText('mesh-trusted', d.trustedPeerCount);
    setText('mesh-discovered', d.discoveredPeerCount);
    setText('mesh-heartbeat', d.lastHeartbeat > 0 ? new Date(d.lastHeartbeat).toLocaleTimeString() : '\u2014');
    setText('mesh-sync-status', d.syncStatus);
    var vc = document.getElementById('vector-clock-display');
    var keys = [];
    var clock = d.vectorClock || {};
    for (var k in clock) {
        if (clock.hasOwnProperty(k)) keys.push(k);
    }
    if (keys.length === 0) {
        vc.innerHTML = '<p class="empty-state">No clock entries</p>';
    } else {
        var parts = [];
        for (var i = 0; i < keys.length; i++) parts.push(keys[i].substring(0, 8) + '\u2026 \u2192 ' + clock[keys[i]]);
        vc.textContent = parts.join('\n');
    }
    setMesh(d.isActive && d.trustedPeerCount > 0, d.syncStatus);
}

function updateNetwork(d) {
    setText('net-gateway', d.gatewayIp);
    setText('net-dns', d.dnsServer);
    setText('net-public-ip', d.publicIp);
    setText('net-exposure', d.exposureLevel);
    setText('net-connections', d.activeConnections);
    var el = document.getElementById('network-interfaces');
    if (!d.interfaces || d.interfaces.length === 0) {
        el.innerHTML = '<p class="empty-state">No interfaces</p>';
    } else {
        var rows = '';
        for (var i = 0; i < d.interfaces.length; i++) {
            var iface = d.interfaces[i];
            rows += '<tr><td>' + esc(iface.name) + '</td><td class="mono">' + esc(iface.ip) + '</td><td class="mono">' + esc(iface.mac) + '</td><td>' + esc(iface.type) + '</td><td><span class="if-dot ' + (iface.isUp ? 'up' : 'down') + '"></span>' + (iface.isUp ? 'Up' : 'Down') + '</td></tr>';
        }
        el.innerHTML = '<table class="if-table"><thead><tr><th>Name</th><th>IP</th><th>MAC</th><th>Type</th><th>Status</th></tr></thead><tbody>' + rows + '</tbody></table>';
    }
    var ports = document.getElementById('open-ports-list');
    if (!d.openPorts || d.openPorts.length === 0) {
        ports.innerHTML = '<p class="empty-state">None detected</p>';
    } else {
        var portHtml = '';
        for (var j = 0; j < d.openPorts.length; j++) {
            var p = d.openPorts[j];
            portHtml += '<div class="detail-row"><span>' + p.port + '/' + p.protocol + '</span><span>' + esc(p.process) + ' (' + p.state + ')</span></div>';
        }
        ports.innerHTML = portHtml;
    }
}

function updateThreats(events) {
    var el = document.getElementById('threat-events-list');
    if (!events || events.length === 0) {
        el.innerHTML = '<p class="empty-state">All clear \u2014 no threats</p>';
    } else {
        var html = '';
        for (var i = 0; i < events.length; i++) html += eventHtml(events[i]);
        el.innerHTML = html;
    }
    var recent = document.getElementById('recent-events-list');
    if (!events || events.length === 0) {
        recent.innerHTML = '<p class="empty-state">No threats detected</p>';
    } else {
        var rHtml = '';
        var limit = Math.min(events.length, 10);
        for (var j = 0; j < limit; j++) rHtml += eventHtml(events[j]);
        recent.innerHTML = rHtml;
    }
    renderTimeline(events);
}

function prependThreat(evt) {
    var el = document.getElementById('threat-events-list');
    var empty = el.querySelector('.empty-state');
    if (empty) empty.remove();
    el.insertAdjacentHTML('afterbegin', eventHtml(evt));
}

function updateHealth(d) {
    setText('health-uptime', fmtUp(d.serviceUptime));
    setText('health-cycles', d.guardianCycles);
    setText('health-logs', d.logEntryCount);
    setText('health-heap-used', d.jvmHeapUsedMb + ' MB');
    setText('health-heap-max', d.jvmHeapMaxMb + ' MB');
    var pct = d.jvmHeapMaxMb > 0 ? (d.jvmHeapUsedMb / d.jvmHeapMaxMb * 100) : 0;
    var fill = document.getElementById('heap-fill');
    fill.style.width = pct + '%';
    fill.style.background = pct > 80 ? 'var(--red)' : pct > 60 ? 'var(--amber)' : 'var(--cyan)';
    var eng = document.getElementById('engine-status-list');
    var engHtml = '';
    var status = d.engineStatus || {};
    for (var name in status) {
        if (status.hasOwnProperty(name)) {
            engHtml += '<div class="detail-row"><span>' + esc(name) + '</span><span style="color:var(--cyan)">' + esc(status[name]) + '</span></div>';
        }
    }
    eng.innerHTML = engHtml;
    // Feed About diagnostics
    updateAboutDiagnostics(null, {
        uptimeMs: d.serviceUptime,
        heapUsedMb: d.jvmHeapUsedMb,
        logEntryCount: d.logEntryCount,
        cycleCount: d.guardianCycles
    });
}

function updateSettings(d) {
    setText('set-name', d.deviceName);
    setText('set-role', d.role);
    setText('set-mesh', d.meshEnabled ? 'Enabled' : 'Disabled');
    setText('set-port', d.meshPort);
    setText('set-heartbeat', d.heartbeatIntervalSec + 's');
    setText('set-cycle', d.guardianCycleIntervalSec + 's');
    setText('set-logs', d.logRetentionCount);
    setText('set-autostart', d.autoStartEnabled ? 'Enabled' : 'Disabled');
}

function updateAboutDiagnostics(dashData, healthData) {
    if (dashData) {
        setText('about-diag-modules', (dashData.activeModuleCount || 0) + ' / ' + (dashData.totalModuleCount || 0));
        setText('about-diag-peers', dashData.meshPeerCount || 0);
        setText('about-diag-cycles', dashData.cycleCount || 0);
    }
    if (healthData) {
        setText('about-diag-uptime', fmtUp(healthData.uptimeMs));
        setText('about-diag-heap', healthData.heapUsedMb ? healthData.heapUsedMb + ' MB' : '—');
        setText('about-diag-logs', healthData.logEntryCount || 0);
        setText('about-diag-cycles', healthData.cycleCount || 0);
    }
}

function updateModules(modules) {
    var cats = {};
    var i;
    for (i = 0; i < modules.length; i++) {
        var m = modules[i];
        if (!cats[m.category]) cats[m.category] = { active: 0, locked: 0 };
        if (m.isV2Active) cats[m.category].active++; else cats[m.category].locked++;
    }
    var catHtml = '';
    for (var catName in cats) {
        if (cats.hasOwnProperty(catName)) {
            var c = cats[catName];
            catHtml += '<div class="category-row"><div class="category-name">' + esc(catName) + '</div><div class="category-counts"><span class="badge">' + c.active + ' active</span>' + (c.locked > 0 ? '<span class="badge badge-dim">' + c.locked + ' locked</span>' : '') + '</div></div>';
        }
    }
    document.getElementById('module-category-list').innerHTML = catHtml;

    var modHtml = '';
    for (i = 0; i < modules.length; i++) {
        var mod = modules[i];
        modHtml += '<div class="module-row"><div class="module-info"><div class="module-name">' + esc(mod.name) + '</div><div class="module-desc">' + esc(mod.description) + '</div></div><span class="module-state ' + mod.state.toLowerCase() + '">' + mod.state + '</span></div>';
    }
    document.getElementById('settings-modules-list').innerHTML = modHtml;
}

// ── Navigation ──

function navigateTo(screen) {
    currentScreen = screen;
    var screens = document.querySelectorAll('.screen');
    var navs = document.querySelectorAll('.nav-item');
    var i;
    for (i = 0; i < screens.length; i++) screens[i].classList.remove('active');
    for (i = 0; i < navs.length; i++) navs[i].classList.remove('active');
    var s = document.getElementById('screen-' + screen);
    var n = document.querySelector('.nav-item[data-screen="' + screen + '"]');
    if (s) {
        s.style.animation = 'none';
        s.offsetHeight;
        s.style.animation = '';
        s.classList.add('active');
    }
    if (n) n.classList.add('active');
}

// ── Actions ──

function startPairing() { send({ type: 'StartPairing' }); }
function closePairing() { document.getElementById('pairing-overlay').classList.add('hidden'); }
function showPairing(code) {
    var container = document.getElementById('pairing-code-display');
    container.innerHTML = '';
    var digits = code.split('');
    for (var i = 0; i < digits.length; i++) {
        var div = document.createElement('div');
        div.className = 'pairing-digit';
        div.textContent = digits[i];
        container.appendChild(div);
    }
    document.getElementById('pairing-overlay').classList.remove('hidden');
}
function requestAction(action) { send({ type: 'Action', action: action, params: {} }); }

// ── Toast notifications ──

function showToast(message, type) {
    var container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.style.cssText = 'position:fixed;top:16px;right:16px;z-index:9999;display:flex;flex-direction:column;gap:8px;pointer-events:none';
        document.body.appendChild(container);
    }
    var toast = document.createElement('div');
    var bg = type === 'success' ? 'rgba(76,175,80,0.95)' : 'rgba(239,83,80,0.95)';
    toast.style.cssText = 'padding:10px 18px;border-radius:8px;font-size:13px;font-family:Segoe UI,system-ui,sans-serif;color:#fff;background:' + bg + ';box-shadow:0 4px 16px rgba(0,0,0,0.4);pointer-events:auto;opacity:0;transform:translateX(40px);transition:opacity 0.3s,transform 0.3s';
    toast.textContent = message;
    container.appendChild(toast);
    // Trigger animation
    setTimeout(function() { toast.style.opacity = '1'; toast.style.transform = 'translateX(0)'; }, 10);
    // Auto-dismiss
    setTimeout(function() {
        toast.style.opacity = '0'; toast.style.transform = 'translateX(40px)';
        setTimeout(function() { if (toast.parentNode) toast.parentNode.removeChild(toast); }, 300);
    }, 3000);
}

// ── HTML generators ──

function deviceHtml(p, trusted) {
    var c = tColor(p.threatLevel);
    var tl = esc(p.threatLevel).toLowerCase();
    var badge = trusted
        ? '<span class="threat-badge ' + tl + '">' + esc(p.threatLevel) + '</span>'
        : '<span class="threat-badge" style="background:rgba(30,154,214,0.15);color:var(--deep)">DISCOVERED</span>';
    return '<div class="device-card"><div class="device-icon" style="background:' + c + '22;color:' + c + '">' + rIcon(p.role) + '</div><div class="device-info"><div class="device-name">' + esc(p.displayName) + '</div><div class="device-meta">' + esc(p.role) + ' \u00B7 ' + esc(p.guardianMode) + ' \u00B7 ' + p.activeModuleCount + ' modules</div></div>' + badge + '</div>';
}

function eventHtml(e) {
    var tl = esc(e.threatLevel).toLowerCase();
    return '<div class="event-item"><div class="event-header"><span class="threat-badge ' + tl + '">' + esc(e.threatLevel) + '</span><span class="event-title">' + esc(e.title) + '</span><span class="event-time">' + new Date(e.timestamp).toLocaleTimeString() + '</span></div><div class="event-detail">' + esc(e.description) + '</div></div>';
}

// ── Helpers ──

function setText(id, v) { var el = document.getElementById(id); if (el) el.textContent = String(v); }
function esc(s) { return (s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;'); }
function tColor(l) {
    switch ((l||'').toLowerCase()) {
        case 'clear': case 'none': return '#00E5FF';
        case 'low': return '#00E676';
        case 'medium': return '#FFC857';
        case 'high': return '#FF7043';
        case 'critical': return '#FF4B5C';
        default: return '#00E5FF';
    }
}
function rIcon(r) {
    switch ((r||'').toLowerCase()) {
        case 'controller': return '\uD83D\uDCBB';
        case 'guardian': return '\uD83D\uDCF1';
        case 'guardian_micro': return '\u231A';
        case 'hub_home': return '\uD83C\uDFE0';
        case 'hub_wear': return '\u2328';
        case 'node_satellite': return '\uD83D\uDCE1';
        case 'node_pocket': return '\uD83D\uDCDF';
        case 'node_linux': return '\uD83D\uDDA5';
        default: return '\u25A3';
    }
}
function fmtUp(ms) {
    if (!ms || ms <= 0) return '0s';
    var s = Math.floor(ms/1000);
    if (s < 60) return s+'s';
    var m = Math.floor(s/60);
    if (m < 60) return m+'m '+(s%60)+'s';
    var h = Math.floor(m/60);
    if (h < 24) return h+'h '+(m%60)+'m';
    return Math.floor(h/24)+'d '+(h%24)+'h';
}
function setMesh(on, sync) {
    var el = document.getElementById('status-mesh');
    el.querySelector('.status-dot').className = 'status-dot' + (on ? '' : ' offline');
    el.querySelectorAll('span')[1].textContent = 'Mesh: ' + (on ? 'Online' : 'Local');
    document.getElementById('status-sync').querySelectorAll('span')[1].textContent = 'Sync: ' + (sync || '\u2014');
}
function setAlerts(n) {
    document.getElementById('status-alerts').querySelectorAll('span')[1].textContent = 'Alerts: ' + n;
}

// ── Lockdown ──

function updateLockdown(active, initiator) {
    var banner = document.getElementById('lockdown-banner');
    if (active) {
        banner.classList.remove('hidden');
        setText('lockdown-info', initiator ? 'Initiated by ' + initiator : 'All nodes in defensive posture');
    } else {
        banner.classList.add('hidden');
    }
}

// ── Join Pairing ──

function showJoinPairing() {
    document.getElementById('join-pairing-overlay').classList.remove('hidden');
    var input = document.getElementById('join-code-input');
    input.value = '';
    input.focus();
}

function submitJoinPairing() {
    var code = document.getElementById('join-code-input').value.trim();
    if (code.length !== 6 || !/^\d{6}$/.test(code)) return;
    send({ type: 'SubmitPairingCode', code: code });
    closeJoinPairing();
}

function closeJoinPairing() {
    document.getElementById('join-pairing-overlay').classList.add('hidden');
}

// ── Topology ──

function updateTopology(data) {
    var svg = document.getElementById('topology-svg');
    var w = svg.clientWidth || 800;
    var h = 400;
    svg.innerHTML = '';

    setText('topo-leader', data.meshLeader ? data.meshLeader.substring(0, 12) + '\u2026' : '\u2014');
    var consBadge = document.getElementById('topo-consensus');
    if (consBadge) { consBadge.textContent = data.consensusThreatLevel || 'NONE'; consBadge.className = 'threat-badge ' + (data.consensusThreatLevel || 'none').toLowerCase(); }
    setText('topo-lockdown', data.lockdownActive ? 'ACTIVE' : 'Inactive');
    setText('topo-nodes', data.nodes ? data.nodes.length : 0);
    setText('topo-edges', data.edges ? data.edges.length : 0);

    if (!data || !data.nodes || data.nodes.length === 0) {
        svg.innerHTML = '<text x="50%" y="50%" text-anchor="middle" fill="#3A4466" font-size="13">No topology data</text>';
        return;
    }

    var defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    defs.innerHTML = '<filter id="glow"><feGaussianBlur stdDeviation="3" result="blur"/><feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge></filter>';
    svg.appendChild(defs);

    var cx = w / 2, cy = h / 2;
    var localNode = null;
    var otherNodes = [];
    var ni;
    for (ni = 0; ni < data.nodes.length; ni++) {
        if (data.nodes[ni].isLocal) localNode = data.nodes[ni];
        else otherNodes.push(data.nodes[ni]);
    }
    var radius = Math.min(w, h) * 0.35;
    var positions = {};

    if (localNode) positions[localNode.deviceId] = { x: cx, y: cy };
    for (ni = 0; ni < otherNodes.length; ni++) {
        var angle = (2 * Math.PI * ni) / Math.max(otherNodes.length, 1) - Math.PI / 2;
        positions[otherNodes[ni].deviceId] = { x: cx + radius * Math.cos(angle), y: cy + radius * Math.sin(angle) };
    }

    var edges = data.edges || [];
    for (var ei = 0; ei < edges.length; ei++) {
        var edge = edges[ei];
        var from = positions[edge.fromDeviceId], to = positions[edge.toDeviceId];
        if (!from || !to) continue;
        var line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('x1', from.x); line.setAttribute('y1', from.y);
        line.setAttribute('x2', to.x); line.setAttribute('y2', to.y);
        line.setAttribute('stroke', edge.isTrusted ? '#0088CC' : '#1A1F27');
        line.setAttribute('stroke-width', edge.isTrusted ? '2' : '1');
        if (!edge.isTrusted) line.setAttribute('stroke-dasharray', '4 4');
        if (edge.isTrusted) line.setAttribute('filter', 'url(#glow)');
        svg.appendChild(line);
    }

    for (ni = 0; ni < data.nodes.length; ni++) {
        var nd = data.nodes[ni];
        var pos = positions[nd.deviceId];
        if (!pos) continue;
        var g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        var r = nd.isLocal ? 24 : 18;
        var c = tColor(nd.threatLevel);

        var circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', pos.x); circle.setAttribute('cy', pos.y); circle.setAttribute('r', r);
        circle.setAttribute('fill', '#111119'); circle.setAttribute('stroke', c);
        circle.setAttribute('stroke-width', nd.isLocal ? '3' : '2');
        if (nd.isLocal) circle.setAttribute('filter', 'url(#glow)');
        g.appendChild(circle);

        var icon = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        icon.setAttribute('x', pos.x); icon.setAttribute('y', pos.y + 5);
        icon.setAttribute('text-anchor', 'middle'); icon.setAttribute('fill', c);
        icon.setAttribute('font-size', nd.isLocal ? '16' : '13');
        icon.textContent = rIcon(nd.role);
        g.appendChild(icon);

        var label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        label.setAttribute('x', pos.x); label.setAttribute('y', pos.y + r + 16);
        label.setAttribute('text-anchor', 'middle'); label.setAttribute('fill', '#7088AA');
        label.setAttribute('font-size', '11'); label.setAttribute('font-family', 'Segoe UI, system-ui, sans-serif');
        label.textContent = nd.displayName;
        g.appendChild(label);

        var dot = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        dot.setAttribute('cx', pos.x + r * 0.7); dot.setAttribute('cy', pos.y - r * 0.7);
        dot.setAttribute('r', '4'); dot.setAttribute('fill', nd.isOnline ? '#4CAF50' : '#EF5350');
        g.appendChild(dot);

        // Hover tooltip
        var title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
        title.textContent = nd.displayName + ' (' + nd.role + ') \u2014 ' + nd.threatLevel;
        g.appendChild(title);

        // Hover effect
        (function(group, circ, nodeR) {
            group.style.cursor = 'pointer';
            group.addEventListener('mouseenter', function() {
                circ.setAttribute('r', nodeR + 4);
                circ.setAttribute('stroke-width', '4');
            });
            group.addEventListener('mouseleave', function() {
                circ.setAttribute('r', nodeR);
                circ.setAttribute('stroke-width', nd.isLocal ? '3' : '2');
            });
        })(g, circle, r);

        svg.appendChild(g);
    }
}

// ── Roles ──

function updateRoles(roles) {
    if (!roles || roles.length === 0) return;
    var sorted = roles.slice(0).sort(function(a, b) { return b.weight - a.weight; });
    var colors = ['#4DD8E8', '#1E9AD6', '#5EC4E6', '#0B6EB5'];

    var hierHtml = '';
    for (var i = 0; i < sorted.length; i++) {
        var r = sorted[i];
        var col = colors[i % colors.length];
        hierHtml += '<div class="role-tier"><div class="role-tier-rank" style="background:' + col + '22;color:' + col + '">T' + (sorted.length - i) + '</div>'
            + '<div class="role-tier-icon">' + esc(r.icon) + '</div>'
            + '<div class="role-tier-info"><div class="role-tier-name">' + esc(r.label) + '</div>'
            + '<div class="role-tier-desc">' + esc(r.description) + '</div></div>'
            + '<div class="role-tier-weight">w' + r.weight + '</div></div>';
    }
    document.getElementById('role-hierarchy').innerHTML = hierHtml;

    var detHtml = '';
    for (var j = 0; j < sorted.length; j++) {
        var rd = sorted[j];
        var capsHtml = '';
        for (var k = 0; k < rd.capabilities.length; k++) capsHtml += '<span class="role-cap">' + esc(rd.capabilities[k]) + '</span>';
        detHtml += '<div class="role-detail-card"><h4 class="card-title">' + esc(rd.icon) + ' ' + esc(rd.label) + '</h4>'
            + '<p class="role-detail-desc">' + esc(rd.description) + '</p>'
            + '<div class="role-caps">' + capsHtml + '</div></div>';
    }
    document.getElementById('role-details-list').innerHTML = detHtml;
}

// ── Threat Timeline ──

function renderTimeline(events) {
    var bar = document.getElementById('timeline-bar');
    if (!events || events.length === 0) {
        bar.innerHTML = '<span style="position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);color:var(--text3);font-size:11px">No events in the last hour</span>';
        return;
    }
    var now = Date.now();
    var range = 3600000;
    var html = '';
    // Time markers
    html += '<span style="position:absolute;bottom:1px;left:4px;font-size:9px;color:var(--text3)">-60m</span>';
    html += '<span style="position:absolute;bottom:1px;left:25%;transform:translateX(-50%);font-size:9px;color:var(--text3)">-45m</span>';
    html += '<span style="position:absolute;bottom:1px;left:50%;transform:translateX(-50%);font-size:9px;color:var(--text3)">-30m</span>';
    html += '<span style="position:absolute;bottom:1px;left:75%;transform:translateX(-50%);font-size:9px;color:var(--text3)">-15m</span>';
    html += '<span style="position:absolute;bottom:1px;right:4px;font-size:9px;color:var(--text3)">now</span>';
    for (var i = 0; i < events.length; i++) {
        var ev = events[i];
        if (ev.timestamp < now - range) continue;
        var pct = Math.max(0, Math.min(100, (ev.timestamp - (now - range)) / range * 100));
        html += '<div class="timeline-dot" style="left:' + pct + '%;background:' + tColor(ev.threatLevel) + '" title="' + esc(ev.title) + ' \u2014 ' + new Date(ev.timestamp).toLocaleTimeString() + '"></div>';
    }
    bar.innerHTML = html;
}

// ── Log ──

function prependLog(entry) {
    send({ type: 'GetThreats', limit: 50 });
}

// ── Direct event binding (CSP-safe, no delegation / closest needed) ──

(function bindUI() {
    var navItems = document.querySelectorAll('.nav-item[data-screen]');
    var i;
    for (i = 0; i < navItems.length; i++) {
        (function(btn) {
            btn.addEventListener('click', function() {
                navigateTo(btn.getAttribute('data-screen'));
            });
        })(navItems[i]);
    }

    var actions = document.querySelectorAll('[data-action]');
    for (i = 0; i < actions.length; i++) {
        (function(btn) {
            btn.addEventListener('click', function() {
                var a = btn.getAttribute('data-action');
                if (a === 'nav-settings') navigateTo('settings');
                else if (a === 'start-pairing') startPairing();
                else if (a === 'join-pairing') showJoinPairing();
                else if (a === 'close-pairing') closePairing();
                else if (a === 'submit-join') submitJoinPairing();
                else if (a === 'close-join') closeJoinPairing();
                else if (a === 'force_scan') requestAction('force_scan');
                else if (a === 'clear_logs') requestAction('clear_logs');
                else if (a === 'lockdown') requestAction('lockdown');
                else if (a === 'cancel_lockdown') requestAction('cancel_lockdown');
                else if (a === 'run-security-scan') runSecurityScan();
                else if (a === 'run-skimmer-scan') runSkimmerScan();
            });
        })(actions[i]);
    }

    var joinInput = document.getElementById('join-code-input');
    if (joinInput) {
        joinInput.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') submitJoinPairing();
        });
    }

    // Settings tab switching (CSP-safe — no inline onclick)
    var settingsTabs = document.querySelectorAll('[data-settings-tab]');
    for (i = 0; i < settingsTabs.length; i++) {
        (function(btn) {
            btn.addEventListener('click', function() {
                switchSettingsTab(btn.getAttribute('data-settings-tab'), btn);
            });
        })(settingsTabs[i]);
    }

    // Diagnostics expand/collapse
    var diagBtn = document.getElementById('diag-expand-btn');
    if (diagBtn) {
        diagBtn.addEventListener('click', function() { toggleDiagnostics(diagBtn); });
    }
})();

// ── Scanner Functions ──

function runSecurityScan() {
    setText('security-scan-status', 'Scanning...');
    send({ type: 'RunSecurityScan' });
}

function runSkimmerScan() {
    setText('skimmer-scan-status', 'Scanning...');
    send({ type: 'RunSkimmerScan' });
}

function runQrScan() {
    var input = document.getElementById('qr-input');
    var content = input ? input.value.trim() : '';
    if (!content) { showToast('Enter content to analyze', 'error'); return; }
    setText('qr-scan-status', 'Analyzing...');
    send({ type: 'RunQrScan', content: content });
}

function updateSecurityScan(d) {
    setText('security-scan-status', d.findingCount > 0 ? d.findingCount + ' Finding(s)' : 'All Clear');
    setText('security-scan-count', d.findingCount);
    var levelBadge = document.getElementById('security-scan-level');
    if (levelBadge) {
        levelBadge.textContent = d.overallThreatLevel.toUpperCase();
        levelBadge.className = 'threat-badge ' + d.overallThreatLevel.toLowerCase();
    }
    var el = document.getElementById('security-scan-findings');
    if (!d.findings || d.findings.length === 0) {
        el.innerHTML = '<p class="empty-state">No threats detected \u2014 device is secure</p>';
    } else {
        var html = '';
        for (var i = 0; i < d.findings.length; i++) {
            var f = d.findings[i];
            html += '<div class="event-item"><div class="event-header"><span class="threat-badge ' + f.threatLevel.toLowerCase() + '">' + esc(f.threatLevel) + '</span><span class="event-title">' + esc(f.title) + '</span></div><div class="event-detail">' + esc(f.moduleName) + ': ' + esc(f.description) + '</div></div>';
        }
        el.innerHTML = html;
    }
}

function updateSkimmerScan(d) {
    setText('skimmer-scan-status', d.suspiciousCount > 0 ? d.suspiciousCount + ' Suspicious Device(s)' : 'No Skimmers Found');
    setText('skimmer-scan-count', d.suspiciousCount);
    var levelBadge = document.getElementById('skimmer-scan-level');
    if (levelBadge) {
        levelBadge.textContent = d.overallThreatLevel.toUpperCase();
        levelBadge.className = 'threat-badge ' + d.overallThreatLevel.toLowerCase();
    }
    var el = document.getElementById('skimmer-scan-findings');
    if (!d.findings || d.findings.length === 0) {
        el.innerHTML = '<p class="empty-state">No Bluetooth skimmers detected</p>';
    } else {
        var html = '';
        for (var i = 0; i < d.findings.length; i++) {
            var f = d.findings[i];
            html += '<div class="event-item"><div class="event-header"><span class="threat-badge ' + f.threatLevel.toLowerCase() + '">' + esc(f.threatLevel) + '</span><span class="event-title">' + esc(f.deviceName) + '</span></div><div class="event-detail">RSSI: ' + f.rssi + ' dBm \u2014 ' + esc(f.description) + '</div></div>';
        }
        el.innerHTML = html;
    }
}

function updateQrScan(d) {
    setText('qr-scan-status', d.safe ? 'Safe' : 'Threat Detected');
    setText('qr-payload-type', d.payloadType);
    setText('qr-safe', d.safe ? 'Yes' : 'No');
    var levelBadge = document.getElementById('qr-threat-level');
    if (levelBadge) {
        levelBadge.textContent = d.threatLevel.toUpperCase();
        levelBadge.className = 'threat-badge ' + d.threatLevel.toLowerCase();
    }
    var el = document.getElementById('qr-scan-findings');
    if (!d.findings || d.findings.length === 0) {
        el.innerHTML = '<p class="empty-state">No threats detected \u2014 content appears safe</p>';
    } else {
        var html = '';
        for (var i = 0; i < d.findings.length; i++) {
            html += '<div class="event-item"><div class="event-header"><span class="threat-badge medium">\u26A0</span><span class="event-title">' + esc(d.findings[i]) + '</span></div></div>';
        }
        el.innerHTML = html;
    }
}

// Desktop shell injects window.__VARYNX_TOKEN then calls connect().
// For standalone/browser access without auth, connect immediately.
if (window.__VARYNX_TOKEN) {
    connect();
} else {
    // Fallback: if token appears later (injected by desktop shell), a
    // call to connect() will be made from Java. If running standalone
    // without auth, try after a short delay.
    setTimeout(function() { if (!ws) connect(); }, 1500);
}

// ── Window Chrome (undecorated desktop mode) ──
// Called by VarynxDesktop.kt after injecting window.varynxWindow bridge.

var _wDrag = false, _wResize = false;
var _wDragLX = 0, _wDragLY = 0;
var _wRDir = '';
var _wRSX = 0, _wRSY = 0, _wRB = null;
var _W_EDGE = 6;

function _wIsInteractive(el) {
    while (el && el !== document) {
        var tag = el.tagName;
        if (tag === 'BUTTON' || tag === 'A' || tag === 'INPUT' || tag === 'SELECT') return true;
        if (el.id === 'window-controls') return true;
        el = el.parentNode;
    }
    return false;
}

function _wEdge(e) {
    var w = window.innerWidth, h = window.innerHeight;
    var x = e.clientX, y = e.clientY, d = '';
    if (y < _W_EDGE) d += 'n'; else if (y > h - _W_EDGE) d += 's';
    if (x < _W_EDGE) d += 'w'; else if (x > w - _W_EDGE) d += 'e';
    return d;
}

function _wCur(dir) {
    var m = {n:'n-resize',s:'s-resize',e:'e-resize',w:'w-resize',ne:'ne-resize',nw:'nw-resize',se:'se-resize',sw:'sw-resize'};
    return m[dir] || '';
}

function _wDoResize(e) {
    if (!_wRB) return;
    var dx = e.screenX - _wRSX, dy = e.screenY - _wRSY;
    var nx = _wRB.x, ny = _wRB.y, nw = _wRB.w, nh = _wRB.h;
    var minW = 960, minH = 600;
    if (_wRDir.indexOf('e') >= 0) nw = Math.max(minW, _wRB.w + dx);
    if (_wRDir.indexOf('s') >= 0) nh = Math.max(minH, _wRB.h + dy);
    if (_wRDir.indexOf('w') >= 0) { var pw = _wRB.w - dx; if (pw >= minW) { nw = pw; nx = _wRB.x + dx; } }
    if (_wRDir.indexOf('n') >= 0) { var ph = _wRB.h - dy; if (ph >= minH) { nh = ph; ny = _wRB.y + dy; } }
    try { window.varynxWindow.setBounds(nx, ny, nw, nh); } catch(_) {}
}

function initWindowChrome() {
    if (!window.varynxWindow) return;
    document.body.classList.add('desktop-mode');

    var bar = document.getElementById('status-bar');
    if (!bar) return;

    // Drag from header
    bar.addEventListener('mousedown', function(e) {
        if (_wIsInteractive(e.target)) return;
        if (_wEdge(e)) return;
        _wDrag = true;
        _wDragLX = e.screenX;
        _wDragLY = e.screenY;
        e.preventDefault();
    });

    // Double-click header to maximize/restore
    bar.addEventListener('dblclick', function(e) {
        if (_wIsInteractive(e.target)) return;
        try { window.varynxWindow.maximize(); } catch(_) {}
    });

    // Global mouse handlers for drag + resize
    document.addEventListener('mousemove', function(e) {
        if (_wDrag) {
            var dx = e.screenX - _wDragLX, dy = e.screenY - _wDragLY;
            _wDragLX = e.screenX; _wDragLY = e.screenY;
            try {
                var sx = window.varynxWindow.getX() + dx;
                var sy = window.varynxWindow.getY() + dy;
                window.varynxWindow.setPosition(sx, sy);
            } catch(_) {}
            return;
        }
        if (_wResize) { _wDoResize(e); return; }
        var dir = _wEdge(e);
        document.body.style.cursor = _wCur(dir);
    });

    document.addEventListener('mousedown', function(e) {
        var dir = _wEdge(e);
        if (dir && window.varynxWindow) {
            _wResize = true; _wRDir = dir;
            _wRSX = e.screenX; _wRSY = e.screenY;
            try {
                _wRB = {
                    x: window.varynxWindow.getX(), y: window.varynxWindow.getY(),
                    w: window.varynxWindow.getWidth(), h: window.varynxWindow.getHeight()
                };
            } catch(_) { _wResize = false; }
            e.preventDefault();
        }
    });

    document.addEventListener('mouseup', function() {
        _wDrag = false; _wResize = false; _wRDir = '';
        document.body.style.cursor = '';
    });

    // Window control buttons
    var minBtn = document.getElementById('wc-min');
    var maxBtn = document.getElementById('wc-max');
    var closeBtn = document.getElementById('wc-close');
    if (minBtn) minBtn.addEventListener('click', function() { try { window.varynxWindow.minimize(); } catch(_) {} });
    if (maxBtn) maxBtn.addEventListener('click', function() { try { window.varynxWindow.maximize(); } catch(_) {} });
    if (closeBtn) closeBtn.addEventListener('click', function() { try { window.varynxWindow.close(); } catch(_) {} });
}

// ── Settings Tab Switching ──

function switchSettingsTab(target, btn) {
    var allTabs = document.querySelectorAll('.settings-tab');
    var allPanels = document.querySelectorAll('.settings-panel');
    for (var t = 0; t < allTabs.length; t++) allTabs[t].classList.remove('active');
    for (var p = 0; p < allPanels.length; p++) allPanels[p].classList.remove('active');
    btn.classList.add('active');
    var panel = document.getElementById('settings-panel-' + target);
    if (panel) panel.classList.add('active');
}

function toggleDiagnostics(btn) {
    var body = document.getElementById('about-diag-body');
    if (body) {
        if (body.style.display === 'none') { body.style.display = 'block'; body.classList.remove('hidden'); }
        else { body.style.display = 'none'; body.classList.add('hidden'); }
        btn.classList.toggle('expanded');
    }
}
