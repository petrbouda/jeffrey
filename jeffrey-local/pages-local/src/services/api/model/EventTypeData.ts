/**
 * Data associated with an event type
 */
export default class EventTypeData {
    constructor(
        /**
         * Categories this event type belongs to
         */
        public categories: string[],
        /**
         * Display name of the event type
         */
        public name: string,
        /**
         * Whether this is a leaf node
         */
        public leaf: boolean,
        /**
         * Event source information JDK / Async-Profiler
         */
        public source: string,
        /**
         * Event type code (e.g. "jdk.CPULoad")
         * Optional as category nodes might not have a code
         */
        public code?: string,
        /**
         * Number of occurrences of this event type
         * Optional as category nodes might not have a count
         */
        public count?: number,
        /**
         * Whether this event type includes stack trace information
         * Optional as category nodes might not have this flag
         */
        public withStackTrace?: boolean,
    ) {
    }
}
