<script setup lang="ts">
/**
 * "What's this?" for a field. Shows the built-in definition straight away - a popover on
 * desktop, a bottom sheet on mobile - with no LLM call. "Explain with my numbers" hands
 * over to the AI explain flow. Fields without a built-in definition go straight to the AI.
 */
const props = defineProps<{
  helpKey: string
  label: string
}>()

const emit = defineEmits<{
  explain: [helpKey: string]
}>()

const store = useApplicationStore()
const open = ref(false)
/** Which definition is showing; following a related term swaps it in place. */
const topic = ref(props.helpKey)

const definition = computed(() => store.definitions[topic.value])
const hasDefinition = computed(() => !!store.definitions[props.helpKey])
const title = computed(() => topic.value === props.helpKey ? props.label : humanize(topic.value))
const related = computed(() =>
  (definition.value?.related ?? []).filter(k => store.definitions[k]),
)

watch(open, (isOpen) => {
  if (isOpen) topic.value = props.helpKey
})

// Bottom sheet below Tailwind's `sm` breakpoint, popover above it.
const isMobile = ref(false)
let mediaQuery: MediaQueryList | null = null
const syncMobile = () => { isMobile.value = !!mediaQuery?.matches }
onMounted(() => {
  mediaQuery = window.matchMedia('(max-width: 639px)')
  syncMobile()
  mediaQuery.addEventListener('change', syncMobile)
})
onBeforeUnmount(() => mediaQuery?.removeEventListener('change', syncMobile))

const Drawer = resolveComponent('UDrawer')
const Popover = resolveComponent('UPopover')

/** Topic to explain once the bottom sheet has finished closing. */
let pendingExplain: string | null = null

function explainWithAi() {
  open.value = false
  // The sheet locks page scroll until its close animation ends, which would cancel
  // the page scrolling to the assistant, so hand over only after that.
  if (isMobile.value) pendingExplain = topic.value
  else emit('explain', topic.value)
}

function onDrawerAnimationEnd(isOpen: boolean) {
  if (isOpen || !pendingExplain) return
  emit('explain', pendingExplain)
  pendingExplain = null
}

function humanize(key: string) {
  const words = key.replace(/-/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
</script>

<template>
  <UButton
    v-if="!hasDefinition"
    size="xs"
    color="neutral"
    variant="ghost"
    icon="i-lucide-help-circle"
    @click="emit('explain', helpKey)"
  >
    What's this?
  </UButton>

  <component
    :is="isMobile ? Drawer : Popover"
    v-else
    v-model:open="open"
    v-bind="isMobile ? { title, description: `Definition of ${title}`, onAnimationEnd: onDrawerAnimationEnd } : { content: { side: 'bottom', align: 'end' } }"
  >
    <UButton
      size="xs"
      color="neutral"
      variant="ghost"
      icon="i-lucide-help-circle"
      :aria-label="`What does ${label} mean?`"
    >
      What's this?
    </UButton>

    <template #content>
      <div class="field-help" :class="isMobile ? 'p-5 pb-8' : 'w-80 max-w-[calc(100vw-2rem)] p-4'">
        <p class="text-sm font-semibold text-highlighted">{{ title }}</p>
        <p class="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-muted">
          {{ definition?.text }}
        </p>

        <div v-if="related.length" class="mt-3 flex flex-wrap gap-1">
          <UButton
            v-for="key in related"
            :key="key"
            size="xs"
            color="neutral"
            variant="soft"
            @click="topic = key"
          >
            {{ humanize(key) }}
          </UButton>
        </div>

        <UButton
          class="mt-4"
          :block="isMobile"
          size="sm"
          icon="i-lucide-sparkles"
          @click="explainWithAi"
        >
          Explain with my numbers
        </UButton>
      </div>
    </template>
  </component>
</template>
