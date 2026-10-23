import type { StreamEvent } from '~/types/api'

const eventTypes = new Set<StreamEvent['type']>([
  'text', 'profile_updated', 'property_updated', 'bank_picker', 'document_status', 'draft_reply', 'corrected', 'done', 'error',
])

/**
 * Parse named SSE events from a POST response, including fragmented UTF-8/chunks.
 * On any failure or abort the response body is cancelled so the server stops generating.
 */
export async function consumeStream(
  response: Response,
  onEvent: (event: StreamEvent) => void,
  signal?: AbortSignal,
) {
  if (!response.body) throw new Error('Missing response stream')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let pending = ''
  let lines: string[] = []

  function dispatch() {
    const eventLine = lines.find(line => line.startsWith('event:'))
    const name = eventLine?.slice(6).trim()
    const payload = lines.filter(line => line.startsWith('data:'))
      .map(line => line.slice(5).replace(/^ /, '')).join('\n')
    lines = []
    if (!name || !eventTypes.has(name as StreamEvent['type'])) return
    if (!payload) throw new Error(`Missing data for event ${name}`)
    const data: unknown = JSON.parse(payload)
    if (!data || typeof data !== 'object' || Array.isArray(data)) {
      throw new Error(`Invalid data for event ${name}`)
    }
    onEvent({ type: name, data } as StreamEvent)
  }

  function append(chunk: string) {
    pending += chunk
    let end: number
    while ((end = pending.indexOf('\n')) !== -1) {
      const line = pending.slice(0, end).replace(/\r$/, '')
      pending = pending.slice(end + 1)
      if (line === '') dispatch()
      else if (!line.startsWith(':')) lines.push(line)
    }
  }

  const onAbort = () => { void reader.cancel(signal?.reason).catch(() => {}) }
  signal?.addEventListener('abort', onAbort, { once: true })

  try {
    while (true) {
      signal?.throwIfAborted()
      const { done, value } = await reader.read()
      if (done) break
      append(decoder.decode(value, { stream: true }))
    }
    signal?.throwIfAborted()
    // Per the SSE spec, an event without a terminating blank line is incomplete and discarded.
    append(decoder.decode())
  } catch (error) {
    await reader.cancel(error).catch(() => {})
    throw error
  } finally {
    signal?.removeEventListener('abort', onAbort)
    reader.releaseLock()
  }
}