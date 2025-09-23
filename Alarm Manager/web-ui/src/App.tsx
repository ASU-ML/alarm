import { useEffect, useState } from 'react'
import { getKpiStandard, runReport as runReportApi, searchAudit, health, kcLogin, getToken, setToken, sseUrl, ingestBatch, runReportTemplate, getTrends } from './api'
import { Chart, LineController, LineElement, PointElement, LinearScale, TimeScale, Title, CategoryScale } from 'chart.js'

Chart.register(LineController, LineElement, PointElement, LinearScale, TimeScale, Title, CategoryScale)

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

function useTheme() {
  const [theme, setTheme] = useState<string>(() => localStorage.getItem('theme') || 'dark')
  useEffect(() => {
    const root = document.documentElement
    if (theme === 'dark') {
      root.style.setProperty('--bg', '#0f1115')
      root.style.setProperty('--fg', '#e5e7eb')
      root.style.setProperty('--muted', '#9aa4b2')
      root.style.setProperty('--card', '#1a1f29')
      root.style.setProperty('--accent', '#22d3ee')
    } else {
      root.style.setProperty('--bg', '#ffffff')
      root.style.setProperty('--fg', '#0b1220')
      root.style.setProperty('--muted', '#5b6471')
      root.style.setProperty('--card', '#f4f6f9')
      root.style.setProperty('--accent', '#0ea5e9')
    }
    localStorage.setItem('theme', theme)
  }, [theme])
  return { theme, setTheme }
}

