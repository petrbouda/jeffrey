<script setup>

const props = defineProps([
  'path'
]);

const home = {
  icon: 'pi pi-home',
  route: '/profile/information'
};

</script>

<template>
  <div class="card p-0 mb-3">
    <Breadcrumb :home="home" :model="props.path" class="border-none">
      <template #item="{ item, props }">
        <router-link v-if="item.route" v-slot="{ href, navigate }" :to="item.route" custom>
          <a :href="href" v-bind="props.action" @click="navigate">
            <span :class="[item.icon, 'text-color']"/>
            <span class="text-primary font-semibold">{{ item.label }}</span>
          </a>
        </router-link>
        <a v-else :href="item.url" :target="item.target" v-bind="props.action">
          <span class="text-color">{{ item.label }}</span>
        </a>
      </template>
    </Breadcrumb>
  </div>
</template>
