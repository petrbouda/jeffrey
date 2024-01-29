<script setup>
import { ref } from 'vue';
import FlamegraphList from '@/components/FlamegraphList.vue';
import { useRouter } from 'vue-router';
import GlobalVars from '@/service/GlobalVars';
import PrimaryProfileService from '@/service/PrimaryProfileService';

const router = useRouter();
const flamegraphs = ref(null);
const selectedEventType = ref(0);

const clickEventTypeSelected = () => {
    router.push({ name: 'flamegraph-show', query: { mode: 'predefined', profileId: PrimaryProfileService.id(), eventType: selectedEventType.value.index } });
};

const jfrEventTypes = ref(GlobalVars.jfrTypes());
</script>

<template>
    <div class="card">
        <SelectButton v-model="selectedEventType" :options="jfrEventTypes" @click="clickEventTypeSelected"
                      optionLabel="label" :multiple="false" />
    </div>

    <div class="card card-w-title">
        <h5>Generate Flamegraph</h5>
        <div class="grid p-fluid mt-3">
            <div class="field col-12 md:col-6">
                <span class="p-float-label">
                    <Textarea inputId="textarea" rows="10" cols="30" v-model="value10"></Textarea>
                    <label for="textarea">Insert JFR SQL</label>
                </span>
                <span class="p-float-label">
                    <InputText type="text" id="profilename" v-model="value1" />
                    <label for="profilename">Profile's name</label>
                </span>
            </div>
        </div>
    </div>

    <FlamegraphList :profile-id="PrimaryProfileService.id()" profile-type="primary" />
</template>

<style scoped lang="scss"></style>
