<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'

const advisor = useAdvisorStore()
onMounted(() => advisor.load())
</script>

<template>
  <div class="mx-auto grid h-full min-h-0 w-full max-w-[1500px] grid-cols-[minmax(340px,1fr)_280px] gap-3 overflow-x-auto p-3 lg:grid-cols-[minmax(520px,2.15fr)_minmax(280px,1.15fr)] lg:gap-4 lg:p-4">
    <ChatPanel />
    <ProfilePanel />
  </div>
</template>