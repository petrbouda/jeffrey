<script setup>
// Copyright 2020 Andrei Pangin
// Licensed under the Apache License, Version 2.0.

import FlamegraphService from '@/service/FlamegraphService';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

let root, rootLevel, px, pattern;
let reverse = false;
let hl, status, levels, c, canvas, canvasWidth, canvasHeight;
const router = useRouter();
const route = useRoute();
const selectedEventType = ref(null);
const jfrEventTypes = ref([
    {
        index: 0,
        label: 'Execution Samples (CPU)',
        code: 'jdk.ExecutionSample'
    },
    {
        index: 1,
        label: 'Allocations',
        code: 'jdk.ObjectAllocationInNewTLAB'
    },
    {
        index: 2,
        label: 'Locks',
        code: 'jdk.ThreadPark'
    },
    {
        index: 3,
        label: 'Live Objects',
        code: 'profiler.LiveObject'
    }
]);

function onResize({ width, height }) {
    // minus padding
    setCanvasWidth(width - 50);

    render();
}

function setCanvasWidth(width) {
    canvasWidth = width;
    canvas.style.width = canvasWidth + 'px';

    canvas.width = canvasWidth * (devicePixelRatio || 1);
    if (devicePixelRatio) c.scale(devicePixelRatio, devicePixelRatio);
    c.font = '12px Arial';
}

onMounted(() => {
    selectedEventType.value = jfrEventTypes.value[route.query.eventType]

    f(0, 0, 0, 0, 'fake-to-include-unused-method');

    canvas = document.getElementById('canvas');
    c = canvas.getContext('2d');
    hl = document.getElementById('hl');
    status = document.getElementById('status');

    canvasHeight = canvas.offsetHeight;
    canvas.height = canvasHeight * (devicePixelRatio || 1);

    setCanvasWidth(canvas.offsetWidth);

    canvas.onmousemove = function() {
        const h = Math.floor((reverse ? event.offsetY : canvasHeight - event.offsetY) / 16);
        if (h >= 0 && h < levels.length) {
            const f = findFrame(levels[h], event.offsetX / px + root.left);
            if (f) {
                if (f !== root) getSelection().removeAllRanges();
                hl.style.left = Math.max(f.left - root.left, 0) * px + canvas.offsetLeft + 'px';
                hl.style.width = Math.min(f.width, root.width) * px + 'px';
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
        FlamegraphService.getPredefined(jfrEventTypes.value[eventType].code)
            .then((data) => processIncomingData(data))
            .then(() => render())
            .then(() => search());
    } else {
        FlamegraphService.getSingle(route.query.flamegraphId)
            .then((data) => processIncomingData(data))
            .then(() => search());
    }
}

const clickEventTypeSelected = () => {
    let eventTypeIndex = selectedEventType.value.index;
    router.push({ name: 'flamegraph-show', query: { mode: 'predefined', eventType: jfrEventTypes.value[eventTypeIndex].code } });
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
    let firstLine = true;

    data.split('\n').forEach((line) => {
        if (firstLine) {
            updateLevels(parseInt(line));
            firstLine = false;
        } else {
            eval(line);
        }
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

    if (frames[left] && (frames[left].left - x) * px < 0.5) return frames[left];
    if (frames[right] && (x - (frames[right].left + frames[right].width)) * px < 0.5) return frames[right];

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
        c.fillStyle = '#ffffff';
        c.fillRect(0, 0, canvasWidth, canvasHeight);
    }

    root = newRoot || levels[0][0];
    rootLevel = newLevel || 0;
    px = canvasWidth / root.width;

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

    function drawFrame(f, y, alpha) {
        if (f.left < x1 && f.left + f.width > x0) {
            c.fillStyle = pattern && f.title.match(pattern) && mark(f) ? '#ee00ee' : f.color;
            c.fillRect((f.left - x0) * px, y, f.width * px, 15);

            if (f.width * px >= 21) {
                const chars = Math.floor((f.width * px) / 7);
                const title = f.title.length <= chars ? f.title : f.title.substring(0, chars - 2) + '..';
                c.fillStyle = '#000000';
                c.fillText(title, Math.max(f.left - x0, 0) * px + 3, y + 12, f.width * px - 6);
            }

            if (alpha) {
                c.fillStyle = 'rgba(255, 255, 255, 0.5)';
                c.fillRect((f.left - x0) * px, y, f.width * px, 15);
            }
        }
    }

    // eslint-disable-next-line prettier/prettier
    for (let h = 0; h < levels.length; h++) {
        const y = reverse ? h * 16 : canvasHeight - (h + 1) * 16;
        const frames = levels[h];
        for (let i = 0; i < frames.length; i++) {
            drawFrame(frames[i], y, h < rootLevel);
        }
    }

    return totalMarked();
}
</script>

<template>
    <div class="card">
        <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                      optionLabel="label" :multiple="false" />
    </div>

    <div v-resize="onResize" class="card card-w-title" style="padding: 40px 25px 25px;">
        <header style="text-align: left">
            <button id="reverse" title="Reverse">&#x1f53b;</button>&nbsp;&nbsp;<button id="search" title="Search">
            &#x1f50d;
        </button>
        </header>
        <header style="text-align: right">Produced by <a href="https://github.com/jvm-profiling-tools/async-profiler">async-profiler</a>
        </header>
        <canvas id="canvas"></canvas>
        <div id="hl"><span></span></div>
        <p id="status"></p>
        <p id="match">Matched: <span id="matchval"></span> <span id="reset" title="Clear">&#x274c;</span></p>
    </div>
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
    height: 15px;
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
