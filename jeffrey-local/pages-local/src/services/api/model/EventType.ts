import EventTypeData from "@/services/viewer/model/EventTypeData.ts";

/**
 * Represents an event type in the viewer
 */
export default class EventType {

    constructor(
        /**
         * Unique identifier for the node
         */
        public key: string,
        /**
         * Data object containing event type information
         */
        public data: EventTypeData,
        /**
         * Child nodes in the hierarchy
         */
        public children: EventType[],
        /**
         * Whether this node is a leaf node with no children
         */
        public leaf: boolean
    ) {
    }
}
