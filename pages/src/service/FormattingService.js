export default class FormattingService {
    static formatBytes(bytes, decimals = 2) {
        if (!+bytes) return '0 Bytes';
        if (bytes < 0) return bytes;

        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
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
