<script setup>
// Copyright 2020 Andrei Pangin
// Modifications copyright (C) 2024 Petr Bouda
// Licensed under the Apache License, Version 2.0.

import FlamegraphService from '@/service/FlamegraphService';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import { useToast } from 'primevue/usetoast';

let root, rootLevel, pxPerSample, pattern;
let reverse = false;
let hl, status, levels, context2d, canvas, canvasWidth, canvasHeight;
const router = useRouter();
const route = useRoute();
const selectedEventType = ref(null);
const jfrEventTypes = ref(GlobalVars.jfrTypes());
const toast = useToast();

const frame_height = 20

function onResize({ width, height }) {
    // minus padding
    setCanvasWidth(width - 50);

    render();
}

function setCanvasWidth(width) {
    canvasWidth = width;
    canvas.style.width = canvasWidth + 'px';

    canvas.width = canvasWidth * (devicePixelRatio || 1);
    if (devicePixelRatio) context2d.scale(devicePixelRatio, devicePixelRatio);
    context2d.font = '12px Arial';
}

onMounted(() => {
    selectedEventType.value = jfrEventTypes.value[route.query.eventType]

    f(0, 0, 0, 0, 'fake-to-include-unused-method');

    canvas = document.getElementById('canvas');
    context2d = canvas.getContext('2d');
    hl = document.getElementById('hl');
    status = document.getElementById('status');

    canvasHeight = canvas.offsetHeight;
    canvas.height = canvasHeight * (devicePixelRatio || 1);

    setCanvasWidth(canvas.offsetWidth);

    canvas.onmousemove = function() {
        const h = Math.floor((reverse ? event.offsetY : canvasHeight - event.offsetY) / 16);
        if (h >= 0 && h < levels.length) {
            const f = findFrame(levels[h], event.offsetX / pxPerSample + root.left);
            if (f) {
                if (f !== root) getSelection().removeAllRanges();
                hl.style.left = Math.max(f.left - root.left, 0) * pxPerSample + canvas.offsetLeft + 'px';
                hl.style.width = Math.min(f.width, root.width) * pxPerSample + 'px';
                hl.style.top = (reverse ? h * 16 : canvasHeight - (h + 1) * 16) + canvas.offsetTop + 'px';
                hl.firstChild.textContent = f.title;
                hl.style.display = 'block';
                canvas.title = f.title + '\n(' + samples(f.width) + f.details + ', ' + pct(f.width, levels[0][0].width) + '%)';
                canvas.style.cursor = 'pointer';
                canvas.onclick = function() {
                    if (f !== root) {
                        render(f, h);
                        canvas.onmousemove();
                    }
                };
                status.textContent = canvas.title;
                status.style.display = 'inline-block';
                status.style.left = document.getElementById('layout-main').getBoundingClientRect().left + 'px';
                status.style.bottom = status.style.bottom + 10 + 'px';
                return;
            }
        }
        canvas.onmouseout();
    };

    canvas.onmouseout = function() {
        hl.style.display = 'none';
        status.style.display = 'none';
        canvas.title = '';
        canvas.style.cursor = '';
        canvas.onclick = '';
    };

    canvas.ondblclick = function() {
        getSelection().selectAllChildren(hl);
    };

    document.getElementById('reverse').onclick = function() {
        reverse = !reverse;
        render();
    };

    document.getElementById('search').onclick = function() {
        search(true);
    };

    document.getElementById('reset').onclick = function() {
        search(false);
    };

    window.onkeydown = function() {
        if (event.ctrlKey && event.keyCode === 70) {
            event.preventDefault();
            search(true);
        } else if (event.keyCode === 27) {
            search(false);
        }
    };

    updateFlamegraph(route.query.eventType)
});

function updateFlamegraph(eventType) {
    let flamegraphMode = route.query.mode;
    if (flamegraphMode === 'predefined') {
        FlamegraphService.getPredefined(route.query.profileId, jfrEventTypes.value[eventType].code)
            .then((data) => processIncomingData(data))
            .then(() => render())
            .then(() => search());
    } else {
        FlamegraphService.getSingle(route.query.profileId, route.query.flamegraphId)
            .then((data) => processIncomingData(data))
            .then(() => render())
            .then(() => search());
    }
}

