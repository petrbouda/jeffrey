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
 * that ranks findings reads the same way.
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
