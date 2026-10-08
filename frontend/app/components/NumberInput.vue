<script setup lang="ts">
/**
 * A number field that groups digits while you type ("1 000 000") but hands out a plain
 * number (1000000), or null when left blank. Other attributes (placeholder, icons, size,
 * aria-label) pass straight through to UInput.
 */
const props = defineProps<{
  modelValue: number | null | undefined
  /** Accept a decimal part, e.g. for percentages. */
  decimal?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
}>()

const fromValue = (n: number | null | undefined) =>
  n === null || n === undefined || Number.isNaN(n) ? '' : formatNumber(n, props.decimal ? 4 : 0)

const display = ref(fromValue(props.modelValue))

// Follow changes made outside the field (a reset draft, a loaded application), but
// leave what the user is typing alone when it already means the same number.
watch(() => props.modelValue, (value) => {
  if (parseNumber(display.value, props.decimal) !== (value ?? null)) display.value = fromValue(value)
})

function onInput(event: Event) {
  const el = event.target as HTMLInputElement
  const raw = el.value
  const caret = el.selectionStart ?? raw.length

  const formatted = formatTyping(raw, props.decimal)
  display.value = formatted
  el.value = formatted
  const pos = caretAfterFormat(raw, caret, formatted)
  el.setSelectionRange(pos, pos)

  emit('update:modelValue', parseNumber(formatted, props.decimal))
}

/**
 * Backspace just after a group space (or Delete just before one) would only remove the
 * space, which comes straight back. Step over it so the digit is removed instead.
 */
function onKeydown(event: KeyboardEvent) {
  const el = event.target as HTMLInputElement
  const at = el.selectionStart
  if (at === null || at !== el.selectionEnd) return
  if (event.key === 'Backspace' && el.value[at - 1] === DIGIT_GROUP) el.setSelectionRange(at - 1, at - 1)
  if (event.key === 'Delete' && el.value[at] === DIGIT_GROUP) el.setSelectionRange(at + 1, at + 1)
}

/** Puts the caret after the same digit it was after before the spaces moved. */
function caretAfterFormat(raw: string, caret: number, formatted: string): number {
  const before = raw.slice(0, caret)
  const digits = before.replace(/\D/g, '').length
  const afterSeparator = props.decimal && /[.,]$/.test(before)

  let seen = 0
  let pos = 0
  while (pos < formatted.length && seen < digits) {
    if (/\d/.test(formatted[pos]!)) seen++
    pos++
  }
  if (afterSeparator && formatted[pos] === '.') pos++
  // A leading zero that formatTyping dropped can leave fewer digits than counted.
  return Math.min(pos, formatted.length)
}
</script>

<template>
  <UInput
    :model-value="display"
    type="text"
    :inputmode="decimal ? 'decimal' : 'numeric'"
    autocomplete="off"
    @input="onInput"
    @keydown="onKeydown"
  />
</template>
