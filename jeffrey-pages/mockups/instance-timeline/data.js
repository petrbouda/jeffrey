/*
 * Instance Timeline mockups — shared data + geometry.
 *
 * Every direction draws THIS data, so what differs between the eleven pages is layout, never the
 * numbers. The three real instances are transcribed verbatim from the screenshot of the shipped
 * page (workspace UAT, project jeffrey-hub, 24H range). The 12-instance FLEET is synthetic and is
 * used only by the directions whose whole point is density or aggregate shape — it is always
 * labelled as a different, busier project so it can never be mistaken for the real one.
 */

const HOUR = 3600e3;
const DAY = 24 * HOUR;

/* 09:39:07 + 3h52m = 13:31:07 — pinned so "Running · 3h 52m" in the screenshot stays true. */
const NOW = Date.UTC(2026, 7, 26, 13, 31, 7);

const RANGES = [
  { key: '1h', label: '1H', ms: HOUR },
  { key: '6h', label: '6H', ms: 6 * HOUR },
  { key: '24h', label: '24H', ms: DAY },
  { key: '7d', label: '7D', ms: 7 * DAY },
  { key: '30d', label: '30D', ms: 30 * DAY }
];

/* ------------------------------------------------------------------ the real three */

const INSTANCES = [
  {
    id: '25pjc',
    name: 'jeffrey-hub-74bd9b8c8d-25pjc',
    rs: '74bd9b8c8d',
    status: 'ACTIVE',
    start: Date.UTC(2026, 7, 26, 9, 39, 7),
    end: null,
    files: 9,
    bytes: 412 * 1024 * 1024,
    sessions: [
      { id: '4f2a91c7', start: Date.UTC(2026, 7, 26, 9, 39, 12), end: null, active: true, files: 9, bytes: 412 * 1024 * 1024 }
    ]
  },
  {
    id: 'qb9g7',
    name: 'jeffrey-hub-74bd9b8c8d-qb9g7',
    rs: '74bd9b8c8d',
    status: 'FINISHED',
    start: Date.UTC(2026, 7, 24, 5, 43, 47),
    end: Date.UTC(2026, 7, 26, 9, 39, 3),
    files: 51,
    bytes: 2140 * 1024 * 1024,
    sessions: [
      { id: 'b7c03e15', start: Date.UTC(2026, 7, 24, 5, 43, 52), end: Date.UTC(2026, 7, 26, 9, 39, 3), files: 51, bytes: 2140 * 1024 * 1024 }
    ]
  },
  {
    id: 's88hb',
    name: 'jeffrey-hub-86dd87c8ff-s88hb',
    rs: '86dd87c8ff',
    status: 'FINISHED',
    start: Date.UTC(2026, 7, 23, 18, 46, 40),
    end: Date.UTC(2026, 7, 24, 5, 43, 44),
    files: 11,
    bytes: 431 * 1024 * 1024,
    sessions: [
      { id: 'e9d4477a', start: Date.UTC(2026, 7, 23, 18, 46, 45), end: Date.UTC(2026, 7, 24, 5, 43, 44), files: 11, bytes: 431 * 1024 * 1024 }
    ]
  }
];

/*
 * Rollover facts that fall out of the real timestamps and that the shipped page cannot show:
 *   86dd87c8ff-s88hb ends 05:43:44  ->  74bd9b8c8d-qb9g7 starts 05:43:47   (3s gap, DEPLOY)
 *   74bd9b8c8d-qb9g7 ends 09:39:03  ->  74bd9b8c8d-25pjc starts 09:39:07   (4s gap, same RS = restart)
 * So this project has run exactly one instance at a time, with two sub-5-second holes in coverage.
 */
const EVENTS = [
  { at: Date.UTC(2026, 7, 23, 18, 46, 40), kind: 'start', label: 'First instance of the window starts' },
  { at: Date.UTC(2026, 7, 24, 5, 43, 47), kind: 'deploy', label: 'Deploy · 86dd87c8ff → 74bd9b8c8d · 3s gap' },
  { at: Date.UTC(2026, 7, 26, 9, 39, 7), kind: 'restart', label: 'Pod restart · 74bd9b8c8d · 4s gap' }
];

/* ------------------------------------------------------------------ the busier project */

