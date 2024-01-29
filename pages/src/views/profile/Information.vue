<script setup>

import { onMounted, ref } from 'vue';
import PrimaryProfileService from '@/service/PrimaryProfileService';
import InformationService from '@/service/InformationService';

let info = ref(null);
onMounted(() => {
    InformationService.info(PrimaryProfileService.id())
        .then((data) => {
            info.value = data;
        });
});
</script>

<template>
    <div class="card card-w-title">
        <div v-for="(section, sectionName) in info" :key="sectionName" style="padding-bottom: 20px">
            <Fieldset :legend="sectionName" :toggleable="false" class="p-fluid">
                <div v-for="(value, key) in section" :key="key">
                    <div class="field grid">
                        <label class="col-12 mb-2 md:col-2 md:mb-0" style="font-weight: bold">{{ key }}</label>
                        <div class="col-12 md:col-10">
                            <div v-if="value != null && value.toString().length > 50">
                                <Textarea disabled type="text" style="color: darkblue; font-weight: bold"
                                          :autoResize="true" :value="value" />
                            </div>
                            <div v-else>
                                <InputText  disabled type="text" style="color: darkblue; font-weight: bold"
                                           :value="value" />
                            </div>
                        </div>
                    </div>
                </div>
            </Fieldset>
        </div>
    </div>

</template>

<style scoped lang="scss"></style>
