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

/**
 * Renders the pinned "Σ per bucket" bar profile for the SubSecond heatmap: one horizontal bar per
 * 20 ms bucket, whose length is the sum of that bucket across all seconds. It lives to the LEFT of,
 * and outside, the horizontally-scrolling heatmap so it stays visible. The colorbar legend lives
 * below the heatmap (see SubSecondComponent), not here.
 *
 * Vertical alignment is derived from the rendered ApexCharts cells (their `rect[i][j]` nodes),
 * so the bars match the heatmap rows exactly regardless of ApexCharts' internal padding.
 */
export const MARGINAL_WIDTH = 64;

const CELL_COLOR_FALLBACK = '#0022ff';
const BAR_ALPHA = 0.55;
const MAX_GEOMETRY_RETRIES = 20;
const GEOMETRY_RETRY_DELAY_MS = 80;

export default class MarginalRenderer {
  private readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D;
  private readonly heatmapContainerId: string;

  private rowSums: number[] = [];
  private rowMax = 0;
  private retryTimer: number | null = null;

  constructor(canvas: HTMLCanvasElement, heatmapContainerId: string) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d')!;
    this.heatmapContainerId = heatmapContainerId;
  }

  width(): number {
    return MARGINAL_WIDTH;
  }

  render(rowSums: number[]) {
    this.rowSums = rowSums;
    this.rowMax = rowSums.reduce((max, value) => Math.max(max, value), 0);
    this.#draw();
  }

  redraw() {
    this.#draw();
  }

  destroy() {
    if (this.retryTimer != null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  #draw() {
    if (this.retryTimer != null) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
    if (this.rowSums.length === 0) {
      return;
    }
    this.#drawWithRetry(0);
  }

  #drawWithRetry(attempt: number) {
    const rowCount = this.rowSums.length;
    const bottomRect = this.#cellRect(0);
    const topRect = this.#cellRect(rowCount - 1);
    if (bottomRect == null || topRect == null) {
      if (attempt < MAX_GEOMETRY_RETRIES) {
        this.retryTimer = window.setTimeout(
          () => this.#drawWithRetry(attempt + 1),
          GEOMETRY_RETRY_DELAY_MS
        );
      }
      return;
    }

    const cssWidth = this.width();
    const container = document.getElementById(this.heatmapContainerId);
    const cssHeight = container ? container.clientHeight : Math.round(bottomRect.bottom - topRect.top) + 40;

    const dpr = Math.min(2, window.devicePixelRatio || 1);
    this.canvas.style.width = cssWidth + 'px';
    this.canvas.style.height = cssHeight + 'px';
    this.canvas.width = Math.round(cssWidth * dpr);
    this.canvas.height = Math.round(cssHeight * dpr);
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.ctx.clearRect(0, 0, cssWidth, cssHeight);

    const canvasRect = this.canvas.getBoundingClientRect();
    const plotTop = topRect.top - canvasRect.top;
    const plotBottom = bottomRect.bottom - canvasRect.top;
    this.#paint(cssWidth, plotTop, plotBottom, rowCount);
  }

  #cellRect(rowIndex: number): DOMRect | null {
    const node = document.querySelector(
      '#' + this.heatmapContainerId + ' rect[i="' + rowIndex + '"][j="0"]'
    );
    return node ? node.getBoundingClientRect() : null;
  }

  #paint(width: number, plotTop: number, plotBottom: number, rowCount: number) {
    if (this.rowMax <= 0) {
      return;
    }
    const ctx = this.ctx;
    const cell = this.#css('--color-subsecond-cell', CELL_COLOR_FALLBACK);
    const barFill = this.#hexA(cell, BAR_ALPHA);
    const rowHeight = (plotBottom - plotTop) / rowCount;
    const barBase = width - 4; // bars grow left from the heatmap edge
    const barSpace = barBase - 4;

    ctx.fillStyle = barFill;
    for (let r = 0; r < rowCount; r++) {
      const barWidth = (this.rowSums[r] / this.rowMax) * barSpace;
      const y = plotBottom - (r + 1) * rowHeight;
      ctx.fillRect(barBase - barWidth, y + 0.5, Math.max(0, barWidth), Math.max(0.5, rowHeight - 1));
    }
  }

  #css(name: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
  }

  #hexA(hex: string, alpha: number): string {
    let normalized = hex.replace('#', '');
    if (normalized.length === 3) {
      normalized = normalized
        .split('')
        .map(ch => ch + ch)
        .join('');
    }
    const num = parseInt(normalized, 16);
    return 'rgba(' + ((num >> 16) & 255) + ',' + ((num >> 8) & 255) + ',' + (num & 255) + ',' + alpha + ')';
  }
}
