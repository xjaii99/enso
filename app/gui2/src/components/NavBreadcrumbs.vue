<script setup lang="ts">
import SvgIcon from '@/components/SvgIcon.vue'

import NavBreadcrumb from '@/components/NavBreadcrumb.vue'

const props = defineProps<{ breadcrumbs: string[] }>()
const emit = defineEmits<{ click: [index: number] }>()
</script>

<template>
  <div class="NavBreadcrumbs">
    <template v-for="(breadcrumb, index) in props.breadcrumbs" :key="index">
      <SvgIcon v-if="index > 0" name="arrow_right_head_only" class="arrow" />
      <NavBreadcrumb :text="breadcrumb" @click="emit('click', index)" />
    </template>
  </div>
</template>

<style scoped>
.NavBreadcrumbs {
  display: flex;
  align-items: center;
  gap: 2px;
}

.arrow {
  color: #666666;
}
</style>
