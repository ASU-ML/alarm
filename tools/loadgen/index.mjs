import fetch from 'node-fetch'

const API = process.env.API || 'http://localhost:8443'
const PROJECT = process.env.PROJECT || 'demo-project'
const RATE = Number(process.env.RATE || 100) // events per minute
const DURATION_MIN = Number(process.env.DURATION || 1)

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

async function run(){
  let seq = 1
  const intervalMs = 60000/ RATE
  const end = Date.now() + DURATION_MIN*60000
  while(Date.now()<end){
    const batch = Array.from({length: 10}, ()=>mkEvent(seq++))
    try{
      const r = await fetch(`${API}/api/v1/ingest/events`,{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(batch) })
      if(!r.ok) console.error('ingest error', r.status)
    }catch(e){ console.error('error', e.message) }
    await sleep(intervalMs)
  }
}

run().then(()=>console.log('done'))
