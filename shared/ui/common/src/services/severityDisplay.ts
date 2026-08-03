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

/** Severity as Jeffrey computes it from the measured profile — never graded by a model. */
export type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW";

/**
 * Presentation helpers for severity. Keeps the severity → look mapping in one place so every screen
 * that ranks recommendations reads the same way.
 */

const BADGE_VARIANT: Record<Severity, string> = {
  CRITICAL: "danger",
  HIGH: "orange",
  MEDIUM: "warning",
  LOW: "grey",
};

export function severityVariant(severity: Severity): string {
  return BADGE_VARIANT[severity] ?? "grey";
}

/**
 * Worst first. Kept here rather than in a view so every screen that orders recommendations agrees on what
 * "worse" means — an unknown severity sorts last rather than jumping the queue.
 */
const RANK: Record<Severity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

export function severityRank(severity: Severity): number {
  return RANK[severity] ?? Number.MAX_SAFE_INTEGER;
}

/** The severity as a word for running text, e.g. "High" — badges use the raw value. */
export function severityLabel(severity: Severity): string {
  return severity.charAt(0) + severity.slice(1).toLowerCase();
}
