import type { StreamEvent } from '~/types/api'

const eventTypes = new Set<StreamEvent['type']>([
  'text', 'profile_updated', 'bank_picker', 'done', 'error',
])

/** Parse named SSE events from a POST response, including fragmented UTF-8/chunks. */
export async function consumeStream(response: Response, onEvent: (event: StreamEvent) => void) {
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

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      append(decoder.decode(value, { stream: true }))
    }
    append(decoder.decode())
    if (pending) lines.push(pending.replace(/\r$/, ''))
    if (lines.length) dispatch()
  } finally {
    reader.releaseLock()
  }
}