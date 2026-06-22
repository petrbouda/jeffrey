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

import SettingsClient from '@/services/api/SettingsClient';
import { markRestartRequired } from '@/stores/restartStore';

// Offer to enable Claude Code as the AI provider when its CLI is detected but no provider is configured.
// Mirrors UpdateCheckToast: a DOM toast in the shared #toast-container. Dismissal is kept in
// sessionStorage, so it stays hidden for the current browser session but re-appears in a new one.

const DISMISS_KEY = 'jeffrey-claude-code-enable-dismissed';
const ANTHROPIC_TOS_URL = 'https://www.anthropic.com/legal/consumer-terms';

const AI_CATEGORY = 'ai';
const PROVIDER_SETTING = 'jeffrey.microscope.ai.provider';
const PROVIDER_CLAUDE_CODE = 'claude-code';

function isDismissed(): boolean {
  return sessionStorage.getItem(DISMISS_KEY) === 'true';
}

function dismiss(): void {
  sessionStorage.setItem(DISMISS_KEY, 'true');
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

async function enable(toast: HTMLElement, action: HTMLElement): Promise<void> {
  action.innerHTML = '<span class="cc-enabling"><i class="bi bi-arrow-repeat cc-spin"></i> Enabling…</span>';
  try {
    await new SettingsClient().upsert(AI_CATEGORY, PROVIDER_SETTING, PROVIDER_CLAUDE_CODE, false);
    markRestartRequired();
    dismiss();
    // The header now shows the "restart to apply" indicator, so just close the toast.
    hideToast(toast);
  } catch {
    action.innerHTML =
      '<span class="cc-error"><i class="bi bi-exclamation-triangle-fill"></i> Could not enable. Try the Settings page.</span>';
  }
}

function buildToast(): HTMLElement {
  const toast = document.createElement('div');
  toast.className = 'toast cc-enable-toast';
  toast.setAttribute('role', 'alert');

  const accent = document.createElement('div');
  accent.className = 'toast-accent';
  toast.appendChild(accent);

  const content = document.createElement('div');
  content.className = 'toast-content';
  content.style.flexDirection = 'column';
  content.style.gap = '11px';

  // Title row: stars avatar + title
  const titleRow = document.createElement('div');
  titleRow.style.cssText = 'display:flex;align-items:center;gap:12px;width:100%';

  const stars = document.createElement('span');
  stars.className = 'cc-stars';
  stars.innerHTML = '<i class="bi bi-stars"></i>';
  titleRow.appendChild(stars);

  const titleEl = document.createElement('div');
  titleEl.className = 'toast-title';
  titleEl.style.flex = '1';
  titleEl.textContent = 'Claude Code detected';
  titleRow.appendChild(titleEl);
  content.appendChild(titleRow);

  // Description
  const desc = document.createElement('div');
  desc.className = 'cc-desc';
  desc.textContent =
    'AI-powered features are available on this machine. Enable Claude Code to turn them on.';
  content.appendChild(desc);

  // Subscription chip
  const chip = document.createElement('span');
  chip.className = 'cc-chip';
  chip.innerHTML = '<i class="bi bi-stars"></i> Uses your Claude subscription';
  content.appendChild(chip);

  // Action row: Enable (swapped in place for status) + Dismiss
  const foot = document.createElement('div');
  foot.className = 'cc-foot';

  const action = document.createElement('span');
  action.className = 'cc-action';
  const enableBtn = document.createElement('button');
  enableBtn.className = 'cc-btn';
  enableBtn.textContent = 'Enable Claude Code';
  enableBtn.addEventListener('click', () => enable(toast, action));
  action.appendChild(enableBtn);
  foot.appendChild(action);

  const dismissBtn = document.createElement('button');
  dismissBtn.className = 'cc-dismiss';
  dismissBtn.textContent = 'Dismiss';
  dismissBtn.addEventListener('click', () => {
    dismiss();
    hideToast(toast);
  });
  foot.appendChild(dismissBtn);
  content.appendChild(foot);

  // ToS footnote
  const tos = document.createElement('span');
  tos.className = 'cc-tos';
  tos.innerHTML =
    `By enabling you agree to <a href="${ANTHROPIC_TOS_URL}" target="_blank" rel="noopener noreferrer">Anthropic's Terms of Service</a>.`;
  content.appendChild(tos);

  toast.appendChild(content);
  return toast;
}

export function showClaudeCodeEnableToast(): void {
  if (isDismissed()) {
    return;
  }
  const container = getOrCreateContainer();
  // No auto-hide: this prompt stays until the user clicks Enable or Dismiss.
  container.appendChild(buildToast());
}
