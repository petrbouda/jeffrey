export default class FormattingService {

    static UNITS = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB']

    static formatBytes(bytes){
        if (bytes === 0) {
            return "0.00 B";
        }

        let e = Math.floor(Math.log(bytes) / Math.log(1024));
        return (bytes / Math.pow(1024, e)).toFixed(2) + ' ' + FormattingService.UNITS[e];
    }

    static formatPercentage(value) {
        const percentage = (value * 100).toFixed(2)
        return percentage + "%"
    }

    static formatDuration(nanos) {
        let us = nanos / 1000;
        if (us < 0) us = -us;
        const time = {
            d: Math.floor(us / 86_400_000_000),
            h: Math.floor(us / 3_600_000_000) % 24,
            m: Math.floor(us / 60_000_000) % 60,
            s: Math.floor(us / 1_000_000) % 60,
            ms: Math.floor(us / 1_000) % 1_000,
            us: Math.floor(us) % 1_000
        };
        return Object.entries(time)
            .filter(val => val[1] !== 0)
            .map(([key, val]) => `${val}${key}`)
            .join(' ');
    };
}
