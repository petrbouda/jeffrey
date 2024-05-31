import FormattingService from "@/service/FormattingService";
import EventTypes from "@/service/EventTypes";

export default class HeatmapTooltip {

    constructor(eventType, useWeight) {
        this.eventType = eventType;
        this.useWeight = useWeight;
    }

    generate(value, second, millis) {
        const valueDiv = this.#generateValue(value)

        return `
            <table>`
            + valueDiv +
            `<tr>
                    <th style="text-align: right">Second:</th>
                    <td>${second}<td>
                </tr>
                <tr>
                    <th style="text-align: right">Millis:</th>
                    <td>${millis}<td>
                </tr>
            </table>`
    }

    #generateValue(value) {
        if (EventTypes.isAllocationEventType(this.eventType) && this.useWeight) {
            return this.#allocSamplesWithWeight(value)
        } else if (EventTypes.isBlockingEventType(this.eventType) && this.useWeight) {
            return this.#blockSamplesWithWeight(value)
        } else {
            return this.#basicValue(value)
        }
    }

    #basicValue(value) {
        return `
            <tr>
                <th style="text-align: right">Samples:</th>
                <td>` + value + `<td>
            </tr>`;
    }

    #blockSamplesWithWeight(value) {
        return `
            <tr>
                <th style="text-align: right">Blocked Time:</th>
                <td>` + FormattingService.formatDuration(value) + `<td>
            </tr>`;
    }

    #allocSamplesWithWeight(value) {
        return `
            <tr>
                <th style="text-align: right">Allocated:</th>
                <td>` + FormattingService.formatBytes(value) + `<td>
            </tr>`;
    }
}
