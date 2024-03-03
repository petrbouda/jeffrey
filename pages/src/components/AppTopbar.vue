<script setup>
import PrimaryProfileService from '@/service/PrimaryProfileService';
import {useRouter} from "vue-router";
import {onMounted, ref} from "vue";

const router = useRouter();
const showTopbar = ref(true)

onMounted(() => {
  showTopbar.value = !(router.currentRoute.value.fullPath.startsWith("/index") || router.currentRoute.value.fullPath === "/")
});
</script>

<template>
  <div class="grid">
      <div v-if="showTopbar" class="col-12 flex justify-content-end flex-wrap mb-2">
        <Button :label="PrimaryProfileService.profile.value" outlined severity="primary"/>
        <!--            <Button :label="SecondaryProfileService.profile.value" outlined severity="secondary" />-->
      </div>
  </div>
</template>
