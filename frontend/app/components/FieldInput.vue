<script setup lang="ts">
import type { Field } from '~/types/api'

const props = defineProps<{
  field: Field
  modelValue: unknown
}>()

const emit = defineEmits<{
  'update:modelValue': [value: unknown]
  explain: [helpKey: string]
}>()

// `any` on purpose: the value type depends on the field type at runtime.
const value = computed<any>({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v),
})

const selectItems = computed(() =>
  (props.field.options ?? []).map(o => ({ label: o.label, value: o.value })),
)
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ field.label }}</label>
      <FieldHelp
        v-if="field.helpKey"
        :help-key="field.helpKey"
        :label="field.label"
        @explain="emit('explain', $event)"
      />
    </div>

    <UInput
      v-if="field.type === 'TEXT' || field.type === 'DATE'"
      v-model="value"
      :type="field.type === 'DATE' ? 'date' : 'text'"
      :placeholder="field.placeholder ?? ''"
    />

    <UInput
      v-else-if="field.type === 'MONEY' || field.type === 'NUMBER' || field.type === 'PERCENT'"
      v-model.number="value"
      type="number"
      :placeholder="field.placeholder ?? ''"
      :leading-icon="field.type === 'MONEY' ? 'i-lucide-euro' : undefined"
      :trailing-icon="field.type === 'PERCENT' ? 'i-lucide-percent' : undefined"
    />

    <USelect
      v-else-if="field.type === 'SELECT'"
      v-model="value"
      :items="selectItems"
      :placeholder="field.placeholder ?? 'Choose one'"
    />

    <URadioGroup
      v-else-if="field.type === 'RADIO'"
      v-model="value"
      :items="selectItems"
    />

    <USwitch
      v-else-if="field.type === 'BOOLEAN'"
      v-model="value"
      :label="value ? 'Yes' : 'No'"
    />
  </div>
</template>
