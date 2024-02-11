<script setup>
// Copyright 2020 Andrei Pangin
// Modifications copyright (C) 2024 Petr Bouda
// Licensed under the Apache License, Version 2.0.

import FlamegraphService from '@/service/FlamegraphService';
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import { useToast } from 'primevue/usetoast';
import Flamegraph from '@/service/Flamegraph';

const router = useRouter();
const route = useRoute();
const selectedEventType = ref(null);
const jfrEventTypes = ref(GlobalVars.jfrTypes());
const toast = useToast();

let hl, canvas;
let flamegraph = null;

function onResize({ width, height }) {
    // minus padding
    flamegraph.resizeCanvas(width - 50);
}

onMounted(() => {
    selectedEventType.value = jfrEventTypes.value[route.query.eventType];

    canvas = document.getElementById('canvas');

    hl = document.getElementById('hl');

    updateFlamegraph(canvas, route.query.eventType);

    document.getElementById('reverse').onclick = function() {
        flamegraph.reverse();
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
});

function updateFlamegraph(canvas, eventType) {
    FlamegraphService.get(route.query.profileId, route.query.flamegraphId, eventType)
        .then((data) => {
            flamegraph = new Flamegraph(data, canvas, hl);
            flamegraph.drawRoot();
        });
}

const clickEventTypeSelected = () => {
    router.push({ name: 'flamegraph-show',
        query: {
            mode: 'predefined',
            profileId: route.query.profileId,
            eventType: selectedEventType.value.code
        }
    });
    updateFlamegraph(canvas, selectedEventType.value.code);
};

function search(r) {
    if (r === true && (r = prompt('Enter regexp to search:', '')) === null) {
        return;
    }

    let pattern = r ? RegExp(r) : undefined;
    if (pattern) {
        const matched = flamegraph.search(pattern);
        document.getElementById('matchval').textContent = matched + '%';
    } else {
        flamegraph.resetSearch();
    }

    const matchEl = document.getElementById('match');
    matchEl.style.display = r ? 'inline-block' : 'none';

    const layoutMainSizes = canvas.getBoundingClientRect();
    const xPosition = layoutMainSizes.right - matchEl.getBoundingClientRect().width;
    matchEl.style.left = xPosition + 'px';
}

const exportFlamegraph = () => {
    let eventType = null;
    if (route.query.eventType != null) {
        eventType = GlobalVars.jfrTypes()[route.query.eventType].code;
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
        <header style="text-align: left; padding-bottom: 10px">
            <Button id="reverse" icon="pi pi-arrows-v" class="p-button-filled p-button-info mt-2" title="Reverse" />&nbsp;
            <Button id="search" icon="pi pi-search" class="p-button-filled p-button-info mt-2" title="Search" />&nbsp;
            <Button icon="pi pi-file-export" class="p-button-filled p-button-info mt-2" @click="exportFlamegraph()"
                    title="Export" />
        </header>
        <canvas id="canvas"></canvas>
        <div id="hl"><span></span></div>
        <p id="match" style="bottom: 10px">Matched: <span id="matchval"></span> <span id="reset" title="Clear">&#x274c;</span></p>
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
    padding-top: 3px;
}

#hl span {
    padding: 0 3px 0 3px;
}

#reset {
    cursor: pointer;
}

#canvas {
    width: 100%;
}
</style>
