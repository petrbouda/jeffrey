import EventTypes from "@/service/EventTypes";
import FormattingService from "@/service/FormattingService";

export default class TimeseriesEventAxeFormatter {

    static resolveFormatter(eventTypeCode) {
        if (EventTypes.isBlockingEventType(eventTypeCode)) {
            return FormattingService.formatDuration
        } else {
            return FormattingService.formatBytes
        }
    }
}
