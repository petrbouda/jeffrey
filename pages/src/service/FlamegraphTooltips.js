import Flamegraph from "@/service/Flamegraph";
import FormattingService from "@/service/FormattingService";

export default class FlamegraphTooltips {

    static BASIC = "basic"
    static CPU = "cpu"
    static TLAB_ALLOC = "tlab_alloc"
    static DIFF = "diff"

    static SAMPLE_TYPE_MAPPING = []
    static {
        FlamegraphTooltips.SAMPLE_TYPE_MAPPING["jit"] = "JIT-compiled"
        FlamegraphTooltips.SAMPLE_TYPE_MAPPING["interpret"] = "Interpreted"
        FlamegraphTooltips.SAMPLE_TYPE_MAPPING["c1"] = "C1-compiled"
        FlamegraphTooltips.SAMPLE_TYPE_MAPPING["inlined"] = "Inlined"
    }

    static FRAME_TYPE_MAPPING = []
    static {
        FlamegraphTooltips.FRAME_TYPE_MAPPING["C1_COMPILED"] = "JAVA C1-compiled"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["NATIVE"] = "Native"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["CPP"] = "C++ (JVM)"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["INTERPRETED"] = "Interpreted (JAVA)"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["JIT_COMPILED"] = "JIT-compiled (JAVA)"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["KERNEL"] = "Kernel"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["INLINED"] = "Inlined (JAVA)"
        FlamegraphTooltips.FRAME_TYPE_MAPPING["UNKNOWN"] = "Unknown"
    }

    static generateTooltip(type, frame, levelTotal) {
        if (type === FlamegraphTooltips.CPU) {
            return FlamegraphTooltips.cpu(frame, levelTotal)
        } else if (type === FlamegraphTooltips.TLAB_ALLOC) {
            return FlamegraphTooltips.tlabAlloc(frame, levelTotal)
        } else if (type === FlamegraphTooltips.DIFF) {
            return FlamegraphTooltips.diff(frame, levelTotal)
        } else if (type === FlamegraphTooltips.BASIC) {
            return FlamegraphTooltips.basic(frame, levelTotal)
        }
    }

    static cpu(frame, levelTotal) {
        let entity = FlamegraphTooltips.basic(frame, levelTotal)
        entity = entity + FlamegraphTooltips.#position(frame.position)
        entity = entity + FlamegraphTooltips.#frame_types(frame.sampleTypes)
        return entity
    }

    static tlabAlloc(frame, levelTotal) {
        let selfSamplesFragment = ""
        if (frame.selfSamples !== 0) {
            selfSamplesFragment = `<tr>
                <th class="text-right">Samples (self):</th>
                <td>${FlamegraphTooltips.#format_samples(frame.selfSamples, frame.totalSamples)}<td>
            </tr>`
        }

        let selfWeightFragment = ""
        if (frame.selfWeight !== 0) {
            selfWeightFragment = `<tr>
                <th class="text-right">Allocated (self):</th>
                <td>${FormattingService.formatBytes(frame.selfWeight)}<td>
            </tr>`
        }

        let entity = `<div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${frame.title}</div>
            <hr>
            <table class="pl-1 pr-1 text-sm">
                <tr>
                    <th class="text-right">Samples (total):</th>
                    <td>${FlamegraphTooltips.#format_samples(frame.totalSamples, levelTotal)}<td>
                </tr>
                ${selfSamplesFragment}
                <tr>
                    <th class="text-right">Allocated (total):</th>
                    <td>${FormattingService.formatBytes(frame.totalWeight)}<td>
                </tr>
                ${selfWeightFragment}
            </table>`
        return entity
    }

