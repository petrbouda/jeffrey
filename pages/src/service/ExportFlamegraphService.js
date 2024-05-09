import FlamegraphService from "@/service/FlamegraphService";

export default class ExportFlamegraphService {

    static exportFlamegraph(primaryProfileId, useThreadMode, eventType, timeRange) {
        if (eventType != null && timeRange != null) {
            return FlamegraphService.exportEventTypeRange(primaryProfileId, eventType, timeRange, useThreadMode);
        } else if (eventType != null) {
            return FlamegraphService.exportEventTypeComplete(primaryProfileId, eventType, useThreadMode);
        }
    }

    static formatPercentage(value) {
        const percentage = (value * 100).toFixed(2)
        return percentage + "%"
    }
}
