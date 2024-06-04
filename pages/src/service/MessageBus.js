import mitt from "mitt"
export default class MessageBus {

    static INSTANCE = mitt()

    static PROFILE_DIALOG_TOGGLE = "profile-dialog-toggle";
    static PROFILE_CARD_TOGGLE = "profile-card-toggle";
    static FLAMEGRAPH_CREATED = "flamegraph-created";
    static FLAMEGRAPH_CHANGED = "flamegraph-timeseries-range-changed";
    static FLAMEGRAPH_SEARCH = "flamegraph-search";
    static TIMESERIES_SEARCH = "timeseries-search"
    static TIMESERIES_RESET_SEARCH = "timeseries-reset-search"

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
