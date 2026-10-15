import type { ProfileField } from '~/types/api'

/** Replace fields with the same key in place and append new ones. */
export function mergeProfileFields(target: ProfileField[], updates: ProfileField[]) {
  for (const field of updates) {
    const index = target.findIndex(existing => existing.key === field.key)
    if (index >= 0) target[index] = field
    else target.push(field)
  }
}
