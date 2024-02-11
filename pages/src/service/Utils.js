
export default class Utils{

    static toTimeRange(start, end) {
        return {
            start: this.#toMillisByTime(start),
            end : this.#toMillisByTime(end)
        }
    }

    static #toMillisByTime(time) {
        return this.#toMillis(time[0], time[1])
    }

    static #toMillis(seconds, millis) {
        return seconds * 1000 + millis
    }
}