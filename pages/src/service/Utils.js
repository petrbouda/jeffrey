export default class Utils {

    static toTimeRange(start, end) {
        return {
            start: this.#toMillisByTime(start),
            end: this.#toMillisByTime(end)
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
        const month = ("0" + (date.getMonth() + 1)).slice(-2)
        const day = ("0" + (date.getDate())).slice(-2)
        const hour = ("0" + (date.getHours())).slice(-2)
        const minute = ("0" + (date.getMinutes())).slice(-2)
        const second = ("0" + (date.getSeconds())).slice(-2)
        return date.getFullYear() + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + second
    }
}
