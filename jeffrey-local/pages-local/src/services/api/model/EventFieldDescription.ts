export default class EventFieldDescription {
    constructor(
        public field: string,
        public header: string,
        public type?: string,
        public description?: string
    ) {
    }
}