function mkFleet() {
  const d = (day, h, m) => Date.UTC(2026, 7, day, h, m, 0);
  const rows = [];
  const push = (name, rs, status, start, end, sessions) =>
    rows.push({ id: name.slice(-5), name, rs, status, start, end, sessions });

  const ses = (start, end, extra) => Object.assign({ id: Math.abs(start / 1000 | 0).toString(16).slice(0, 8), start, end }, extra || {});

  /* rs 5c4b1e77 — the outgoing version */
  push('jeffrey-hub-5c4b1e77-p4x2m', '5c4b1e77', 'FINISHED', d(20, 4, 12), d(24, 5, 43), [ses(d(20, 4, 12), d(24, 5, 43))]);
  push('jeffrey-hub-5c4b1e77-h9k1t', '5c4b1e77', 'FINISHED', d(20, 4, 12), d(24, 5, 44), [ses(d(20, 4, 12), d(22, 3, 0)), ses(d(22, 15, 0), d(24, 5, 44))]);
  push('jeffrey-hub-5c4b1e77-w7n3q', '5c4b1e77', 'FINISHED', d(20, 4, 13), d(21, 22, 8), [ses(d(20, 4, 13), d(21, 22, 8))]);

  /* the crash loop — four zero-byte sessions in a row before the pod was pulled */
  push('jeffrey-hub-86dd87c8ff-z2v8r', '86dd87c8ff', 'FINISHED', d(24, 5, 44), d(24, 6, 21), [
    ses(d(24, 5, 44), d(24, 5, 52), { failed: true }),
    ses(d(24, 5, 55), d(24, 6, 3), { failed: true }),
    ses(d(24, 6, 6), d(24, 6, 14), { failed: true }),
    ses(d(24, 6, 15), d(24, 6, 21), { failed: true })
  ]);

  /* rs 86dd87c8ff — the incoming version */
  push('jeffrey-hub-86dd87c8ff-s88hb', '86dd87c8ff', 'FINISHED', d(23, 18, 46), d(24, 5, 43), [ses(d(23, 18, 46), d(24, 5, 43))]);
  push('jeffrey-hub-86dd87c8ff-m3j6c', '86dd87c8ff', 'FINISHED', d(24, 5, 45), d(25, 20, 12), [ses(d(24, 5, 45), d(25, 20, 12))]);
  push('jeffrey-hub-86dd87c8ff-t8b4y', '86dd87c8ff', 'FINISHED', d(24, 5, 45), d(26, 9, 39), [ses(d(24, 5, 45), d(24, 20, 0)), ses(d(25, 8, 0), d(26, 9, 39))]);

  /* rs 74bd9b8c8d — current */
  push('jeffrey-hub-74bd9b8c8d-qb9g7', '74bd9b8c8d', 'FINISHED', d(24, 5, 43), d(26, 9, 39), [ses(d(24, 5, 43), d(26, 9, 39))]);
  push('jeffrey-hub-74bd9b8c8d-25pjc', '74bd9b8c8d', 'ACTIVE', d(26, 9, 39), null, [ses(d(26, 9, 39), null, { active: true })]);
  push('jeffrey-hub-74bd9b8c8d-r6f9l', '74bd9b8c8d', 'ACTIVE', d(25, 20, 13), null, [ses(d(25, 20, 13), null, { active: true })]);
  push('jeffrey-hub-74bd9b8c8d-c1d5g', '74bd9b8c8d', 'ACTIVE', d(25, 22, 30), null, [ses(d(25, 22, 30), d(26, 4, 0)), ses(d(26, 9, 30), null, { active: true })]);
  push('jeffrey-hub-74bd9b8c8d-k4s2w', '74bd9b8c8d', 'PENDING', d(26, 13, 24), null, []);

  for (const r of rows) {
    r.files = 4 + (r.sessions.length * 7);
    r.bytes = r.sessions.reduce((sum, s) => sum + (s.failed ? 0 : ((s.end ?? NOW) - s.start) / HOUR * 38 * 1024 * 1024), 0);
  }
  return rows;
}

const FLEET = mkFleet();

const FLEET_DEPLOYS = [
  { at: Date.UTC(2026, 7, 23, 18, 46, 0), from: '5c4b1e77', to: '86dd87c8ff' },
  { at: Date.UTC(2026, 7, 24, 5, 44, 0), from: '86dd87c8ff', to: '74bd9b8c8d' }
];

/* ------------------------------------------------------------------ geometry */

function windowOf(rangeKey, now) {
  const range = RANGES.find(r => r.key === rangeKey) ?? RANGES[2];
  const end = now ?? NOW;
  return { start: end - range.ms, end: end, ms: range.ms, key: range.key, label: range.label };
}

/** Position of an instant inside the window, as a percentage. Left edge = oldest. */
function pct(t, win) {
  return ((t - win.start) / win.ms) * 100;
}

const MIN_BAR_PERCENT = 0.4;

/**
 * Bar geometry for a [start, end) interval clipped to the window.
 * Mirrors TraceWaterfallLayout.spanBar: a floor on the width so sub-pixel bars stay hoverable,
 * then a pull-back so a clamped width can never push the bar past the right edge.
 */
