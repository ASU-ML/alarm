const computedBase = (() => {
  const fromEnv = (import.meta as any).env?.VITE_API_URL as string | undefined
  if (fromEnv && fromEnv.length > 0) return fromEnv
  try {
    const url = new URL(window.location.href)
    if (url.port === '5173') {
      url.port = '8443'
      return url.origin
    }
    return url.origin
  } catch {
    return ''
  }
})()

const API = computedBase

let ACCESS_TOKEN: string | null = null
export function setToken(t: string | null) { ACCESS_TOKEN = t; if (t) localStorage.setItem('access_token', t); else localStorage.removeItem('access_token') }
export function getToken() { return ACCESS_TOKEN || localStorage.getItem('access_token') }

export async function kcLogin({ baseUrl, realm, clientId, username, password }: { baseUrl: string, realm: string, clientId: string, username: string, password: string }) {
  const url = `${baseUrl}/realms/${encodeURIComponent(realm)}/protocol/openid-connect/token`
  const body = new URLSearchParams()
  body.set('grant_type','password')
  body.set('client_id', clientId)
  body.set('username', username)
  body.set('password', password)
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type':'application/x-www-form-urlencoded' }, body })
  if (!r.ok) throw new Error('Keycloak login failed')
  const j = await r.json() as any
  setToken(j.access_token)
  return j
}

export type Kpi = { alm_rate_per_min: number; standing_alarms: number; p95_ack_seconds: number; chattering_count: number }

function authHeaders() { const t = getToken(); return t ? { Authorization: `Bearer ${t}` } : {} }

export async function getKpiStandard(from: string, to: string): Promise<Kpi> {
  const r = await fetch(`${API}/api/v1/kpi/standard?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, { headers: authHeaders() })
  if (!r.ok) throw new Error('KPI error')
  return r.json()
}

export async function runReport(from: string, to: string, format: 'pdf'|'xlsx'): Promise<{file: string}> {
  const r = await fetch(`${API}/api/v1/reports/run?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&format=${format}`, { method: 'POST', headers: { 'Content-Type':'application/json', ...authHeaders() } })
  if (!r.ok) throw new Error('Report error')
  return r.json()
}

export async function searchAudit(from?: string, to?: string) {
  const url = new URL(`${API}/api/v1/audit/search`)
  if (from) url.searchParams.set('from', from)
  if (to) url.searchParams.set('to', to)
  const r = await fetch(url.toString(), { headers: authHeaders() })
  if (!r.ok) throw new Error('Audit error')
  return r.json()
}

export async function health(): Promise<{status:string}> {
  const r = await fetch(`${API}/api/health`)
  return r.json()
}

export function sseUrl(path: string) {
  const t = getToken()
  const sep = path.includes('?') ? '&' : '?'
  return `${API}${path}${t ? `${sep}access_token=${encodeURIComponent(t)}` : ''}`
}

export async function ingestBatch(projectId: string, events: Array<any>) {
  const url = new URL(`${API}/api/v1/ingest/events`)
  const t = getToken()
  if (t) url.searchParams.set('access_token', t)
  const r = await fetch(url.toString(), { method: 'POST', headers: { 'Content-Type':'application/json' }, body: JSON.stringify(events.map(e=>({ ...e, projectId }))) })
  if (!r.ok) throw new Error(`ingest failed: ${r.status}`)
}

export async function runReportTemplate(req: { title: string, sections: Array<{title:string, metrics:string[]}>, format: 'pdf'|'xlsx', from: string, to: string }): Promise<{file:string}> {
  const r = await fetch(`${API}/api/v1/reports/run-template`, { method:'POST', headers: { 'Content-Type':'application/json', ...authHeaders() }, body: JSON.stringify(req) })
  if (!r.ok) throw new Error('Report template error')
  return r.json()
}

export async function getTrends(tags: string[], from: string, to: string, stepSec = 60): Promise<{series: Record<string, [string, number][]>}> {
  const url = new URL(`${API}/api/v1/trends/series`)
  url.searchParams.set('tags', tags.join(','))
  url.searchParams.set('from', from)
  url.searchParams.set('to', to)
  url.searchParams.set('stepSec', String(stepSec))
  const r = await fetch(url.toString(), { headers: authHeaders() })
  if (!r.ok) throw new Error('Trends error')
  return r.json()
}
