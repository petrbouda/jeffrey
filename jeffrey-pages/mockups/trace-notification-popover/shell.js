/*
 * Shared data + waterfall render for the popover mockups.
 *
 * The trace, the lanes and the rows are transcribed from the screenshot of the shipped modal, so
 * every variant is judged against the same picture. Two notifications are drawn on purpose:
 *
 *   SHORT — the real one from the screenshot. "Pipeline run completed" is three words, and it is
 *           the reason the popover reads as empty today: the panel is sized for a paragraph.
 *   LONG  — the same shape carrying a message and attribute set worth scrolling, so a format that
 *           only works when there is little to say is caught here rather than in review.
 */

const TRACE = {
  name: 'POST /api/internal/recordings/recordings/{recordingId}/analyze',
  spans: 3610,
  duration: '11s 286ms',
  threads: 6,
  kind: 'SERVER',
  notifications: 2,
  throws: 153,
  hot: 'chunk.parse · 71%',
  id: '2291db38124f4a53'
};

const SHORT = {
  severity: 'LOW',
  sevClass: 'sev-low',
  badge: 'secondary',
  type: 'PIPELINE_COMPLETED',
  at: '+10s 85ms',
  message: 'Pipeline run completed',
  attrs: [
    ['pipelineId', 'profile-init'],
    ['profileId', '01a03fb5-d51f-7292-974c-bdfcef9d35de'],
    ['scopeId', 'empty'],
    ['durationMs', '8847']
  ],
  category: 'PROFILE',
  source: 'cafe.jeffrey.profile.manager.ProfileInitializer',
  span: 'profile-init'
};

const LONG = {
  severity: 'MEDIUM',
  sevClass: 'sev-medium',
  badge: 'warning',
  type: 'CONNECTION_POOL_EXHAUSTED',
  at: '+7s 402ms',
  message:
    'No connection was available within the borrow timeout, so the request fell back to opening ' +
    'one outside the pool. The pool has been at its ceiling for the last 42 seconds and 17 other ' +
    'borrowers are queued behind this one — the ceiling, not the query, is what this trace is ' +
    'waiting on.',
  attrs: [
    ['pool', 'profile-duckdb'],
    ['maxSize', '16'],
    ['active', '16'],
    ['queued', '17'],
    ['borrowTimeoutMs', '2500'],
    ['waitedMs', '2500'],
    ['leakSuspects', '3'],
    ['lastGrowthAt', '2026-08-27T05:58:11Z']
  ],
  category: 'PERSISTENCE',
  source: 'cafe.jeffrey.shared.persistence.pool.BorrowGuard',
  span: 'chunk.parse'
};

const SPAN_ROWS = [
  { name: 'POST /api/internal/recordings/recordings/{recordi…', kind: 'server', depth: 0, left: 0.5, width: 92, dur: '10s 98ms', twist: true, count: 1 },
  { name: 'File read', kind: 'internal', depth: 1, left: 0.6, width: 0.5, dur: '51us 11ns' },
  { name: 'File read', kind: 'internal', depth: 1, left: 0.7, width: 0.4, dur: '28us 792ns' },
  { name: 'find_recording', kind: 'client', depth: 1, left: 0.8, width: 0.6, dur: '10ms 87us' },
  { name: 'find_recording_files', kind: 'client', depth: 1, left: 1.0, width: 0.5, dur: '3ms 934us' },
  { name: 'File read', kind: 'internal', depth: 1, left: 1.2, width: 0.4, dur: '25us 688us' },
  { name: 'File read', kind: 'internal', depth: 1, left: 1.3, width: 0.4, dur: '11us 291us' },
  { name: 'insert_profile', kind: 'client', depth: 1, left: 1.5, width: 0.7, dur: '11ms 79us' }
];

/** Where the two notification marks sit on the rail, as a percentage of the trace window. */
const MARK_AT = { short: 74, long: 63 };

