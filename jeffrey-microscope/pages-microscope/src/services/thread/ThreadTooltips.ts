/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import EventMetadata from '@/services/api/model/EventMetadata';
import FormattingService from '@shared/services/FormattingService';
import { escapeAttr, escapeHtml } from '@shared/services/HtmlEscape';

/**
 * What is known about one category at the hovered position, once the lookup for it has been given a
 * chance to land. Everything a tooltip renders is derived from this, so the states a lookup can
 * actually be in are spelled out rather than inferred from undefined-ness at render time.
 */
export type CategoryTooltip =
  /** The lookup has not come back yet. */
  | { kind: 'pending' }
  /** The lookup failed and will not be retried. */
  | { kind: 'failed' }
  /** Events start inside the hovered window; `values` fill in separately from the count. */
  | { kind: 'events'; count: number; windowNanos: number; values: Array<string> | undefined }
  /** Nothing starts in the window, but an event that began earlier is still running through it. */
  | { kind: 'ongoing'; startOffset: number; values: Array<string> }
  /** Nothing starts in the window and nothing spans it either. */
  | { kind: 'empty'; windowNanos: number };

export default class ThreadTooltips {
  static header(javaName: string): string {
    return `
            <div class="tooltip-header p-2 border-bottom">
                <h5 class="m-0 text-dark font-weight-bold">${escapeHtml(javaName)}</h5>
            </div>`;
  }

  /**
   * Renders one category of the tooltip.
   *
   * Everything interpolated here is escaped: the labels come from the recording's event metadata and
   * the values from the events themselves — remote hosts, addresses and file paths — and this builds
   * markup as a string rather than going through Vue.
   */
  static basic(metadata: EventMetadata, colorRgb: string, tooltip: CategoryTooltip): string {
    return `
            ${ThreadTooltips.divider(metadata.label, colorRgb, tooltip)}
            <div class="tooltip-content">
                ${ThreadTooltips.body(metadata, tooltip)}
            </div>`;
  }

  private static body(metadata: EventMetadata, tooltip: CategoryTooltip): string {
    if (tooltip.kind === 'empty') {
      return ThreadTooltips.note(
        `No event starts in this ${FormattingService.formatDuration2Units(tooltip.windowNanos)} window`
      );
    }

    const values = ThreadTooltips.valuesOf(tooltip);

    let fields = '';
    if (tooltip.kind === 'ongoing') {
      fields += ThreadTooltips.note(
        `Still running — started at ${FormattingService.formatDuration2Units(tooltip.startOffset)}`
      );
    }

    metadata.fields.forEach((threadField, index) => {
      const value =
        values === undefined
          ? '<span class="text-muted">…</span>'
          : ThreadTooltips.formatValue(values[index], threadField.type);

      fields += `
                <div class="tooltip-row d-flex px-2 py-1">
                    <span class="field-name text-secondary font-weight-medium">${escapeHtml(threadField.name)}:</span>
                    <span class="field-value text-dark">${value}</span>
                </div>`;
    });

    return fields;
  }

  /**
   * A field the recording did not capture renders as a dash rather than as the word "undefined" —
   * the values are positional, so a missing one is a hole in the middle of the list, not the end
   * of it.
   */
  private static formatValue(value: string | undefined, type: string): string {
    if (value === undefined || value === null) {
      return '-';
    }
    return escapeHtml(String(FormattingService.format(value, type)));
  }

  private static valuesOf(tooltip: CategoryTooltip): Array<string> | undefined {
    if (tooltip.kind === 'events') {
      return tooltip.values;
    }
    if (tooltip.kind === 'ongoing') {
      return tooltip.values;
    }
    return undefined;
  }

  /**
   * The count as it stands. A pending lookup shows the same ellipsis the field rows use rather than
   * a number that would have to be corrected — the band's own total is a whole run of activity, so
   * showing it first would flash a number orders of magnitude too large.
   */
  private static countFragment(tooltip: CategoryTooltip): string {
    switch (tooltip.kind) {
      case 'pending':
        return '<span class="event-count text-muted small">…</span>';
      case 'failed':
        return '<span class="event-count text-muted small">—</span>';
      case 'ongoing':
        return '<span class="event-count text-muted small">in progress</span>';
      case 'empty':
        return '<span class="event-count text-muted small">no events start here</span>';
      case 'events': {
        const label = `${tooltip.count.toLocaleString()} event${tooltip.count !== 1 ? 's' : ''}`;
        const window = FormattingService.formatDuration2Units(tooltip.windowNanos);
        const title = escapeAttr(
          `Events starting in the ${window} of recording time under the pointer`
        );
        return `<span class="event-count text-muted small" title="${title}">${escapeHtml(label)} · ${escapeHtml(window)}</span>`;
      }
    }
  }

  private static note(text: string): string {
    return `
                <div class="tooltip-row d-flex px-2 py-1">
                    <span class="field-value text-muted fst-italic">${escapeHtml(text)}</span>
                </div>`;
  }

  private static divider(text: string, colorRgb: string, tooltip: CategoryTooltip): string {
    return `<div class="tooltip-category d-flex align-items-center px-2 py-1 bg-light">
                    <div class="color-indicator mr-2" style="width: 10px; height: 10px; border-radius: 2px; background-color: ${escapeAttr(colorRgb)}"></div>
                    <div class="d-flex justify-content-between w-100">
                        <span class="category-name font-weight-medium">${escapeHtml(text)}</span>
                        ${ThreadTooltips.countFragment(tooltip)}
                    </div>
                </div>`;
  }
}
