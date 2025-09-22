import { useEffect, useState } from 'react'
import { getKpiStandard, runReport as runReportApi, searchAudit, health } from './api'

const API = (import.meta as any).env?.VITE_API_URL || ''

type EventItem = {
  ts_utc: string
  event_type: string
  alarm_id?: string
  value?: number
  note?: string
  project_id: string
}

type Kpi = { alm_rate_per_min: number; standing_alarms: number; p95_ack_seconds: number; chattering_count: number }

type AuditItem = { ts_utc: string; actor_id: string; action: string; object_type: string; object_id: string; details: string; hash: string }

export function App() {
  const [healthState, setHealthState] = useState('')
  const [feed, setFeed] = useState<EventItem[]>([])
  const [tab, setTab] = useState<'feed'|'kpi'|'reports'|'audit'|'wf'>('feed')
  const [kpi, setKpi] = useState<Kpi | null>(null)
  const [audit, setAudit] = useState<AuditItem[]>([])

  const [wfProject, setWfProject] = useState('')
  const [wfAlarm, setWfAlarm] = useState('')
  const [wfThreshold, setWfThreshold] = useState('')
  const [wfReason, setWfReason] = useState('')
  const [wfPass, setWfPass] = useState('')
  const [wfId, setWfId] = useState('')
  const [wfApprovePass, setWfApprovePass] = useState('')

  useEffect(() => {
    health().then(j => setHealthState(j.status)).catch(() => setHealthState('ОШИБКА'))
  }, [])

  useEffect(() => {
    const es = new EventSource(`${API}/api/v1/events/stream`)
    es.addEventListener('alarm_event', ev => {
      const e = ev as MessageEvent
      try { setFeed(prev => [JSON.parse(e.data) as EventItem, ...prev].slice(0, 200)) } catch {}
    })
    es.onerror = () => { es.close(); setTimeout(() => location.reload(), 3000) }
    return () => es.close()
  }, [])

  const loadKpi = async () => {
    const to = new Date().toISOString()
    const from = new Date(Date.now()-24*3600*1000).toISOString()
    setKpi(await getKpiStandard(from, to))
  }

  const runReport = async (format: 'pdf'|'xlsx') => {
    const to = new Date().toISOString()
    const from = new Date(Date.now()-24*3600*1000).toISOString()
    const j = await runReportApi(from, to, format)
    alert(`Файл отчёта сохранён: ${j.file}`)
  }

  const loadAudit = async () => {
    const to = new Date().toISOString()
    const from = new Date(Date.now()-24*3600*1000).toISOString()
    setAudit(await searchAudit(from, to))
  }

  const startWf = async () => {
    const res = await fetch(`${API}/api/v1/workflow/change-threshold`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ projectId: wfProject, alarmId: wfAlarm, newThreshold: parseFloat(wfThreshold), reason: wfReason, passcode: wfPass })
    })
    const j = await res.json()
    if (res.ok) { setWfId(j.workflowId || '') }
    alert(JSON.stringify(j))
  }

  const approveWf = async () => {
    const res = await fetch(`${API}/api/v1/workflow/approve/${encodeURIComponent(wfId)}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ passcode: wfApprovePass })
    })
    const j = await res.json()
    alert(JSON.stringify(j))
  }

  return (
    <div style={{ fontFamily: 'sans-serif', padding: 16 }}>
      <h2>Менеджер сигнализаций</h2>
      <p>Состояние backend: {healthState || '...'}</p>
      <div style={{ marginBottom: 12 }}>
        <button onClick={() => setTab('feed')}>Лента</button>
        <button onClick={() => setTab('kpi')}>KPI</button>
        <button onClick={() => setTab('reports')}>Отчёты</button>
        <button onClick={() => setTab('audit')}>Аудит</button>
        <button onClick={() => setTab('wf')}>Workflow</button>
      </div>

      {tab === 'feed' && (
        <>
          <h3>Живая лента</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th align="left">Время (UTC)</th>
                <th align="left">Тип</th>
                <th align="left">Alarm</th>
                <th align="left">Значение</th>
                <th align="left">Проект</th>
                <th align="left">Заметка</th>
              </tr>
            </thead>
            <tbody>
              {feed.map((e, i) => (
                <tr key={i}>
                  <td>{e.ts_utc}</td>
                  <td>{e.event_type}</td>
                  <td>{e.alarm_id || ''}</td>
                  <td>{e.value ?? ''}</td>
                  <td>{e.project_id}</td>
                  <td>{e.note || ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {tab === 'kpi' && (
        <>
          <button onClick={loadKpi}>Загрузить KPI за 24ч</button>
          {kpi && (
            <ul>
              <li>ALM rate/мин: {kpi.alm_rate_per_min.toFixed(2)}</li>
              <li>Standing alarms: {kpi.standing_alarms}</li>
              <li>p95 ACK, сек: {kpi.p95_ack_seconds.toFixed(1)}</li>
              <li>Chattering: {kpi.chattering_count}</li>
            </ul>
          )}
        </>
      )}

      {tab === 'reports' && (
        <>
          <button onClick={() => runReport('pdf')}>Сгенерировать PDF</button>
          <button onClick={() => runReport('xlsx')}>Сгенерировать XLSX</button>
        </>
      )}

      {tab === 'audit' && (
        <>
          <button onClick={loadAudit}>Загрузить аудит (24ч)</button>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th align="left">Время</th>
                <th align="left">Актор</th>
                <th align="left">Действие</th>
                <th align="left">Объект</th>
                <th align="left">Детали</th>
                <th align="left">Хэш</th>
              </tr>
            </thead>
            <tbody>
              {audit.map((a, i) => (
                <tr key={i}>
                  <td>{a.ts_utc}</td>
                  <td>{a.actor_id}</td>
                  <td>{a.action}</td>
                  <td>{a.object_type}:{a.object_id}</td>
                  <td>{a.details}</td>
                  <td style={{ fontFamily: 'monospace' }}>{a.hash}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}

      {tab === 'wf' && (
        <>
          <h3>Старт изменения уставки</h3>
          <div>
            <input placeholder="projectId" value={wfProject} onChange={e=>setWfProject(e.target.value)} />
            <input placeholder="alarmId" value={wfAlarm} onChange={e=>setWfAlarm(e.target.value)} />
            <input placeholder="newThreshold" value={wfThreshold} onChange={e=>setWfThreshold(e.target.value)} />
            <input placeholder="reason" value={wfReason} onChange={e=>setWfReason(e.target.value)} />
            <input placeholder="passcode" value={wfPass} onChange={e=>setWfPass(e.target.value)} />
            <button onClick={startWf}>Старт</button>
          </div>
          <h3>Апрув</h3>
          <div>
            <input placeholder="workflowId" value={wfId} onChange={e=>setWfId(e.target.value)} />
            <input placeholder="passcode" value={wfApprovePass} onChange={e=>setWfApprovePass(e.target.value)} />
            <button onClick={approveWf}>Апрув</button>
          </div>
        </>
      )}
    </div>
  )
}
