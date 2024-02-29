
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

    static formatDateTime(dateTime) {
        const date = new Date(dateTime)
        const month =("0" + (date.getMonth() + 1)).slice(-2)
        return date.getFullYear() + "-" + month + "-" + date.getDate() + " " + date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds()
    }
}