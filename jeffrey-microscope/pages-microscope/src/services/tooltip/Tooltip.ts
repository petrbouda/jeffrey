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
    IdeButtonGate.check(profileId, fqn).then((present) => {
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

    this.tooltip.addEventListener('click', (event) => {
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

  private static placeTooltip(
    canvas: HTMLElement,
    tooltip: HTMLElement,
    position: TooltipPosition,
    currentScrollY: number
  ): void {
    const currWindowHeight = window.innerHeight;
    const canvasPos = canvas.getBoundingClientRect();

    if (canvasPos.y + position.offsetY > currWindowHeight / 2) {
      tooltip.style.top =
        canvas.offsetTop - currentScrollY + position.offsetY - tooltip.offsetHeight + 5 + 'px';
    } else {
      tooltip.style.top = canvas.offsetTop - currentScrollY + position.offsetY + 5 + 'px';
    }

    if (position.offsetX > canvas.offsetWidth / 2) {
      tooltip.style.left = canvas.offsetLeft + position.offsetX - tooltip.offsetWidth - 5 + 'px';
    } else {
      tooltip.style.left = canvas.offsetLeft + position.offsetX + 5 + 'px';
    }

    tooltip.style.visibility = 'visible';
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