const clickEventTypeSelected = () => {
    let eventTypeIndex = selectedEventType.value.index;
    router.push({ name: 'flamegraph-show', query: { mode: 'predefined', profileId: route.query.profileId, eventType: jfrEventTypes.value[eventTypeIndex].code } });
    updateFlamegraph(eventTypeIndex)
};

const palette = [
    [0xb2e1b2, 20, 20, 20],
    [0x50e150, 30, 30, 30],
    [0x50cccc, 30, 30, 30],
    [0xe15a5a, 30, 40, 40],
    [0xc8c83c, 30, 30, 10],
    [0xe17d00, 30, 30, 0],
    [0xcce880, 20, 20, 20]
];

function processIncomingData(data) {
    updateLevels(data.depth);
    data.frames.forEach((line) => {
        eval(line);
    });
}

function getColor(p) {
    const v = Math.random();
    return '#' + (p[0] + (((p[1] * v) << 16) | ((p[2] * v) << 8) | (p[3] * v))).toString(16);
}

function f(level, left, width, type, title, inln, c1, int) {
    if (level === 0 && left === 0 && width === 0 && type === 0 && title === 'fake-to-include-unused-method') {
        return;
    }

    levels[level].push({
        left: left,
        width: width,
        color: getColor(palette[type]),
        title: title,
        details: (int ? ', int=' + int : '') + (c1 ? ', c1=' + c1 : '') + (inln ? ', inln=' + inln : '')
    });
}

function updateLevels(numOfLevels) {
    levels = Array(numOfLevels + 1);
    for (let h = 0; h < levels.length; h++) {
        levels[h] = [];
    }
}

function samples(n) {
    return n === 1 ? '1 sample' : n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',') + ' samples';
}

function pct(a, b) {
    return a >= b ? '100' : ((100 * a) / b).toFixed(2);
}

function findFrame(frames, x) {
    let left = 0;
    let right = frames.length - 1;

    while (left <= right) {
        const mid = (left + right) >>> 1;
        const f = frames[mid];

        if (f.left > x) {
            right = mid - 1;
        } else if (f.left + f.width <= x) {
            left = mid + 1;
        } else {
            return f;
        }
    }

    if (frames[left] && (frames[left].left - x) * pxPerSample < 0.5) return frames[left];
    if (frames[right] && (x - (frames[right].left + frames[right].width)) * pxPerSample < 0.5) return frames[right];

    return null;
}

function search(r) {
    if (r === true && (r = prompt('Enter regexp to search:', '')) === null) {
        return;
    }

    pattern = r ? RegExp(r) : undefined;
    const matched = render(root, rootLevel);
    document.getElementById('matchval').textContent = pct(matched, root.width) + '%';

    const matchEl = document.getElementById('match');
    matchEl.style.display = r ? 'inline-block' : 'none';
    matchEl.style.bottom = status.style.bottom + 10 + 'px';

    const layoutMainSizes = canvas.getBoundingClientRect();
    const xPosition = layoutMainSizes.right - matchEl.getBoundingClientRect().width;
    matchEl.style.left = xPosition + 'px';
}

