/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import type { UpdateCheckResponse } from '@/services/api/VersionClient';

const DISMISS_KEY_PREFIX = 'jeffrey-update-dismissed-';
const INFO_AUTO_HIDE_DELAY = 10000;
const WARNING_AUTO_HIDE_DELAY = 15000;

function isDismissed(latestVersion: string): boolean {
  return localStorage.getItem(DISMISS_KEY_PREFIX + latestVersion) === 'true';
}

function dismiss(latestVersion: string): void {
  clearOldDismissals(latestVersion);
  localStorage.setItem(DISMISS_KEY_PREFIX + latestVersion, 'true');
}

function clearOldDismissals(currentVersion: string): void {
  const currentKey = DISMISS_KEY_PREFIX + currentVersion;
  Object.keys(localStorage)
    .filter(k => k.startsWith(DISMISS_KEY_PREFIX) && k !== currentKey)
    .forEach(k => localStorage.removeItem(k));
}

function getOrCreateContainer(): HTMLElement {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    container.className = 'toast-container';
    document.body.appendChild(container);
  }
  return container;
}

function hideToast(toast: HTMLElement): void {
  toast.classList.add('hide');
  setTimeout(() => toast.remove(), 300);
}

function buildToast(info: UpdateCheckResponse): HTMLElement {
  const isMajor = info.majorUpdate;
  const toastClass = isMajor ? 'toast-warning' : 'toast-info';
  const iconText = isMajor ? '\u26A0' : '\u24D8';
  const title = isMajor ? 'Major Update Available' : 'Update Available';

  const toast = document.createElement('div');
  toast.className = `toast ${toastClass} update-check-toast`;
  toast.setAttribute('role', 'alert');
  toast.setAttribute('aria-live', 'assertive');
  toast.setAttribute('aria-atomic', 'true');

  // Accent bar
  const accent = document.createElement('div');
  accent.className = 'toast-accent';
  toast.appendChild(accent);

  // Content wrapper
  const content = document.createElement('div');
  content.className = 'toast-content';
  content.style.flexDirection = 'column';
  content.style.gap = '8px';

  // Title row (icon + title + close)
  const titleRow = document.createElement('div');
  titleRow.style.cssText = 'display:flex;align-items:center;gap:10px;width:100%';

  const icon = document.createElement('div');
  icon.className = 'toast-icon';
  icon.textContent = iconText;
  titleRow.appendChild(icon);

  const titleEl = document.createElement('div');
  titleEl.className = 'toast-title';
  titleEl.style.flex = '1';
  titleEl.textContent = title;
  titleRow.appendChild(titleEl);

  const closeBtn = document.createElement('button');
  closeBtn.className = 'toast-close';
  closeBtn.setAttribute('aria-label', 'Close');
  closeBtn.innerHTML = '&times;';
  closeBtn.addEventListener('click', () => {
    dismiss(info.latestVersion);
    hideToast(toast);
  });
  titleRow.appendChild(closeBtn);

  content.appendChild(titleRow);

  // Warning note (major only)
  if (isMajor) {
    const warningNote = document.createElement('div');
    warningNote.className = 'update-warning-note';
    warningNote.innerHTML =
      '<i class="bi bi-exclamation-triangle-fill"></i> May include breaking changes';
    content.appendChild(warningNote);
  } else {
    const desc = document.createElement('div');
    desc.className = 'update-desc';
    desc.textContent = 'A newer version of Jeffrey is available.';
    content.appendChild(desc);
  }

  // Version cards
  const cards = document.createElement('div');
  cards.className = info.downloadUrl
    ? 'update-version-cards update-version-cards-with-download'
    : 'update-version-cards';

  const currentCard = document.createElement('div');
  currentCard.className = 'update-card update-card-current';
  currentCard.innerHTML =
    '<div class="update-card-label">Current</div>' +
    `<div class="update-card-version">${info.currentVersion}</div>`;

  const chevron = document.createElement('div');
  chevron.className = 'update-card-chevron';
  chevron.innerHTML = '<i class="bi bi-chevron-right"></i>';

  const latestCard = document.createElement('div');
  latestCard.className = `update-card ${isMajor ? 'update-card-latest-warning' : 'update-card-latest-info'}`;
  latestCard.innerHTML =
    '<div class="update-card-label">Latest</div>' +
    `<div class="update-card-version">${info.latestVersion}</div>`;

  cards.appendChild(currentCard);
  cards.appendChild(chevron);
  cards.appendChild(latestCard);

  if (info.downloadUrl) {
    const downloadBtn = document.createElement('a');
    downloadBtn.href = info.downloadUrl;
    downloadBtn.className = `update-download-btn ${isMajor ? 'update-download-btn-warning' : 'update-download-btn-info'}`;
    downloadBtn.title = 'Download jeffrey.jar';
    downloadBtn.innerHTML = '<i class="bi bi-download"></i>';
    cards.appendChild(downloadBtn);
  }

  content.appendChild(cards);

  // GitHub link
  const link = document.createElement('a');
  link.href = info.releaseUrl;
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.className = `update-link ${isMajor ? 'update-link-warning' : 'update-link-info'}`;
  link.innerHTML = '<i class="bi bi-box-arrow-up-right"></i> View release notes on GitHub';
  content.appendChild(link);

  toast.appendChild(content);

  return toast;
}

export function showUpdateCheckToast(info: UpdateCheckResponse): void {
  if (isDismissed(info.latestVersion)) {
    return;
  }

  const container = getOrCreateContainer();
  const toast = buildToast(info);
  container.appendChild(toast);

  const delay = info.majorUpdate ? WARNING_AUTO_HIDE_DELAY : INFO_AUTO_HIDE_DELAY;
  const timer = setTimeout(() => hideToast(toast), delay);

  // Allow close button to cancel auto-hide
  toast.querySelector('.toast-close')?.addEventListener('click', () => clearTimeout(timer));
}
