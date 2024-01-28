import mitt from "mitt"
export default class MessageBus {

    static INSTANCE = mitt()

    static FLAMEGRAPH_CREATED = "flamegraph-created";
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
