import type { Condition, Field } from '~/types/api'

// Mirrors FlowDefinition.Condition#matches on the backend, so a field is shown here exactly
// when the backend keeps its answer.

function equalsLoose(a: unknown, b: unknown): boolean {
  if (a === null || a === undefined || b === null || b === undefined) {
    return (a ?? null) === (b ?? null)
  }
  if (typeof a === 'number' && typeof b === 'number') return a === b
  return String(a).toLowerCase() === String(b).toLowerCase()
}

function compare(a: unknown, b: unknown): number {
  if (a === null || a === undefined) return -1
  return Number(a) - Number(b)
}

export function conditionMatches(condition: Condition, answers: Record<string, unknown>): boolean {
  const actual = answers[condition.field]
  switch (condition.op) {
    case 'eq': return equalsLoose(actual, condition.value)
    case 'ne': return !equalsLoose(actual, condition.value)
    case 'gt': return compare(actual, condition.value) > 0
    case 'gte': return compare(actual, condition.value) >= 0
    case 'lt': return compare(actual, condition.value) < 0
    case 'lte': return compare(actual, condition.value) <= 0
    case 'in': return Array.isArray(condition.value) && condition.value.some(v => equalsLoose(actual, v))
    default: return false
  }
}

/** Fields without a showIf are always shown. */
export function fieldVisible(field: Field, answers: Record<string, unknown>): boolean {
  return !field.showIf || conditionMatches(field.showIf, answers)
}
