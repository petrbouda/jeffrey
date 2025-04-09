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

import TooltipPosition from "./TooltipPosition";

export default class Tooltip {

    private tooltipTimeoutId: number | null = null;
    private readonly canvas: HTMLElement;
    private readonly tooltipClassName: string;
    private readonly tooltip: HTMLElement;

    constructor(canvas: HTMLElement) {
        this.canvas = canvas;
        this.tooltipClassName = this.canvas.id + "-tooltip";
        this.tooltip = Tooltip.createTooltipDiv(this.canvas, this.tooltipClassName);
    }

    public showTooltip(event: TooltipPosition, currentScrollY: number, content: string): void {
        this.tooltip.style.visibility = 'hidden';

        clearTimeout(this.tooltipTimeoutId as number);
        this.tooltipTimeoutId = window.setTimeout(() => {
            this.tooltip.innerHTML = content;
            Tooltip.placeTooltip(this.canvas, this.tooltip, event, currentScrollY);
        }, 500);
    }

    public hideTooltip(): void {
        this.tooltip.style.visibility = 'hidden';
        clearTimeout(this.tooltipTimeoutId as number);
    }

    private static placeTooltip(
        canvas: HTMLElement, tooltip: HTMLElement, position: TooltipPosition, currentScrollY: number): void {

        const currWindowHeight = window.innerHeight;
        const canvasPos = canvas.getBoundingClientRect();

        if ((canvasPos.y + position.offsetY) > (currWindowHeight / 2)) {
            tooltip.style.top = (canvas.offsetTop - currentScrollY + position.offsetY - tooltip.offsetHeight + 5) + 'px';
        } else {
            tooltip.style.top = (canvas.offsetTop - currentScrollY + position.offsetY + 5) + 'px';
        }

        if (position.offsetX > (canvas.offsetWidth / 2)) {
            tooltip.style.left = (canvas.offsetLeft + position.offsetX - tooltip.offsetWidth - 5) + 'px';
        } else {
            tooltip.style.left = (canvas.offsetLeft + position.offsetX + 5) + 'px';
        }

        tooltip.style.visibility = 'visible';
    }

    private static createTooltipDiv(canvas: HTMLElement, threadTooltipName: string): HTMLElement {
        const divContent = '<div class="' + threadTooltipName + ' card shadow"' +
            ' style="visibility:hidden; z-index: 1030; position:absolute; min-width: 280px; max-width: 500px; font-size: 90%; border-radius: 0.5rem; overflow: hidden;"/>';
        const element = Tooltip.createElementFromHTML(divContent);
        return canvas.insertAdjacentElement('afterend', element) as HTMLElement;
    }

    private static createElementFromHTML(htmlString: string): HTMLElement {
        const div = document.createElement('div');
        div.innerHTML = htmlString.trim();
        return div.firstChild as HTMLElement;
    }
}