function metaStrip() {
  return `<div class="trace-meta">
    <span class="chip"><i class="bi bi-diagram-3"></i> ${TRACE.spans} spans</span>
    <span class="chip"><i class="bi bi-stopwatch"></i> ${TRACE.duration}</span>
    <span class="chip"><i class="bi bi-cpu"></i> ${TRACE.threads} threads</span>
    <span class="chip"><i class="bi bi-hdd-network"></i> ${TRACE.kind}</span>
    <span class="chip msg"><i class="bi bi-chat-square-dots-fill"></i> ${TRACE.notifications} notifications</span>
    <span class="chip err"><i class="bi bi-x-octagon-fill"></i> ${TRACE.throws} throws</span>
    <span class="chip"><i class="bi bi-signpost-split"></i> ${TRACE.hot}</span>
    <span class="chip mono"># ${TRACE.id}</span>
  </div>`;
}

function toolbar() {
  const sw = label => `<span class="wf-switch-item"><span class="wf-switch on"></span> ${label}</span>`;
  return `<div class="wf-toolbar">
    <span class="wf-toggle"><i class="bi bi-signpost-split"></i> Critical path only</span>
    <span class="wf-toggle"><i class="bi bi-arrows-collapse"></i> Collapse all</span>
    <span class="wf-overlays">
      <span class="wf-overlays-label">Overlays</span>
      ${sw('JVM context')}${sw('Blocking ops')}${sw('I/O ops')}${sw('Notifications')}${sw('Exceptions')}
    </span>
  </div>`;
}

function contextLanes() {
  const bands = (n, color, seed) => {
    let out = '';
    for (let i = 0; i < n; i++) {
      out += `<span class="lane-band" style="left:${(seed + i * 7.3) % 96}%;width:.6%;background:${color}"></span>`;
    }
    return out;
  };
  return `
    <div class="wf-lane">
      <span class="lane-label"><i class="lane-dot" style="background:var(--color-goldenrod)"></i> Safepoint
        <span class="lane-meter"><i style="width:3.1%;background:var(--color-goldenrod)"></i></span>
        <span class="lane-stat">3.1% · 12×</span></span>
      <span class="lane-track">${bands(12, 'var(--color-goldenrod)', 6)}</span>
      <span class="wf-duration">353ms 855us</span>
    </div>
    <div class="wf-lane">
      <span class="lane-label"><i class="lane-dot" style="background:var(--color-danger)"></i> GC pause
        <span class="lane-meter"><i style="width:3.1%;background:var(--color-danger)"></i></span>
        <span class="lane-stat">3.1% · 7×</span></span>
      <span class="lane-track">${bands(7, 'var(--color-danger)', 9)}</span>
      <span class="wf-duration">353ms 90us</span>
    </div>`;
}

/** The notification rail. `openAt` is the mark the popover hangs off; `pop` is its markup. */
function notificationRail(openAt, pop) {
  return `<div class="wf-rail">
    <span class="lane-label"><i class="lane-dot" style="background:var(--color-warning);transform:rotate(45deg);border-radius:1px"></i>
      Notifications <span class="lane-stat">medium · 2×</span></span>
    <span class="lane-track rail-track">
      <span class="rail-rule"></span>
      <span class="rail-mark ntf ${openAt === MARK_AT.long ? 'open' : ''}" style="left:${MARK_AT.long}%;--mark:var(--color-warning)"></span>
      <span class="rail-mark ntf ${openAt === MARK_AT.short ? 'open' : ''}" style="left:${MARK_AT.short}%;--mark:var(--color-secondary)"></span>
      ${pop}
    </span>
    <span class="wf-duration">2</span>
  </div>`;
}

function exceptionRail() {
  let marks = '';
  for (let i = 0; i < 22; i++) {
    marks += `<span class="rail-mark exc" style="left:${(38 + i * 2.4) % 60 + 34}%;--mark:var(--color-text-muted)"></span>`;
  }
  return `<div class="wf-rail">
    <span class="lane-label"><i class="lane-dot" style="background:var(--color-text-muted)"></i>
      Exceptions <span class="lane-stat">153×</span></span>
    <span class="lane-track rail-track"><span class="rail-rule"></span>${marks}</span>
    <span class="wf-duration">153</span>
  </div>`;
}