function barGeom(start, end, win) {
  const finish = end ?? win.end;
  if (finish <= win.start || start >= win.end) {
    return { outside: true, before: finish <= win.start, left: 0, width: 0, clampLeft: false, clampRight: false };
  }
  const from = Math.max(start, win.start);
  const to = Math.min(finish, win.end);
  const rawLeft = pct(from, win);
  const rawWidth = Math.max(pct(to, win) - rawLeft, MIN_BAR_PERCENT);
  const width = Math.min(rawWidth, 100);
  return {
    outside: false,
    left: Math.min(Math.max(rawLeft, 0), 100 - width),
    width: width,
    clampLeft: start < win.start,
    clampRight: finish > win.end
  };
}

/** Axis ticks, oldest at 0% and Now at 100%. */
function axisTicks(win) {
  const steps = {
    '1h': [[0, '-1h'], [0.25, '-45m'], [0.5, '-30m'], [0.75, '-15m'], [1, 'Now']],
    '6h': [[0, '-6h'], [1 / 3, '-4h'], [2 / 3, '-2h'], [1, 'Now']],
    '24h': [[0, '-24h'], [0.25, '-18h'], [0.5, '-12h'], [0.75, '-6h'], [1, 'Now']],
    '7d': [[0, '-7d'], [0.25, '-5d'], [0.5, '-3d'], [0.75, '-1d'], [1, 'Now']],
    '30d': [[0, '-30d'], [0.25, '-3w'], [0.5, '-2w'], [0.75, '-1w'], [1, 'Now']]
  };
  return (steps[win.key] ?? steps['24h']).map(([f, label]) => ({ pos: f * 100, label: label }));
}

/* ------------------------------------------------------------------ formatting */

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function two(n) { return String(n).padStart(2, '0'); }

function fmtUTC(t) {
  const d = new Date(t);
  return d.getUTCFullYear() + '-' + two(d.getUTCMonth() + 1) + '-' + two(d.getUTCDate()) +
    ' ' + two(d.getUTCHours()) + ':' + two(d.getUTCMinutes()) + ':' + two(d.getUTCSeconds()) + ' UTC';
}

function fmtClock(t) {
  const d = new Date(t);
  return two(d.getUTCHours()) + ':' + two(d.getUTCMinutes());
}

function fmtDayClock(t) {
  const d = new Date(t);
  return MONTHS[d.getUTCMonth()] + ' ' + d.getUTCDate() + ' ' + two(d.getUTCHours()) + ':' + two(d.getUTCMinutes());
}

/** Two units, like FormattingService.formatDurationInMillis2Units. */
function fmtDur(ms) {
  if (ms == null) { return '—'; }
  const s = Math.floor(ms / 1000);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  if (d > 0) { return d + 'd ' + h + 'h'; }
  if (h > 0) { return h + 'h ' + m + 'm'; }
  if (m > 0) { return m + 'm ' + sec + 's'; }
  return sec + 's';
}

function fmtBytes(b) {
  if (!b) { return '0 B'; }
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let i = 0;
  let v = b;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return (v >= 100 ? v.toFixed(0) : v.toFixed(1)) + ' ' + units[i];
}

function durationOf(inst, now) {
  return (inst.end ?? (now ?? NOW)) - inst.start;
}

function statusClass(status) { return status.toLowerCase(); }

/* ------------------------------------------------------------------ shared chrome */

function pageChrome(title, toolbarHtml, bodyHtml) {
  return `
  <div class="frame">
    <div class="proj-crumb"><span>Workspaces</span> <i class="bi bi-chevron-right" style="font-size:.6rem"></i>
      <span>UAT</span> <i class="bi bi-chevron-right" style="font-size:.6rem"></i> <b>jeffrey-hub</b></div>
    <div class="proj-tabs">
      <span class="proj-tab active"><i class="bi bi-bar-chart-steps"></i>Timeline</span>
      <span class="proj-tab"><i class="bi bi-grid"></i>Instances</span>
      <span class="proj-tab"><i class="bi bi-broadcast"></i>Live Stream</span>
      <span class="proj-tab"><i class="bi bi-collection-play"></i>Replay Stream</span>
      <span class="proj-tab"><i class="bi bi-cpu"></i>Profiler Settings</span>
      <span class="proj-tab"><i class="bi bi-sliders"></i>Settings</span>
    </div>
    <div style="padding:1rem 1rem 1.25rem;background:var(--color-light)">
      <div class="main-card">
        <div class="page-header">
          <div class="page-header-info">
            <i class="page-header-icon bi bi-bar-chart-steps"></i>
            <span class="page-header-title">${title}</span>
          </div>
        </div>
        <div class="main-card-content">
          ${toolbarHtml}
          ${bodyHtml}
        </div>
      </div>
    </div>
  </div>`;
}

