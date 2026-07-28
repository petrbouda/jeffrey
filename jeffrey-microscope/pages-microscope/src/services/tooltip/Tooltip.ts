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

import TooltipPosition from './TooltipPosition';
import IdeJumpService from '@/services/IdeJumpService';
import IdeButtonGate from '@/services/IdeButtonGate';
import MessageBus from '@/services/MessageBus';
import router from '@/router';

export default class Tooltip {
  private static readonly HIDE_DELAY_MS = 600;

  /** Kept clear of the window edges so the tooltip never sits flush against them. */
  private static readonly VIEWPORT_MARGIN = 8;

  /** Distance between the pointer and the edge of the tooltip. */
  private static readonly CURSOR_GAP = 5;

  private tooltipTimeoutId: number | null = null;
  private hideTimeoutId: number | null = null;
  private displayedContent: string | null = null;
  private pendingContent: string | null = null;
  // Incremented every time gated IDE buttons are rendered; lets an in-flight class check ignore its
  // result if the tooltip has since moved to another frame.
  private ideGateToken = 0;
  private readonly canvas: HTMLElement;
  private readonly tooltipClassName: string;
  private readonly tooltip: HTMLElement;

  constructor(canvas: HTMLElement) {
    this.canvas = canvas;
    this.tooltipClassName = this.canvas.id + '-tooltip';
    this.tooltip = Tooltip.createTooltipDiv(this.canvas, this.tooltipClassName);
    this.registerInteractionHandlers();
  }

  public showTooltip(event: TooltipPosition, currentScrollY: number, content: string): void {
    this.cancelPendingHide();

    if (this.displayedContent === content) {
      return;
    }

    if (this.pendingContent === content) {
      return;
    }

    // Keep any currently-shown tooltip visible until the new content is ready, rather than blanking
    // it instantly. Otherwise moving the cursor toward the tooltip (crossing other frames) makes it
    // vanish before it can be hovered — which is what made the IDE buttons so hard to click.
    this.pendingContent = content;
    clearTimeout(this.tooltipTimeoutId as number);
    this.tooltipTimeoutId = window.setTimeout(() => {
      this.tooltip.innerHTML = content;
      this.displayedContent = content;
      this.pendingContent = null;
      Tooltip.placeTooltip(this.canvas, this.tooltip, event, currentScrollY);
      this.applyIdeGate();
    }, 500);
  }

  /**
   * JFR Profiler Plugin mode renders the IDE buttons disabled ({@code data-ide-gated}); enable them
   * once the IDE confirms it contains the frame's class. Guarded by a token so a result arriving after
   * the cursor moved to another frame cannot enable the wrong tooltip's buttons.
   */
  private applyIdeGate(): void {
    const gated = Array.from(
      this.tooltip.querySelectorAll('[data-ide-action][data-ide-gated="true"]')
    ) as HTMLButtonElement[];
    if (gated.length === 0) {
      return;
    }
    const fqn = gated[0].getAttribute('data-fqn') ?? '';
    if (!fqn) {
      return;
    }
    const profileId = (router.currentRoute.value.params.profileId as string) ?? '';
    const token = ++this.ideGateToken;
    IdeButtonGate.check(profileId, fqn).then(present => {
      if (token !== this.ideGateToken || !present) {
        return;
      }
      const current = Array.from(
        this.tooltip.querySelectorAll('[data-ide-action][data-ide-gated="true"]')
      ) as HTMLButtonElement[];
      for (const button of current) {
        if (button.getAttribute('data-fqn') === fqn) {
          button.removeAttribute('disabled');
        }
      }
    });
  }

  public hideTooltip(): void {
    clearTimeout(this.tooltipTimeoutId as number);
    this.pendingContent = null;
    this.cancelPendingHide();
    this.hideTimeoutId = window.setTimeout(() => {
      this.tooltip.style.visibility = 'hidden';
      this.displayedContent = null;
    }, Tooltip.HIDE_DELAY_MS);
  }

  private cancelPendingHide(): void {
    if (this.hideTimeoutId !== null) {
      clearTimeout(this.hideTimeoutId);
      this.hideTimeoutId = null;
    }
  }

  private registerInteractionHandlers(): void {
    this.tooltip.addEventListener('mouseenter', () => {
      this.cancelPendingHide();
      // Once the cursor is on the tooltip, don't let a queued new-frame tooltip replace it.
      clearTimeout(this.tooltipTimeoutId as number);
      this.pendingContent = null;
    });
    this.tooltip.addEventListener('mouseleave', () => {
      this.tooltip.style.visibility = 'hidden';
      this.displayedContent = null;
    });

    this.tooltip.addEventListener('click', event => {
      const target = (event.target as HTMLElement | null)?.closest(
        '[data-ide-action]'
      ) as HTMLElement | null;
      if (!target) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();

      const action = target.getAttribute('data-ide-action');
      const fqn = target.getAttribute('data-fqn') ?? '';
      const method = target.getAttribute('data-method') ?? '';
      const lineAttr = target.getAttribute('data-line');
      const parsedLine = lineAttr ? parseInt(lineAttr, 10) : -1;
      const line = Number.isNaN(parsedLine) ? -1 : parsedLine;

      const profileId = (router.currentRoute.value.params.profileId as string) ?? '';
      if (action === 'open') {
        IdeJumpService.openInIde(profileId, fqn, method, line);
      } else if (action === 'source') {
        const title = target.getAttribute('data-title') ?? '';
        MessageBus.emit(MessageBus.IDE_VIEW_SOURCE, { profileId, fqn, method, line, title });
      }

      // Hide the tooltip so it does not linger behind the modal / IDE focus shift.
      this.tooltip.style.visibility = 'hidden';
      this.displayedContent = null;
    });
  }

