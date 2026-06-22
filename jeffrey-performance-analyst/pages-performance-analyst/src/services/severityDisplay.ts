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

import type Severity from '@/services/api/model/Severity';

/**
 * Presentation helpers for recommendation severity: the Badge variant, a CSS-token color (for accent
 * rails / dots), an icon, and a sort rank. Keeps the severity → look mapping in one place so the
 * Overview list and the recordings panel stay consistent.
 */

const BADGE_VARIANT: Record<Severity, string> = {
  CRITICAL: 'danger',
  HIGH: 'orange',
  MEDIUM: 'warning',
  LOW: 'grey'
};

const COLOR_VAR: Record<Severity, string> = {
  CRITICAL: 'var(--color-danger)',
  HIGH: 'var(--color-orange)',
  MEDIUM: 'var(--color-amber)',
  LOW: 'var(--color-text-muted)'
};

const ICON: Record<Severity, string> = {
  CRITICAL: 'bi-exclamation-octagon-fill',
  HIGH: 'bi-exclamation-triangle-fill',
  MEDIUM: 'bi-dash-circle-fill',
  LOW: 'bi-info-circle-fill'
};

const RANK: Record<Severity, number> = { CRITICAL: 4, HIGH: 3, MEDIUM: 2, LOW: 1 };

export const SEVERITY_ORDER: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

export function severityVariant(severity: Severity): string {
  return BADGE_VARIANT[severity] ?? 'grey';
}

export function severityColor(severity: Severity): string {
  return COLOR_VAR[severity] ?? 'var(--color-text-muted)';
}

export function severityIcon(severity: Severity): string {
  return ICON[severity] ?? 'bi-info-circle-fill';
}

export function severityRank(severity: Severity): number {
  return RANK[severity] ?? 0;
}
