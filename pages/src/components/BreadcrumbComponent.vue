<!--
  - Jeffrey
  - Copyright (C) 2024 Petr Bouda
  -
  - This program is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Affero General Public License as published by
  - the Free Software Foundation, either version 3 of the License, or
  - (at your option) any later version.
  -
  - This program is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU Affero General Public License for more details.
  -
  - You should have received a copy of the GNU Affero General Public License
  - along with this program.  If not, see <http://www.gnu.org/licenses/>.
  -->

<script setup>

import {onBeforeMount} from "vue";
import {useRoute} from "vue-router";

const route = useRoute();

const props = defineProps([
  'path'
]);

const home = {
  icon: 'pi pi-home',
  route: '/'
};

const items = [
  {label: 'Project', route: '/projects/' + route.params.projectId + '/profiles'},
  {label: 'Profile', route: '/projects/' + route.params.projectId + '/profiles/' + route.params.profileId + '/information'} ,
];

onBeforeMount(() => {
  props.path.forEach((item) => {
    items.push(item);
  });
});
</script>

<template>
  <div class="card p-0 mb-3">
    <Breadcrumb :home="home" :model="items" class="border-none">
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