  /**
   * Places the tooltip beside the pointer, wholly inside the window.
   *
   * <p>The content has no fixed size: a pointer resting where several event categories overlap
   * produces a box listing every one of them with its fields. Such a box does not fit above the
   * pointer, and moving it there regardless put its top outside the card — which clips its overflow
   * — leaving the part that mattered unreachable. So the height is capped first, and the side is
   * chosen by what actually fits.
   *
   * <p>Two coordinate systems meet here. The tooltip is positioned against the canvas's offset
   * parent, while the room available for it is measured in the window. They differ by a constant,
   * computed once as {@code layoutOffset}: the fitting is done in window coordinates and shifted
   * into layout coordinates at the end.
   */
  private static placeTooltip(
    canvas: HTMLElement,
    tooltip: HTMLElement,
    position: TooltipPosition,
    currentScrollY: number
  ): void {
    const viewportHeight = window.innerHeight;
    const viewportWidth = window.innerWidth;
    const canvasPos = canvas.getBoundingClientRect();

    // Capped before measuring: a box taller than the window has no correct placement at all, and
    // scrolling its content is the only way to keep the rest of it reachable.
    tooltip.style.maxHeight = viewportHeight - 2 * Tooltip.VIEWPORT_MARGIN + 'px';
    tooltip.style.overflowY = 'auto';

    const layoutOffsetY = canvas.offsetTop - currentScrollY - canvasPos.y;
    const pointerY = canvasPos.y + position.offsetY;
    const height = tooltip.offsetHeight;

    // Above is preferred — it leaves the hovered lane visible — but only when it fits there.
    let top: number;
    if (height + Tooltip.CURSOR_GAP <= pointerY - Tooltip.VIEWPORT_MARGIN) {
      top = pointerY - height - Tooltip.CURSOR_GAP;
    } else if (height + Tooltip.CURSOR_GAP <= viewportHeight - pointerY - Tooltip.VIEWPORT_MARGIN) {
      top = pointerY + Tooltip.CURSOR_GAP;
    } else {
      top = Tooltip.VIEWPORT_MARGIN;
    }

    // Flooring the layout value at 0 as well: the timeline nests its canvases in cards that clip
    // their overflow, which the window clamp on its own does nothing about.
    const clampedTop = Tooltip.clamp(
      top,
      Tooltip.VIEWPORT_MARGIN,
      viewportHeight - height - Tooltip.VIEWPORT_MARGIN
    );
    tooltip.style.top = Math.max(clampedTop + layoutOffsetY, 0) + 'px';

    const layoutOffsetX = canvas.offsetLeft - canvasPos.x;
    const pointerX = canvasPos.x + position.offsetX;
    const width = tooltip.offsetWidth;

    const left =
      position.offsetX > canvas.offsetWidth / 2
        ? pointerX - width - Tooltip.CURSOR_GAP
        : pointerX + Tooltip.CURSOR_GAP;

    const clampedLeft = Tooltip.clamp(
      left,
      Tooltip.VIEWPORT_MARGIN,
      viewportWidth - width - Tooltip.VIEWPORT_MARGIN
    );
    tooltip.style.left = clampedLeft + layoutOffsetX + 'px';

    tooltip.style.visibility = 'visible';
  }

  /**
   * Clamps to {@code [min, max]}, preferring {@code min} when the two cross — which happens when the
   * tooltip is larger than the space it has to sit in.
   */
  private static clamp(value: number, min: number, max: number): number {
    return Math.max(min, Math.min(value, Math.max(max, min)));
  }

  private static createTooltipDiv(canvas: HTMLElement, threadTooltipName: string): HTMLElement {
    const divContent =
      '<div class="' +
      threadTooltipName +
      ' shadow"' +
      ' style="visibility:hidden; z-index: 1030; position:absolute; min-width: 280px; max-width: 400px; font-size: 13px; border-radius: 0; overflow: hidden; background: #fff; border: 1px solid #eaedf1;"/>';
    const element = Tooltip.createElementFromHTML(divContent);
    return canvas.insertAdjacentElement('afterend', element) as HTMLElement;
  }

  private static createElementFromHTML(htmlString: string): HTMLElement {
    const div = document.createElement('div');
    div.innerHTML = htmlString.trim();
    return div.firstChild as HTMLElement;
  }
}
