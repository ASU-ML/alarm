const API = (import.meta as any).env?.VITE_API_URL || ''

export type Kpi = { alm_rate_per_min: number; standing_alarms: number; p95_ack_seconds: number; chattering_count: number }

export async function getKpiStandard(from: string, to: string): Promise<Kpi> {
  const r = await fetch(`${API}/api/v1/kpi/standard?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
  if (!r.ok) throw new Error('KPI error')
  return r.json()
}

export async function runReport(from: string, to: string, format: 'pdf'|'xlsx'): Promise<{file: string}> {
  const r = await fetch(`${API}/api/v1/reports/run?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&format=${format}`, { method: 'POST' })
  if (!r.ok) throw new Error('Report error')
  return r.json()
}

export async function searchAudit(from?: string, to?: string) {
  const url = new URL(`${API}/api/v1/audit/search`)
  if (from) url.searchParams.set('from', from)
  if (to) url.searchParams.set('to', to)
  const r = await fetch(url.toString())
  if (!r.ok) throw new Error('Audit error')
  return r.json()
}

export async function health(): Promise<{status:string}> {
  const r = await fetch(`${API}/api/health`)
  return r.json()
}
