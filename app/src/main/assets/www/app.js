// Dashboard logic for USync
const api = (path) => fetch('/api/' + path).then(r => r.json());

// ---------- Tabs ----------
document.querySelectorAll('.tab').forEach(btn => {
    btn.addEventListener('click', () => {
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        document.getElementById('tab-' + btn.dataset.tab).classList.add('active');
        if (btn.dataset.tab === 'files' && !curPath) loadFiles();
    });
});

// ---------- Connection indicator ----------
async function checkConn() {
    try {
        const r = await api('ping');
        const ok = r && r.pong;
        document.getElementById('connDot').className = 'dot ' + (ok ? 'dot-green' : 'dot-red');
        document.getElementById('connText').textContent = ok ? 'Connected' : 'Offline';
    } catch {
        document.getElementById('connDot').className = 'dot dot-red';
        document.getElementById('connText').textContent = 'Offline';
    }
}
checkConn();
setInterval(checkConn, 5000);

// ---------- Overview ----------
async function loadOverview() {
    try {
        const d = await api('info');
        if (d.error) return;
        document.getElementById('d-model').textContent = d.model || '—';
        document.getElementById('d-android').textContent = d.android || '—';
        document.getElementById('d-battery').textContent = d.battery || '—';
        document.getElementById('d-charging').textContent = d.charging ? 'Yes ⚡' : 'No';
        document.getElementById('d-free').textContent = d.storage_free || '—';
        document.getElementById('d-used').textContent = (d.storage_used_pct || 0) + '%';
        document.getElementById('storageBar').style.width = (d.storage_used_pct || 0) + '%';
    } catch (e) { console.error(e); }
}
loadOverview();

// ---------- Files ----------
let curPath = null;
let parentPath = null;
const fileInput = document.getElementById('fileInput');
const uploadBtn = document.getElementById('uploadBtn');
const uploadStatus = document.getElementById('uploadStatus');
const fileNameDisplay = document.getElementById('fileNameDisplay');

fileInput.addEventListener('change', () => {
    if (fileInput.files.length > 0) {
        fileNameDisplay.textContent = fileInput.files[0].name;
    } else {
        fileNameDisplay.textContent = "Choose a file to export...";
    }
});

async function loadFiles(path) {
    const p = path ? encodeURIComponent(path) : '';
    try {
        const r = await api('files' + (p ? '?path=' + p : ''));
        if (r.error) {
            document.getElementById('fileList').innerHTML = `<li style="color:red; font-weight:bold">${r.error}</li>`;
            return;
        }
        curPath = r.path;
        parentPath = r.parent;
        document.getElementById('curPath').textContent = r.path;
        const ul = document.getElementById('fileList');
        ul.innerHTML = '';

        if (!r.files || r.files.length === 0) {
            ul.innerHTML = '<li class="muted">This folder is empty.</li>';
            return;
        }

        r.files.forEach(f => {
            const li = document.createElement('li');
            li.innerHTML = `
                <div class="name-wrap">
                    <span class="icon">${f.dir ? '📁' : '📄'}</span>
                    <span class="name-text">${f.name}</span>
                </div>
                <div class="size-wrap">${f.dir ? '—' : humanSize(f.size)}</div>
                <div class="action-wrap">
                    ${!f.dir ? `<button class="btn sm primary" onclick="downloadFile('${f.path.replace(/\\/g, '\\\\')}', event)">Import ⬇</button>` : ''}
                </div>
            `;

            if (f.dir) {
                li.onclick = () => loadFiles(f.path);
            }
            ul.appendChild(li);
        });
    } catch (e) {
        console.error(e);
    }
}

window.downloadFile = (path, e) => {
    if (e) e.stopPropagation();
    window.location.href = '/download?path=' + encodeURIComponent(path);
};

document.getElementById('upBtn').addEventListener('click', () => {
    if (parentPath) loadFiles(parentPath);
});

uploadBtn.addEventListener('click', async () => {
    if (!fileInput.files || !fileInput.files[0]) {
        uploadStatus.style.color = '#EA4335';
        uploadStatus.textContent = 'Please select a file first.';
        return;
    }
    const file = fileInput.files[0];
    const fd = new FormData();
    fd.append('file', file, file.name);

    uploadStatus.style.color = '#1A73E8';
    uploadStatus.textContent = '🚀 Sending to phone...';

    try {
        const url = `/upload?filename=${encodeURIComponent(file.name)}&dir=${encodeURIComponent(curPath || '')}`;
        const res = await fetch(url, { method: 'POST', body: fd });
        const r = await res.json();

        if (r.error) {
            uploadStatus.style.color = '#EA4335';
            uploadStatus.textContent = '❌ Error: ' + r.error;
        } else {
            uploadStatus.style.color = '#34A853';
            uploadStatus.textContent = '✅ Successfully exported to mobile!';
            loadFiles(curPath);
            fileInput.value = '';
            fileNameDisplay.textContent = "Choose a file to export...";
        }
    } catch (e) {
        uploadStatus.style.color = '#EA4335';
        uploadStatus.textContent = '❌ Connection lost: ' + e.message;
    }
});

function humanSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(1) + ' ' + units[i];
}

// ---------- SMS / Location / Notif / Clip (simplified) ----------
function escapeHtml(s) {
    return (s || '').toString().replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

document.getElementById('locBtn').addEventListener('click', async () => {
    const info = document.getElementById('locInfo');
    info.textContent = '📡 Locating device...';
    try {
        const loc = await api('location');
        if (loc.error) { info.textContent = loc.error; return; }
        info.textContent = `📍 Found at: ${loc.lat.toFixed(6)}, ${loc.lng.toFixed(6)}`;
        document.getElementById('mapFrame').src = `https://www.openstreetmap.org/export/embed.html?bbox=${loc.lng-0.01},${loc.lat-0.01},${loc.lng+0.01},${loc.lat+0.01}&layer=mapnik&marker=${loc.lat},${loc.lng}`;
    } catch (e) { info.textContent = 'Error: ' + e.message; }
});

document.getElementById('smsBtn').addEventListener('click', async () => {
    const ul = document.getElementById('smsList');
    ul.innerHTML = '<li>Loading messages...</li>';
    try {
        const r = await api('sms');
        ul.innerHTML = (r.messages || []).map(m => `<li><b>${escapeHtml(m.address)}</b><br><small>${new Date(m.date).toLocaleString()}</small><p>${escapeHtml(m.body)}</p></li>`).join('') || '<li>No messages.</li>';
    } catch (e) { ul.innerHTML = '<li>Error loading SMS.</li>'; }
});

document.getElementById('notifBtn').addEventListener('click', async () => {
    const ul = document.getElementById('notifList');
    ul.innerHTML = '<li>Refreshing...</li>';
    try {
        const r = await api('notifications');
        ul.innerHTML = (r.items || []).map(n => `<li><b>${escapeHtml(n.package)}</b><br><small>${n.timeText}</small><p>${escapeHtml(n.title || n.body)}</p></li>`).join('') || '<li>No notifications.</li>';
    } catch (e) { ul.innerHTML = '<li>Error loading notifications.</li>'; }
});

document.getElementById('clipBtn').addEventListener('click', async () => {
    try {
        const r = await api('clipboard');
        document.getElementById('clipBox').value = r.text || '';
    } catch (e) { }
});
