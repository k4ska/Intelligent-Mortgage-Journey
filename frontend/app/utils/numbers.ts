/**
 * One way to show numbers everywhere: digits grouped in threes with a (non-breaking)
 * space, so 100 000 and 1 000 000 are easy to tell apart. Fixed rather than taken from
 * the browser's locale, so inputs and displayed amounts always look the same.
 */
export const DIGIT_GROUP = ' '

function groupDigits(digits: string): string {
  return digits.replace(/\B(?=(\d{3})+(?!\d))/g, DIGIT_GROUP)
}

/** 1000000 -> "1 000 000"; 3.25 -> "3.25". Rounds to at most `maxFractionDigits`. */
export function formatNumber(n: number, maxFractionDigits = 0): string {
  const [int = '0', frac = ''] = Math.abs(n).toFixed(maxFractionDigits).split('.')
  const trimmed = frac.replace(/0+$/, '')
  return `${n < 0 ? '-' : ''}${groupDigits(int)}${trimmed ? `.${trimmed}` : ''}`
}

/** 100000 -> "€100 000". Whole euros. */
export function formatMoney(n: number): string {
  return `${n < 0 ? '-' : ''}€${formatNumber(Math.abs(n))}`
}

interface NumberParts {
  negative: boolean
  int: string
  /** Digits after the decimal separator; '' for "3." and null when there is none. */
  frac: string | null
}

/**
 * Splits typed or pasted text into its parts. "1 000 000", "1,000,000", "1.000.000" all
 * have no decimals; "3.5", "3,5" and "3." do. A lone separator followed by exactly three
 * digits ("1,000") is read as grouping. Without `allowDecimal` a decimal part is dropped,
 * so pasting "250000.00" into a money field gives 250000, not 25000000.
 */
function splitNumber(text: string, allowDecimal: boolean): NumberParts {
  const negative = text.trim().startsWith('-')
  const s = text.replace(/[^\d.,]/g, '')

  const last = Math.max(s.lastIndexOf('.'), s.lastIndexOf(','))
  const mixed = s.includes('.') && s.includes(',')
  const single = last >= 0 && s.indexOf(s[last]!) === last
  const decimalAt = last >= 0 && (mixed || (single && s.length - last - 1 !== 3)) ? last : -1

  return {
    negative,
    int: (decimalAt >= 0 ? s.slice(0, decimalAt) : s).replace(/\D/g, ''),
    frac: decimalAt >= 0 && allowDecimal ? s.slice(decimalAt + 1).replace(/\D/g, '') : null,
  }
}

/** Blank (or nothing numeric) is null - "not answered" - never 0. */
export function parseNumber(text: string, allowDecimal = false): number | null {
  const { negative, int, frac } = splitNumber(text, allowDecimal)
  if (!int && !frac) return null
  const value = Number(`${int || '0'}${frac ? `.${frac}` : ''}`)
  return negative ? -value : value
}

/**
 * Formats text as it is being typed: groups the whole part, but keeps a decimal part
 * exactly as typed so "3." or "3.50" isn't rewritten under the cursor.
 */
export function formatTyping(text: string, allowDecimal = false): string {
  const { negative, int, frac } = splitNumber(text, allowDecimal)
  if (!int && frac === null) return negative ? '-' : ''
  const whole = groupDigits(int.replace(/^0+(?=\d)/, '') || '0')
  return `${negative ? '-' : ''}${whole}${frac !== null ? `.${frac}` : ''}`
}
