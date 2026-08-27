/*
 * Shared render helpers for the five card variants. Data and geometry come from
 * ../instance-timeline/data.js — the same three instances as the screenshot, at 24H.
 */

const WIN = windowOf('24h');

/** Vertical gridlines inside a lane, at the same positions as the shared ruler's ticks. */
function gridLines(win) {
  return axisTicks(win)
    .filter(t => t.pos > 0 && t.pos < 100)
    .map(t => `<span class="lane-grid" style="left:${t.pos}%"></span>`)
    .join('');
}

/**
 * The shared ruler, drawn once above the stack instead of once per card.
 * `inset` must match the lane's own left/right offset inside the card — card border + padding —
 * or the ticks will not line up with the bars they label.
 */
function ruler(win, inset) {
  const i = inset ?? {};
  const style = `margin-left:${i.left ?? '0'};margin-right:${i.right ?? '0'}`;
  return `<div class="ruler" style="${style}">` +
    axisTicks(win).map(t => `<span style="left:${t.pos}%">${t.label}</span>`).join('') +
    '</div>';
}

/** Bar + clamp markers + the out-of-window state the shipped page renders as nothing at all. */
function laneBody(inst, win, opts) {
  const o = opts ?? {};
  const g = barGeom(inst.start, inst.end, win);
  const cls = inst.status.toLowerCase();

  if (g.outside) {
    const away = fmtDur(win.start - (inst.end ?? NOW));
    return `${o.grid === false ? '' : gridLines(win)}
      <span class="offwin"><i class="bi bi-chevron-double-left"></i>
        finished ${away} before this window opens</span>`;
  }

  const title = `${inst.name}\n${fmtUTC(inst.start)} → ${inst.end ? fmtUTC(inst.end) : 'running'}\n${fmtDur(durationOf(inst))}`;
  return `${o.grid === false ? '' : gridLines(win)}
    ${g.clampLeft ? '<span class="clamp l"><i class="bi bi-chevron-double-left"></i></span>' : ''}
    <span class="bar ${cls}" style="left:${g.left}%;width:${g.width}%" title="${title}">${o.inner ?? ''}</span>
    ${g.clampRight ? '<span class="clamp r"><i class="bi bi-chevron-double-right"></i></span>' : ''}`;
}

function metaHtml(inst) {
  return `<span class="mk">Started</span><span class="mv">${fmtUTC(inst.start)}</span>
    <span class="msep">→</span>
    ${inst.end
      ? `<span class="mk">Finished</span><span class="mv">${fmtUTC(inst.end)}</span>`
      : '<span class="mv mrun">Running</span>'}
    <span class="msep">·</span>
    <span class="mk">Duration</span><span class="mv">${fmtDur(durationOf(inst))}</span>`;
}

function chipsHtml(inst, open) {
  const n = inst.sessions.length;
  return `<span class="ichip"><i class="bi bi-layers"></i>${n} session${n === 1 ? '' : 's'}
      <i class="bi bi-arrow-up-right"></i></span>
    <i class="bi bi-chevron-${open ? 'down' : 'right'} ichev"></i>`;
}

/** Toolbar: presets + the count chip. The "adjacent finished/active" legend is gone everywhere —
 *  it described index parity, not data. */
function toolbar(extra) {
  return `<div class="tl-toolbar">${rangeGroup('24h')}
    ${extra ?? ''}
    ${countChip(3, 3)}</div>`;
}

/**
 * Density readout. Measured from the rendered DOM after paint rather than typed in by hand, so the
 * number on the page cannot drift from the layout it describes. 140px is the shipped card, which
 * mockup-0 in the sibling set draws.
 */
const SHIPPED_CARD_PX = 140;

function heightNote() {
  return '<div class="note-inline" id="height-note"></div>';
}

function measureHeights() {
  const note = document.getElementById('height-note');
  if (!note) { return; }
  /* the second card: a collapsed, finished instance — the representative case, not the open one */
  const card = document.querySelectorAll('.icard')[1];
  if (!card) { return; }
  const px = Math.round(card.getBoundingClientRect().height);
  note.innerHTML = `<i class="bi bi-arrows-collapse"></i>
    <span class="hdiff">collapsed card <b>${px}px</b> <s>shipped ~${SHIPPED_CARD_PX}px</s></span>
    · ${(SHIPPED_CARD_PX / px).toFixed(1)}× more instances per screen · measured from this page`;
}

function fixedNote() {
  return `<div class="note-inline"><i class="bi bi-check2-circle" style="color:var(--color-success)"></i>
    fixed in every variant: time runs left → right · one ruler for the whole stack ·
    edge labels no longer clipped · the parity legend is gone · out-of-window instances say so</div>`;
}
