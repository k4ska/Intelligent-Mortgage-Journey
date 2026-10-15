/** HTTP status from a $fetch error, a fetch-based API error or a mock error. */
export function errorStatus(error: unknown): number | undefined {
  const { status, statusCode } = (error ?? {}) as { status?: number; statusCode?: number }
  return status ?? statusCode
}
