
<script setup lang="ts">
import { useAdvisorStore } from '~/stores/advisor'
import { copy } from '~/utils/copy'

const advisor = useAdvisorStore()

// Always the order of the labels in copy (the server's key order), not the order the
// fields arrived in during the chat, so nothing jumps around on a reload (#146).
function ordered<T extends { key: string }>(fields: T[], labels: Record<string, string>): T[] {
  const order = Object.keys(labels)
  const rank = (key: string) => order.includes(key) ? order.indexOf(key) : order.length
  return [...fields].sort((a, b) => rank(a.key) - rank(b.key))
}
const profileFields = computed(() => ordered(advisor.profile.fields, copy.profileFields))
const propertyFields = computed(() => ordered(advisor.profile.property?.fields ?? [], copy.propertyFields))

const listingHref = computed(() => {
  const url = advisor.profile.property?.listingUrl
  if (!url) return null
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : null
  } catch {
    return null
  }
})
</script>

<template>
  <aside class="app-panel min-h-0 overflow-y-auto" :aria-label="copy.profile">
    <div class="app-panel-header"><UIcon name="i-lucide-user-round" class="text-lg" /><h2>{{ copy.profile }}</h2></div>
    <div class="grid grid-cols-2 gap-2 p-3">
      <p v-if="!advisor.profile.fields.length" class="col-span-2 py-8 text-center text-sm leading-6 text-[var(--ui-text-muted)]">{{ copy.profileEmpty }}</p>
      <ProfileFieldRow v-for="field in profileFields" :key="field.key" :field="field" :label="copy.profileFields[field.key]" />
    </div>
    <div class="app-panel-header border-t border-[var(--ui-border)]"><UIcon name="i-lucide-house" class="text-lg" /><h2>{{ copy.property }}</h2></div>
    <div class="grid grid-cols-2 gap-2 p-3">
      <p v-if="!advisor.profile.property?.fields.length" class="col-span-2 text-sm leading-6 text-[var(--ui-text-muted)]">{{ copy.propertyEmpty }}</p>
      <ProfileFieldRow v-for="field in propertyFields" :key="field.key" :field="field" :label="copy.propertyFields[field.key]" />
      <a v-if="listingHref" :href="listingHref" target="_blank" rel="noopener noreferrer" class="col-span-2 inline-flex items-center gap-1 text-sm text-[var(--ui-primary)] hover:underline">
        {{ copy.viewListing }} <UIcon name="i-lucide-external-link" />
      </a>
    </div>
  </aside>
</template>
