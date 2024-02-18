<script setup>
import { onMounted } from 'vue';
import HeatmapGraphApex from '@/service/HeatmapGraphApex';
import FlamegraphService from '@/service/FlamegraphService';

onMounted(() => {

    FlamegraphService.testData()
        .then((data) => {
            let heatmap1 = new HeatmapGraphApex('heatmap1', data);
            heatmap1.render();

            // let heatmap2 = new HeatmapGraphApex('heatmap2', data);
            // heatmap2.render();
        })
});
</script>

<template>
    <div class="grid">
        <div class="col-12">
            <div class="card">
                <!--                <div id="chart-bar" style="overflow: auto; height: 280px"></div>-->
                <div style="overflow: auto;">
                    <div id="heatmap1"></div>
<!--                    <div id="heatmap2"></div>-->
                </div>
            </div>
        </div>
    </div>
</template>

<style>
.apexcharts-tooltip:not(:empty) {
    padding: 6px 9px;
}
</style>