function render(newRoot, newLevel) {
    if (root) {
        context2d.fillStyle = '#ffffff';
        context2d.fillRect(0, 0, canvasWidth, canvasHeight);
    }

    root = newRoot || levels[0][0];
    rootLevel = newLevel || 0;
    pxPerSample = canvasWidth / root.width;

    const x0 = root.left;
    const x1 = x0 + root.width;
    const marked = [];

    function mark(f) {
        return marked[f.left] >= f.width || (marked[f.left] = f.width);
    }

    function totalMarked() {
        let total = 0;
        let left = 0;
        Object.keys(marked)
            .sort(function(a, b) {
                return a - b;
            })
            .forEach(function(x) {
                if (+x >= left) {
                    total += marked[x];
                    left = +x + marked[x];
                }
            });
        return total;
    }

    function isMethodMatched(frame) {
        return pattern && frame.title.match(pattern) && mark(frame)
    }

    function drawFrame(frame, y, alpha) {
        if (frame.left < x1 && frame.left + frame.width > x0) {
            context2d.fillStyle = isMethodMatched(frame) ? '#ee00ee' : frame.color;
            context2d.fillRect((frame.left - x0) * pxPerSample, y, frame.width * pxPerSample, frame_height);

            // Do we want to fill the text, or the frame is too small and leave it empty
            if (frame.width * pxPerSample >= 21) {
                // const chars = Math.floor((frame.width * pxPerSample) / 7);
                // const title = frame.title.length <= chars ? frame.title : frame.title.substring(0, chars - 2) + '..';
                context2d.fillStyle = '#000000';
                context2d.fillText(frame.title, Math.max(frame.left - x0, 0) * pxPerSample + 3, y + 12, frame.width * pxPerSample - 6);
            }

            if (alpha) {
                context2d.fillStyle = 'rgba(255, 255, 255, 0.5)';
                context2d.fillRect((frame.left - x0) * pxPerSample, y, frame.width * pxPerSample, frame_height);
            }
        }
    }


    for (let h = 0; h < levels.length; h++) {
        const y = reverse ? h * (frame_height + 1) : canvasHeight - (h + 1) * (frame_height + 1);
        const frames = levels[h];
        for (let i = 0; i < frames.length; i++) {
            drawFrame(frames[i], y, h < rootLevel);
        }
    }

    return totalMarked();
}

const exportFlamegraph = () => {
    let eventType = null
    if (route.query.eventType != null) {
        eventType = GlobalVars.jfrTypes()[route.query.eventType].code
    }

    FlamegraphService.export(route.query.profileId, route.query.flamegraphId, eventType)
        .then((json) => {
            toast.add({ severity: 'success', summary: 'Successful', detail: 'Flamegraph exported', life: 3000 });
        });
};
</script>

<template>
    <div class="card">
        <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                      optionLabel="label" :multiple="false" />
    </div>

    <div v-resize="onResize" class="card card-w-title" style="padding: 40px 25px 25px;">
        <header style="text-align: left">
            <Button id="reverse" icon="pi pi-arrows-v" class="p-button-rounded p-button-info mt-2" title="Reverse" />&nbsp;
            <Button id="search" icon="pi pi-search" class="p-button-rounded p-button-info mt-2" title="Search" />&nbsp;
            <Button icon="pi pi-file-export" class="p-button-rounded p-button-info mt-2" @click="exportFlamegraph()" title="Export" />
        </header>
        <header style="text-align: right">Produced by <a href="https://github.com/jvm-profiling-tools/async-profiler">async-profiler</a>
        </header>
        <canvas id="canvas"></canvas>
        <div id="hl"><span></span></div>
        <p id="status"></p>
        <p id="match">Matched: <span id="matchval"></span> <span id="reset" title="Clear">&#x274c;</span></p>
    </div>

    <Toast />
</template>

<style>
body {
    margin: 0;
    padding: 10px 10px 22px 10px;
}

h1 {
    margin: 5px 0 0 0;
    font-size: 18px;
    font-weight: normal;
    text-align: center;
}

header {
    margin: -24px 0 5px 0;
    line-height: 24px;
}

button {
    font: 12px sans-serif;
    cursor: pointer;
}

p {
    position: fixed;
    bottom: 0;
    margin: 0;
    padding: 2px 3px 2px 3px;
    outline: 1px solid #ffc000;
    display: none;
    overflow: hidden;
    white-space: nowrap;
    background-color: #ffffe0;
}

a {
    color: #0366d6;
}

#hl {
    position: absolute;
    display: none;
    overflow: hidden;
    white-space: nowrap;
    pointer-events: none;
    background-color: #ffffe0;
    outline: 1px solid #ffc000;
    font: 12px Arial;
    height: 20px;
}

#hl span {
    padding: 0 3px 0 3px;
}

#status {
    left: 0;
}

#reset {
    cursor: pointer;
}

#canvas {
    width: 100%;
    height: 1952px;
}
</style>