    static diff(frame, levelTotal) {
        let diffFragment = ""
        let details = frame.details;
        if (details.samples > 0) {
            diffFragment = diffFragment + `<tr>
                <th class="text-right text-red-500">Added:</th>
                <td>${details.samples} (${details.percent}%)<td>
            </tr>`
        } else if (details.samples < 0) {
            diffFragment = diffFragment + `<tr>
                <th class="text-right text-green-500">Removed:</th>
                <td>${details.samples} (${details.percent}%)<td>
            </tr>`
        } else {
            diffFragment = diffFragment + `There is no difference in samples`
        }

        let entity = `<div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${frame.title}</div>
           <hr>
        <table class="pl-1 pr-1 text-sm">
            <tr>
                <th class="text-right">Total:</th>
                <td>${FlamegraphTooltips.#format_samples(frame.totalSamples, levelTotal)}<td>
            </tr>
            ${diffFragment}
        </table>`

        return entity
    }

    static basic(frame, levelTotal) {
        let typeFragment = ""
        if (frame.type != null) {
            typeFragment = `<tr>
                <th class="text-right">Frame Type:</th>
                <td>${FlamegraphTooltips.FRAME_TYPE_MAPPING[frame.type]}<td>
            </tr>`
        }

        let selfFragment = ""
        if (frame.self != null) {
            selfFragment = `<tr>
                <th class="text-right">Self:</th>
                <td>${FlamegraphTooltips.#format_samples(frame.self, frame.total)}<td>
            </tr>`
        }

        return `
            <div style="color: black" class="w-full text-center p-1 pl-2 pr-2 text-sm font-bold">${frame.title}</div>
            <hr>
            <table class="pl-1 pr-1 text-sm">
                ${typeFragment}
                <tr>
                    <th class="text-right">Samples (total):</th>
                    <td>${FlamegraphTooltips.#format_samples(frame.totalSamples, levelTotal)}<td>
                </tr>
                <tr>
                    <th class="text-right">Samples (self):</th>
                    <td>${FlamegraphTooltips.#format_samples(frame.selfSamples, frame.totalSamples)}<td>
                </tr>
                ${selfFragment}
            </table>`
    }

    /**
     * Frame types are sorted and printed.
     */
    static #frame_types(types) {
        if (types == null) {
            return ""
        }

        let entity = `${FlamegraphTooltips.#divider("All Frame Types")}<table class="pl-1 pr-1 text-sm">`
        let sortable = [];
        for (const type in types) {
            sortable.push([type, types[type]]);
        }
        sortable.sort(function (a, b) {
            return b[1] - a[1];
        });
        sortable.forEach(function (key) {
            entity = entity + `<tr>
                <th class="text-right">${FlamegraphTooltips.SAMPLE_TYPE_MAPPING[key[0]]}:</th>
                <td>${key[1]}<td>
            </tr>`
        })
        return entity + '</table>'
    }

    static #position(position) {
        if (position == null) {
            return ""
        }

        return `
            ${FlamegraphTooltips.#divider("Positioning")}
            <table class="pl-1 pr-1 text-sm">
                <tr>
                    <th class="text-right">Bytecode (bci):</th>
                    <td>${position.bci}<td>
                </tr>
                <tr>
                    <th class="text-right">Line number:</th>
                    <td>${position.line}<td>
                </tr>
            </table>`
    }

    static #divider(text) {
        return `<div class="m-2 ml-4 italic text-gray-500 text-sm">${text}</div>`
    }

    static #format_samples(value, base) {
        return value + ' (' + FlamegraphTooltips.#pct(value, base) + '%)'
    }

    static #pct(a, b) {
        return a >= b ? '100' : ((100 * a) / b).toFixed(2);
    }

    static resolveType(eventType, mode) {
        if (mode === Flamegraph.DIFFERENTIAL) {
            return FlamegraphTooltips.DIFF
        } else if (eventType === "jdk.ExecutionSample") {
            return FlamegraphTooltips.CPU
        } else if (eventType === "jdk.ObjectAllocationInNewTLAB") {
            return FlamegraphTooltips.TLAB_ALLOC
        } else {
            return FlamegraphTooltips.BASIC
        }
    }
}
