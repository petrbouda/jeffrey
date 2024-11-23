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

export default class Tooltip {

    tooltipTimeoutId = null

    constructor(canvas) {
        this.canvas = canvas;
        this.tooltipClassName = this.canvas.id + "-tooltip"
        this.tooltip = Tooltip.#createTooltipDiv(this.canvas, this.tooltipClassName);
    }

    showTooltip(event, currentScrollY, content) {
        this.tooltip.style.visibility = 'hidden';

        clearTimeout(this.tooltipTimeoutId)
        this.tooltipTimeoutId = setTimeout(() => {
            this.tooltip.innerHTML = content
            Tooltip.#placeTooltip(this.canvas, this.tooltip, event, currentScrollY)
        }, 500);
    }

    hideTooltip() {
        this.tooltip.style.visibility = 'hidden';
        clearTimeout(this.tooltipTimeoutId)
    }

    static #placeTooltip(canvas, tooltip, event, currentScrollY) {
        if (event.offsetY > (canvas.offsetHeight / 2)) {
            tooltip.style.top = (canvas.offsetTop - currentScrollY + event.offsetY - tooltip.offsetHeight + 5) + 'px';
        } else {
            tooltip.style.top = (canvas.offsetTop - currentScrollY + event.offsetY + 5) + 'px';
        }

        // Placing of the tooltip based on the canvas middle position
        if (event.offsetX > (canvas.offsetWidth / 2)) {
            tooltip.style.left = (canvas.offsetLeft + event.offsetX - tooltip.offsetWidth - 5) + 'px';
        } else {
            tooltip.style.left = (canvas.offsetLeft + event.offsetX + 5) + 'px';
        }

        tooltip.style.visibility = 'visible';
    }

    static #createTooltipDiv(canvas, threadTooltipName) {
        const divContent = '<div class="' + threadTooltipName + ' card p-2 border-1 bg-gray-50"' +
            ' style="visibility:hidden; z-index: 10; position:absolute"/>'

        const element = Tooltip.#createElementFromHTML(divContent)
        return canvas.insertAdjacentElement('afterend', element)
    }

    static #createElementFromHTML(htmlString) {
        var div = document.createElement('div');
        div.innerHTML = htmlString.trim();

        // Change this to div.childNodes to support multiple top-level nodes.
        return div.firstChild;
    }
}