function rangeGroup(activeKey) {
  return '<div class="range-group">' +
    RANGES.map(r => `<button class="range-btn${r.key === activeKey ? ' active' : ''}">${r.label}</button>`).join('') +
    '</div>';
}

function countChip(instances, sessions, failed) {
  return `<span class="count-chip"><i class="bi bi-box"></i><b>${instances}</b> instances
    <span class="sep">·</span><b>${sessions}</b> sessions` +
    (failed ? `<span class="sep">·</span><span class="fail"><b>${failed}</b> failed</span>` : '') +
    '</span>';
}

/** The shared ruler. Oldest at the left, Now at the right — in every direction. */
function rulerRow(win, leftLabel, rightLabel) {
  return `<div class="wf-head">
    <span>${leftLabel ?? 'Instance'}</span>
    <span class="wf-scale">${axisTicks(win).map(t => `<span style="left:${t.pos}%">${t.label}</span>`).join('')}</span>
    <span class="wf-duration">${rightLabel ?? 'Duration'}</span>
  </div>`;
}

function notesBlock(good, cost, build) {
  const li = xs => xs.map(x => `<li>${x}</li>`).join('');
  return `<div class="notes">
    <div class="note good"><h4><i class="bi bi-check-circle-fill"></i> What it buys</h4><ul>${li(good)}</ul></div>
    <div class="note cost"><h4><i class="bi bi-exclamation-triangle-fill"></i> What it costs</h4><ul>${li(cost)}</ul></div>
    <div class="note build"><h4><i class="bi bi-tools"></i> What it takes to build</h4><ul>${li(build)}</ul></div>
  </div>`;
}

/** The session drawer — identical in every direction, because it is the part that already works. */
function drawerHtml(inst, session) {
  const s = session ?? inst.sessions[0];
  return `<div class="span-detail">
    <div class="sd-head">
      <i class="bi bi-layers" style="color:var(--color-primary)"></i>
      <span style="font-size:var(--font-size-xs);letter-spacing:.06em;text-transform:uppercase;color:var(--color-text-muted);font-weight:600">Session</span>
      <span style="font-family:var(--font-family-monospace);font-size:var(--font-size-sm)">${s.id}</span>
      <span class="badge ${s.active ? 'badge-active' : 'badge-finished'}">${s.active ? 'recording' : 'finished'}</span>
      <span class="sd-actions">
        ${s.active ? '<span class="sd-act primary"><i class="bi bi-broadcast"></i> Live Stream</span>' : ''}
        <span class="sd-act"><i class="bi bi-collection-play"></i> Replay Stream</span>
        <span class="sd-act"><i class="bi bi-download"></i> Download</span>
        <span class="sd-act"><i class="bi bi-pin-angle"></i> Pin</span>
      </span>
    </div>
    <div class="sd-cols">
      <div class="sd-card"><div class="sd-card-head">jdk.JVMInformation</div><div class="sd-card-body">
        <div class="kv"><span class="k">jvmName</span><span class="v">OpenJDK 64-Bit Server VM</span></div>
        <div class="kv"><span class="k">jvmVersion</span><span class="v">25.0.1+9-LTS</span></div>
        <div class="kv"><span class="k">pid</span><span class="v">1</span></div>
      </div></div>
      <div class="sd-card"><div class="sd-card-head">jdk.OSInformation</div><div class="sd-card-body">
        <div class="kv"><span class="k">osVersion</span><span class="v">Linux 6.8.0-137-generic</span></div>
        <div class="kv"><span class="k">virtualization</span><span class="v">kvm</span></div>
      </div></div>
      <div class="sd-card"><div class="sd-card-head">jdk.CPUInformation</div><div class="sd-card-body">
        <div class="kv"><span class="k">cores</span><span class="v">8</span></div>
        <div class="kv"><span class="k">hwThreads</span><span class="v">16</span></div>
      </div></div>
      <div class="sd-card"><div class="sd-card-head">Storage</div><div class="sd-card-body">
        <div class="kv"><span class="k">files</span><span class="v">${s.files ?? inst.files}</span></div>
        <div class="kv"><span class="k">size</span><span class="v">${fmtBytes(s.bytes ?? inst.bytes)}</span></div>
        <div class="kv"><span class="k">started</span><span class="v">${fmtUTC(s.start)}</span></div>
      </div></div>
    </div>
  </div>`;
}