function spanRows() {
  return SPAN_ROWS.map(r => `<div class="wf-row">
    <span class="wf-name">
      <span class="wf-indent" style="width:${r.depth * 0.9}rem"></span>
      <span class="wf-twist">${r.twist ? '<i class="bi bi-caret-down-fill"></i>' : ''}</span>
      <span class="wf-kind kind-${r.kind}"></span>
      <span class="wf-label">${r.name}</span>
      ${r.count ? `<span class="wf-count" style="--mark:var(--color-text-muted)">${r.count}</span>` : ''}
    </span>
    <span class="wf-track"><span class="wf-bar bar-${r.kind}" style="left:${r.left}%;width:${r.width}%"></span></span>
    <span class="wf-duration">${r.dur}</span>
  </div>`).join('');
}

/**
 * The whole modal, with `pop` anchored to the rail. `inlineRow` is for the one variant that does
 * not float: it is inserted between the rail and the span rows instead.
 */
function modal(pop, openAt, inlineRow) {
  return `<div class="modal-frame">
    <div class="modal-head">
      <i class="bi bi-diagram-3"></i>
      <h2>${TRACE.name}</h2>
      <span class="x"><i class="bi bi-x-lg"></i></span>
    </div>
    <div class="modal-body">
      ${metaStrip()}
      <div class="wf-card">
        ${toolbar()}
        ${contextLanes()}
        ${notificationRail(openAt, pop ?? '')}
        ${exceptionRail()}
        <div class="wf-head">
          <span>Span</span>
          <span class="wf-scale"><span>0</span><span>${TRACE.duration}</span></span>
          <span class="wf-duration">Duration</span>
        </div>
        <div class="wf-rows">${inlineRow ?? ''}${spanRows()}</div>
      </div>
    </div>
  </div>`;
}

/**
 * Two panels per page: the real short notification, then one worth scrolling.
 * `inline: true` puts the rendered panel in the rows area instead of on the rail, for the one
 * variant that does not float.
 */
function bothCases(render, opts) {
  const inline = (opts ?? {}).inline === true;
  /* `at` is the mark's position on the rail — the real component sets the popover's `left` from
     offsetPercent(...), so a mockup that leaves it unset would not be anchored where it really is. */
  const place = (n, at) => inline ? modal(null, at, render(n, at)) : modal(render(n, at), at);
  return `
    <div class="panel" style="margin-bottom:1.1rem">
      <h3>The real one <span class="tag now">from the screenshot</span>
        <span class="measure" id="m-short"></span></h3>
      <p class="cap">${SHORT.type} — a three-word message and four attributes. This is the case that
        reads as empty today.</p>
      ${place(SHORT, MARK_AT.short)}
    </div>
    <div class="panel">
      <h3>One worth scrolling <span class="tag new">stress case</span>
        <span class="measure" id="m-long"></span></h3>
      <p class="cap">${LONG.type} — a paragraph and eight attributes, two past the
        <code>MAX_POPOVER_ATTRIBUTES</code> cap of 6.</p>
      ${place(LONG, MARK_AT.long)}
    </div>`;
}

/** Attribute chips, capped at 6 as MAX_POPOVER_ATTRIBUTES does, with the "+N more" tail. */
function attrChips(n, cls) {
  const shown = n.attrs.slice(0, 6);
  const rest = n.attrs.length - shown.length;
  const c = cls ?? 'pop-attr';
  return shown.map(([k, v]) =>
    `<span class="${c}"><span class="${c}-k">${k}</span><span class="${c}-v">${v}</span></span>`
  ).join('') + (rest > 0 ? `<span class="pop-attr-more">+${rest} more</span>` : '');
}

/*
 * Measured from the rendered DOM, so the number cannot drift from the drawing. Panels that fill
 * their container are labelled fluid rather than given a width: theirs is whatever the dialog is,
 * and a fixed figure here would read as a property of the design instead of of this viewport.
 */
function measurePanels() {
  const rows = document.querySelectorAll('.wf-rows');
  for (const [id, sel] of [['m-short', 0], ['m-long', 1]]) {
    const el = document.getElementById(id);
    const panel = document.querySelectorAll('.pop-measure')[sel];
    if (!el || !panel) { continue; }
    const r = panel.getBoundingClientRect();
    const container = rows[sel] ? rows[sel].getBoundingClientRect().width : 0;
    const fluid = container > 0 && r.width > container * 0.6;
    el.innerHTML = fluid
      ? `panel <b>fluid</b> · ${Math.round(r.height)}px tall here`
      : `panel <b>${Math.round(r.width)}×${Math.round(r.height)}px</b>`;
  }
}
