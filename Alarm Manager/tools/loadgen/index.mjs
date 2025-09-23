import fetch from 'node-fetch'

const API = process.env.API || 'http://localhost:8443'
const PROJECT = process.env.PROJECT || 'demo-project'
const RATE = Number(process.env.RATE || 100) // events per minute
const DURATION_MIN = Number(process.env.DURATION || 1)
const ACCESS_TOKEN = process.env.ACCESS_TOKEN || null

function sleep(ms){ return new Promise(r=>setTimeout(r,ms)) }

function mkEvent(seq){
  const now = new Date().toISOString()
  return {
    eventType: 'ALM_IN',
    alarmTag: 'TAG_'+(seq%10),
    value: Math.random()*100,
    note: null,
    sourceId: 'loadgen',
    sourceSeq: seq,
    tsUtc: now,
    projectId: PROJECT
  }
}

async function postBatch(batch){
  const url = new URL(`${API}/api/v1/ingest/events`)
  if (ACCESS_TOKEN) url.searchParams.set('access_token', ACCESS_TOKEN)
  const r = await fetch(url.toString(),{ method:'POST', headers:{ 'Content-Type':'application/json', ...(ACCESS_TOKEN?{}:{}) }, body: JSON.stringify(batch) })
  if(!r.ok) {
    const text = await r.text().catch(()=> '')
    console.error('ingest error', r.status, text)
  }
}

async function run(){
  let seq = 1
  const intervalMs = Math.max(1, Math.floor(60000/ Math.max(1, RATE)))
  const end = Date.now() + DURATION_MIN*60000
  while(Date.now()<end){
    const batch = Array.from({length: 10}, ()=>mkEvent(seq++))
    try{ await postBatch(batch) }catch(e){ console.error('error', e.message) }
    await sleep(intervalMs)
  }
}

run().then(()=>console.log('done'))