export function App() {
  const { theme, setTheme } = useTheme()
  const [healthState, setHealthState] = useState('')
  const [feed, setFeed] = useState<EventItem[]>([])
  const [tab, setTab] = useState<'feed'|'kpi'|'reports'|'audit'|'wf'|'charts'|'reportsEditor'>('feed')
  const [kpi, setKpi] = useState<Kpi | null>(null)
  const [audit, setAudit] = useState<AuditItem[]>([])
  const [streamOk, setStreamOk] = useState<boolean>(false)

  const [kcBase, setKcBase] = useState('http://localhost:8444')
  const [kcRealm, setKcRealm] = useState('alarm')
  const [kcClient, setKcClient] = useState('alarm-ui')
  const [kcUser, setKcUser] = useState('operator1')
  const [kcPass, setKcPass] = useState('operator')

  const [demoProject, setDemoProject] = useState('demo-project')
  const [demoRate, setDemoRate] = useState(300)
  const [demoSeconds, setDemoSeconds] = useState(10)
  const [demoTagCount, setDemoTagCount] = useState(5)
  const [demoRunning, setDemoRunning] = useState(false)

  const [wfProject, setWfProject] = useState('')
  const [wfAlarm, setWfAlarm] = useState('')
  const [wfThreshold, setWfThreshold] = useState('')
  const [wfReason, setWfReason] = useState('')
  const [wfPass, setWfPass] = useState('')
  const [wfId, setWfId] = useState('')
  const [wfApprovePass, setWfApprovePass] = useState('')

  // Charts state
  const [chartTags, setChartTags] = useState('TAG_0,TAG_1')
  const [chartFrom, setChartFrom] = useState(() => new Date(Date.now()-3600*1000).toISOString())
  const [chartTo, setChartTo] = useState(() => new Date().toISOString())
  const [chartStep, setChartStep] = useState(60)
  const [chartData, setChartData] = useState<Record<string, [string, number][]>>({})

  // Report template editor state
  const metricOptions = [ { key:'alm_in_count', label:'ALM_IN count' }, { key:'ack_p95_s', label:'p95 ACK (s)' }, { key:'standing_alarms', label:'Standing alarms' } ]
  const [tplTitle, setTplTitle] = useState('Custom KPI')
  const [tplSections, setTplSections] = useState<{title:string, metrics:string[]}[]>([{ title:'Main', metrics:['alm_in_count','ack_p95_s'] }])
  const [tplFrom, setTplFrom] = useState(() => new Date(Date.now()-24*3600*1000).toISOString())
  const [tplTo, setTplTo] = useState(() => new Date().toISOString())
  const [tplFormat, setTplFormat] = useState<'pdf'|'xlsx'>('pdf')

  useEffect(() => {
    health().then(j => setHealthState(j.status)).catch(() => setHealthState('ОШИБКА'))
  }, [])

  useEffect(() => {
    let es: EventSource | null = null
    let attempt = 0
    let stopped = false

    const connect = () => {
      if (stopped) return
      const url = sseUrl('/api/v1/events/stream')
      es = new EventSource(url)
      es.addEventListener('open', () => { setStreamOk(true); attempt = 0 })
      es.addEventListener('alarm_event', ev => {
        const e = ev as MessageEvent
        try { setFeed(prev => [JSON.parse(e.data) as EventItem, ...prev].slice(0, 200)) } catch {}
      })
      es.onerror = () => {
        setStreamOk(false)
        es?.close()
        const delay = Math.min(30000, 1000 * Math.pow(2, attempt++))
        setTimeout(connect, delay)
      }
    }

    connect()
    return () => { stopped = true; es?.close() }
  }, [])

  const login = async () => {
    await kcLogin({ baseUrl: kcBase, realm: kcRealm, clientId: kcClient, username: kcUser, password: kcPass })
    // пересоздадим SSE с токеном
    location.reload()
  }

  const logout = () => { setToken(null); location.reload() }

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

  const startDemo = async () => {
    if (!getToken()) { alert('Сначала войдите (Keycloak)'); return }
    setDemoRunning(true)
    const total = Math.max(1, Math.floor(demoRate * (demoSeconds/60)))
    let seq = 1
    const mk = () => ({
      eventType: 'ALM_IN',
      alarmTag: 'TAG_'+((seq++)%Math.max(1, demoTagCount)),
      value: Math.random()*100,
      note: null,
      sourceId: 'ui-demo',
      sourceSeq: Date.now()+seq,
      tsUtc: new Date().toISOString()
    })
    const batchSize = 20
    const batches = Math.ceil(total / batchSize)
    for (let i=0;i<batches;i++) {
      const slice = Array.from({length: Math.min(batchSize, total - i*batchSize)}, mk)
      try { await ingestBatch(demoProject, slice) } catch(e){ console.error(e) }
      await new Promise(r=>setTimeout(r, 1000))
    }
    setDemoRunning(false)
  }

  const loadCharts = async () => {
    const tags = chartTags.split(',').map(s=>s.trim()).filter(Boolean)
    const res = await getTrends(tags, chartFrom, chartTo, chartStep)
    setChartData(res.series)
  }

  // Scale grouping by average value per tag
  const computeGroups = () => {
    const entries = Object.entries(chartData)
    const withAvg = entries.map(([tag, points]) => {
      const avg = points.length ? points.reduce((a, [,v]) => a + (v||0), 0) / points.length : 0
      return { tag, avg, points }
    })
    withAvg.sort((a,b)=>a.avg-b.avg)
    const groups: { avg:number, series:{tag:string,points:[string,number][]}[] }[] = []
    const threshold = 0.2 // 20% окно для одной группы относительно базового avg
    withAvg.forEach(s => {
      const g = groups.find(gr => Math.abs(s.avg - gr.avg) / (gr.avg || 1) < threshold)
      if (g) g.series.push({ tag:s.tag, points:s.points })
      else groups.push({ avg: s.avg || 0, series: [{ tag:s.tag, points:s.points }] })
    })
    return groups
  }

  const addSection = () => setTplSections(prev => [...prev, { title:`S${prev.length+1}`, metrics: [] }])
  const runTemplateReport = async () => {
    const file = await runReportTemplate({ title: tplTitle, sections: tplSections, format: tplFormat, from: tplFrom, to: tplTo })
    alert(`Файл отчёта: ${file.file}`)
  }

  const btn = { padding: '6px 10px', background: 'var(--card)', color: 'var(--fg)', border: '1px solid rgba(255,255,255,.1)', borderRadius: 6, cursor: 'pointer', marginRight: 6 }
  const page = { background: 'var(--bg)', color: 'var(--fg)', minHeight: '100vh' }

  return (
    <div style={{ ...page, padding: 16 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <h2 style={{ margin: 0 }}>Менеджер сигнализаций</h2>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 8, alignItems: 'center' }}>
          <span style={{ fontSize: 12, color: 'var(--muted)' }}>{getToken() ? 'Авторизован' : 'Гость'}</span>
          {!getToken() ? (
            <>
              <input placeholder="KC URL" value={kcBase} onChange={e=>setKcBase(e.target.value)} style={{ width: 160 }} />
              <input placeholder="realm" value={kcRealm} onChange={e=>setKcRealm(e.target.value)} style={{ width: 80 }} />
              <input placeholder="clientId" value={kcClient} onChange={e=>setKcClient(e.target.value)} style={{ width: 100 }} />
              <input placeholder="user" value={kcUser} onChange={e=>setKcUser(e.target.value)} style={{ width: 100 }} />
              <input placeholder="pass" type="password" value={kcPass} onChange={e=>setKcPass(e.target.value)} style={{ width: 100 }} />
              <button style={btn} onClick={login}>Войти</button>
            </>
          ) : (
            <button style={btn} onClick={logout}>Выйти</button>
          )}
          <button style={btn} onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? 'Светлая' : 'Тёмная'}</button>
        </div>
      </div>
      <p>Состояние backend: {healthState || '...'}</p>
      <p style={{ color: streamOk ? '#22c55e' : '#f59e0b' }}>Поток событий: {streamOk ? 'подключен' : 'переподключение...'}</p>
      <div style={{ marginBottom: 12 }}>
        <button style={btn} onClick={() => setTab('feed')}>Лента</button>
        <button style={btn} onClick={() => setTab('kpi')}>KPI</button>
        <button style={btn} onClick={() => setTab('reports')}>Отчёты</button>
        <button style={btn} onClick={() => setTab('reportsEditor')}>Редактор отчётов</button>
        <button style={btn} onClick={() => setTab('charts')}>Графики</button>
        <button style={btn} onClick={() => setTab('audit')}>Аудит</button>
        <button style={btn} onClick={() => setTab('wf')}>Workflow</button>
      </div>

      {tab === 'feed' && (
        <>
          <h3>Живая лента</h3>
          <div style={{ marginBottom: 8, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input placeholder="project" value={demoProject} onChange={e=>setDemoProject(e.target.value)} />
            <input placeholder="rate/min" type="number" value={demoRate} onChange={e=>setDemoRate(parseInt(e.target.value||'0'))} />
            <input placeholder="seconds" type="number" value={demoSeconds} onChange={e=>setDemoSeconds(parseInt(e.target.value||'0'))} />
            <input placeholder="tags" type="number" value={demoTagCount} onChange={e=>setDemoTagCount(parseInt(e.target.value||'0'))} />
            <button style={btn} disabled={demoRunning} onClick={startDemo}>{demoRunning? 'Идёт...' : 'Старт демо'}</button>
          </div>
          <div style={{ overflowX: 'auto', border: '1px solid rgba(255,255,255,.1)', borderRadius: 8 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead style={{ background: 'var(--card)' }}>
                <tr>
                  <th align="left" style={{ padding: 8 }}>Время (UTC)</th>
                  <th align="left" style={{ padding: 8 }}>Тип</th>
                  <th align="left" style={{ padding: 8 }}>Alarm</th>
                  <th align="left" style={{ padding: 8 }}>Значение</th>
                  <th align="left" style={{ padding: 8 }}>Проект</th>
                  <th align="left" style={{ padding: 8 }}>Заметка</th>
                </tr>
              </thead>
              <tbody>
                {feed.map((e, i) => (
                  <tr key={i}>
                    <td style={{ padding: 8 }}>{e.ts_utc}</td>
                    <td style={{ padding: 8 }}>{e.event_type}</td>
                    <td style={{ padding: 8 }}>{e.alarm_id || ''}</td>
                    <td style={{ padding: 8 }}>{e.value ?? ''}</td>
                    <td style={{ padding: 8 }}>{e.project_id}</td>
                    <td style={{ padding: 8 }}>{e.note || ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'kpi' && (
        <>
          <button style={btn} onClick={loadKpi}>Загрузить KPI за 24ч</button>
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
          <button style={btn} onClick={() => runReport('pdf')}>Сгенерировать PDF</button>
          <button style={btn} onClick={() => runReport('xlsx')}>Сгенерировать XLSX</button>
        </>
      )}

      {tab === 'audit' && (
        <>
          <button style={btn} onClick={loadAudit}>Загрузить аудит (24ч)</button>
          <div style={{ overflowX: 'auto', border: '1px solid rgba(255,255,255,.1)', borderRadius: 8 }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead style={{ background: 'var(--card)' }}>
                <tr>
                  <th align="left" style={{ padding: 8 }}>Время</th>
                  <th align="left" style={{ padding: 8 }}>Актор</th>
                  <th align="left" style={{ padding: 8 }}>Действие</th>
                  <th align="left" style={{ padding: 8 }}>Объект</th>
                  <th align="left" style={{ padding: 8 }}>Детали</th>
                  <th align="left" style={{ padding: 8 }}>Хэш</th>
                </tr>
              </thead>
              <tbody>
                {audit.map((a, i) => (
                  <tr key={i}>
                    <td style={{ padding: 8 }}>{a.ts_utc}</td>
                    <td style={{ padding: 8 }}>{a.actor_id}</td>
                    <td style={{ padding: 8 }}>{a.action}</td>
                    <td style={{ padding: 8 }}>{a.object_type}:{a.object_id}</td>
                    <td style={{ padding: 8 }}>{a.details}</td>
                    <td style={{ padding: 8, fontFamily: 'monospace' }}>{a.hash}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'wf' && (
        <>
          <h3>Старт изменения уставки</h3>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <input placeholder="projectId" value={wfProject} onChange={e=>setWfProject(e.target.value)} />
            <input placeholder="alarmId" value={wfAlarm} onChange={e=>setWfAlarm(e.target.value)} />
            <input placeholder="newThreshold" value={wfThreshold} onChange={e=>setWfThreshold(e.target.value)} />
            <input placeholder="reason" value={wfReason} onChange={e=>setWfReason(e.target.value)} />
            <input placeholder="passcode" value={wfPass} onChange={e=>setWfPass(e.target.value)} />
            <button style={btn} onClick={startWf}>Старт</button>
          </div>
          <h3>Апрув</h3>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <input placeholder="workflowId" value={wfId} onChange={e=>setWfId(e.target.value)} />
            <input placeholder="passcode" value={wfApprovePass} onChange={e=>setWfApprovePass(e.target.value)} />
            <button style={btn} onClick={approveWf}>Апрув</button>
          </div>
        </>
      )}

      {tab === 'charts' && (
        <>
          <h3>Графики</h3>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
            <input placeholder="tags (comma)" value={chartTags} onChange={e=>setChartTags(e.target.value)} />
            <input placeholder="from" value={chartFrom} onChange={e=>setChartFrom(e.target.value)} style={{ width: 260 }} />
            <input placeholder="to" value={chartTo} onChange={e=>setChartTo(e.target.value)} style={{ width: 260 }} />
            <input placeholder="step (sec)" type="number" value={chartStep} onChange={e=>setChartStep(parseInt(e.target.value||'60'))} />
            <button style={btn} onClick={loadCharts}>Загрузить</button>
          </div>
          {Object.keys(chartData).length === 0 ? <p>Нет данных</p> : (
            computeGroups().map((g, i) => (
              <div key={i} style={{ marginBottom: 16, padding: 8, border: '1px solid rgba(255,255,255,.1)', borderRadius: 8 }}>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>Группа средн.: {g.avg.toFixed(2)}</div>
                <div style={{ overflowX: 'auto' }}>
                  <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                    <thead style={{ background: 'var(--card)' }}>
                      <tr>
                        <th align="left" style={{ padding: 6 }}>Время</th>
                        {g.series.map(s => (<th key={s.tag} align="left" style={{ padding: 6 }}>{s.tag}</th>))}
                      </tr>
                    </thead>
                    <tbody>
                      {(() => {
                        const times = new Set<string>()
                        g.series.forEach(s => s.points.forEach(p => times.add(p[0])))
                        const sorted = Array.from(times).sort()
                        return sorted.map(t => (
                          <tr key={t}>
                            <td style={{ padding: 6 }}>{t}</td>
                            {g.series.map(s => {
                              const found = s.points.find(p => p[0]===t)
                              return <td key={s.tag+ t} style={{ padding: 6 }}>{found? found[1].toFixed(2): ''}</td>
                            })}
                          </tr>
                        ))
                      })()}
                    </tbody>
                  </table>
                </div>
              </div>
            ))
          )}
        </>
      )}

      {tab === 'reportsEditor' && (
        <>
          <h3>Редактор отчётов</h3>
          <div style={{ display:'flex', gap:8, flexWrap:'wrap', marginBottom:8 }}>
            <input placeholder="Заголовок" value={tplTitle} onChange={e=>setTplTitle(e.target.value)} />
            <input placeholder="from" value={tplFrom} onChange={e=>setTplFrom(e.target.value)} style={{ width: 260 }} />
            <input placeholder="to" value={tplTo} onChange={e=>setTplTo(e.target.value)} style={{ width: 260 }} />
            <select value={tplFormat} onChange={e=>setTplFormat(e.target.value as any)}>
              <option value="pdf">PDF</option>
              <option value="xlsx">XLSX</option>
            </select>
            <button style={btn} onClick={addSection}>Добавить секцию</button>
            <button style={btn} onClick={runTemplateReport}>Сформировать</button>
          </div>
          {tplSections.map((s, idx) => (
            <div key={idx} style={{ marginBottom: 12, padding: 8, border:'1px solid rgba(255,255,255,.1)', borderRadius:8 }}>
              <input placeholder="Название секции" value={s.title} onChange={e=>{
                const v = e.target.value; setTplSections(prev => prev.map((x,i)=> i===idx? {...x, title:v}: x))
              }} />
              <div style={{ display:'flex', gap:8, flexWrap:'wrap', marginTop: 6 }}>
                {metricOptions.map(m => (
                  <label key={m.key} style={{ display:'flex', alignItems:'center', gap:4 }}>
                    <input type="checkbox" checked={s.metrics.includes(m.key)} onChange={e=>{
                      const checked = e.target.checked
                      setTplSections(prev => prev.map((x,i)=> i===idx? {...x, metrics: checked? Array.from(new Set([...x.metrics, m.key])) : x.metrics.filter(k=>k!==m.key)}: x))
                    }} />{m.label}
                  </label>
                ))}
              </div>
            </div>
          ))}
        </>
      )}
    </div>
  )
}
