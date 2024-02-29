import mitt from "mitt"
export default class MessageBus {

    static INSTANCE = mitt()

    static PROFILE_CARD_TOGGLE = "profile-card-toggle";
    static FLAMEGRAPH_CREATED = "flamegraph-created";
    static FLAMEGRAPH_EVENT_TYPE_CHANGED = "flamegraph-event-type-changed";
    static FLAMEGRAPH_TIMESERIES_RANGE_CHANGED = "flamegraph-timeseries-range-changed";
    static PRIMARY_FLAMEGRAPH_CREATED = "primary-flamegraph-created";
    static SECONDARY_FLAMEGRAPH_CREATED = "secondary-flamegraph-created";

    static emit(type, content) {
        this.INSTANCE.emit(type, content)
    }

    static on(type, handler) {
        this.INSTANCE.on(type, handler)
    }

    static off(type) {
        this.INSTANCE.off(type)
    }
}
