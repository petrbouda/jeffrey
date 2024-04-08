export default class Utils {

    static toTimeRange(start, end, absoluteTime) {
        return {
            start: this.#toMillisByTime(start),
            end: this.#toMillisByTime(end),
            absoluteTime: absoluteTime
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

    static formatBytes(bytes, decimals) {
        if(bytes === 0) return '0 Bytes';
        const k = 1024,
            dm = decimals || 2,
            sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'],
            i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    }

    static formatPercentage(value) {
        const percentage = (value * 100).toFixed(2)
        return percentage + "%"
    }
}
