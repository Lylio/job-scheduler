import React, {useEffect, useRef, useState} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';
const zone='Europe/London';
const time=value=>value ? new Intl.DateTimeFormat('en-GB',{dateStyle:'medium',timeStyle:'medium',timeZone:zone}).format(new Date(value)) : '—';
const duration=value=>value==null?'—':value<1000?`${value} ms`:`${(value/1000).toFixed(1)} s`;
const labels={REPORT_DEMO:'Sample report',SLOW_DEMO:'Slow processing',FAILURE_DEMO:'Intentional failure'};
async function api(path, options={}) {
 const response=await fetch('/api'+path,{...options,headers:{'Content-Type':'application/json',...options.headers}});
 if(!response.ok) { const data=await response.json().catch(()=>({})); throw new Error(data.detail||data.message||`Request failed (${response.status})`); }
 return response.status===204||response.status===202?null:response.json();
}
function Badge({status}) { return <span className={`badge ${status?.toLowerCase()||'idle'}`}><span className="dot"/>{status||'NOT RUN'}</span>; }
function App() {
 const [page,setPage]=useState('Overview'),[jobs,setJobs]=useState([]),[runs,setRuns]=useState([]);
 const [loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(''),[updated,setUpdated]=useState(null);
 const [modal,setModal]=useState(false),[selected,setSelected]=useState(null),[pending,setPending]=useState(''),[filter,setFilter]=useState('ALL');
 const busy=useRef(false),alive=useRef(true);
 async function refresh() {
  if(busy.current) return; busy.current=true;
  try { const [j,r]=await Promise.all([api('/jobs'),api('/executions?limit=200')]); if(alive.current) { setJobs(j);setRuns(r);setUpdated(new Date());setError(''); } }
  catch(e) { if(alive.current) setError(e.message); }
  finally { busy.current=false;if(alive.current) setLoading(false); }
 }
 useEffect(()=>{alive.current=true;refresh();const timer=setInterval(refresh,3000);return()=>{alive.current=false;clearInterval(timer);};},[]);
 useEffect(()=>{if(!notice)return;const timer=setTimeout(()=>setNotice(''),6000);return()=>clearTimeout(timer);},[notice]);
 async function action(id,kind,body) {
  setPending(id);setError('');
  try {await api(`/jobs/${id}/${kind}`,{method:kind==='run'?'POST':'PUT',...(body?{body:JSON.stringify(body)}:{})});setNotice(kind==='run'?'Execution requested. It may queue while this job is running.':body.enabled?'Schedule resumed.':'Schedule paused. An active or queued run may still finish.');await refresh();}
  catch(e){setError(e.message);}finally{setPending('');}
 }
 const completed=runs.filter(r=>['SUCCESS','FAILED'].includes(r.status));
 const successes=completed.filter(r=>r.status==='SUCCESS').length;
 const running=runs.filter(r=>r.status==='RUNNING').length;
 const today=new Intl.DateTimeFormat('en-CA',{timeZone:zone}).format(new Date());
 const failedToday=runs.filter(r=>r.status==='FAILED'&&new Intl.DateTimeFormat('en-CA',{timeZone:zone}).format(new Date(r.startedAt))===today).length;
 const visible=runs.filter(r=>filter==='ALL'||r.status===filter);
 const recent=runs.slice(0,8);
 return <div className="shell">
  <aside><a className="brand" href="#" onClick={e=>{e.preventDefault();setPage('Overview');}}><span className="brand-icon">◷</span> Job Scheduler</a>
   <div className="nav-caption">WORKSPACE</div><nav>{['Overview','Jobs','Execution History'].map((name,i)=><button className={page===name?'nav active':'nav'} key={name} onClick={()=>setPage(name)}><span>{['▦','☷','◴'][i]}</span>{name}</button>)}</nav>
   <div className="sidebar-bottom"><span className="dot"/> SINGLE INSTANCE<br/><small>Java · Spring Boot · Quartz</small></div>
  </aside>
  <div className="workspace"><header><span>WORKSPACE <span className="slash">/</span> {page}</span><span className="environment">LOCAL DEVELOPMENT</span></header>
   <main><div className="heading"><div><div className="eyebrow">BACKGROUND OPERATIONS</div><h1>{page==='Overview'?'Execution dashboard':page}</h1><p>Schedule tasks. Track every run. Investigate failures.</p></div><button className="primary" onClick={()=>setModal(true)}>+ Create job</button></div>
   {error&&<div className="alert error" role="alert">{error} <button onClick={refresh}>Refresh</button></div>}
   {notice&&<div className="alert" role="status">{notice}</div>}
   {loading?<div className="panel empty">Connecting to scheduler…</div>:<>
   {page==='Overview'&&<><div className="stats">
    <Stat label="Active schedules" value={jobs.filter(j=>j.enabled).length} note={`${jobs.length} jobs configured`} icon="◷"/>
    <Stat label="Running now" value={running} note="Up to 4 worker threads" icon="▷"/>
    <Stat label="Success rate" value={completed.length?`${(100*successes/completed.length).toFixed(1)}%`:'—'} note="Latest 200 executions · completed only" icon="↗"/>
    <Stat label="Failed today" value={failedToday} note="Latest 200 executions · London time" icon="!" danger/>
   </div></>}
   {page!=='Execution History'&&<section className="panel"><div className="panel-heading"><div><h2>Scheduled jobs <span className="count">{jobs.length}</span></h2><p>Quartz cron schedules · each job uses its configured timezone</p></div><span className="live"><span className="dot"/> LIVE</span></div>
    <div className="table-scroll"><table><thead><tr><th>Job name</th><th>Schedule</th><th>Last execution</th><th>Next run</th><th>Actions</th></tr></thead><tbody>
     {jobs.map(job=><tr key={job.id}><td><strong>{job.name}</strong><small>{labels[job.taskType]} {!job.enabled&&'· Paused'}</small></td><td><code>{job.cronExpression}</code><small>{job.zoneId}</small></td><td><Badge status={job.lastExecution?.status}/></td><td className="date">{time(job.nextRun)}</td><td><div className="actions"><button disabled={!job.enabled||pending===job.id} onClick={()=>action(job.id,'run')}>▷ Run now</button><button disabled={pending===job.id} onClick={()=>action(job.id,'enabled',{enabled:!job.enabled})}>{job.enabled?'Pause':'Resume'}</button></div></td></tr>)}
    </tbody></table>{jobs.length===0&&<div className="empty">No jobs yet. Create your first scheduled task.</div>}</div>
   </section>}
   {page==='Overview'&&<div className="bottom-grid"><section className="panel"><div className="panel-heading"><h2>Recent executions</h2><button className="text-button" onClick={()=>setPage('Execution History')}>View history →</button></div><RunTable runs={recent} select={setSelected}/></section><Activity runs={runs}/></div>}
   {page==='Execution History'&&<section className="panel"><div className="panel-heading"><div><h2>Execution log</h2><p>Latest 200 runs. Select a row to inspect its result.</p></div><select aria-label="Filter status" value={filter} onChange={e=>setFilter(e.target.value)}>{['ALL','RUNNING','SUCCESS','FAILED','INTERRUPTED'].map(s=><option key={s}>{s}</option>)}</select></div><RunTable runs={visible} select={setSelected}/></section>}
   </>}
   <div className="demo-note"><strong>Demo task types</strong> Report and slow tasks simulate successful work. The failing import deliberately throws an error so you can inspect a failure. No real files or business data are changed.</div>
   </main><footer><span><span className={`dot ${error?'offline':''}`}/>{error?'Connection unavailable':'Auto-refresh every 3 seconds'}</span><span>Updated {updated?updated.toLocaleTimeString('en-GB',{timeZone:zone}):'—'} · Europe/London</span></footer>
  </div>
  {modal&&<CreateJob close={()=>setModal(false)} saved={async()=>{setModal(false);setNotice('Job created and scheduled.');await refresh();}}/>}
  {selected&&<Dialog title="Execution details" close={()=>setSelected(null)}><h3>{selected.jobName}</h3><Badge status={runs.find(r=>r.id===selected.id)?.status||selected.status}/><ExecutionDetails run={runs.find(r=>r.id===selected.id)||selected}/></Dialog>}
 </div>;
}
function Stat({label,value,note,icon,danger}){return <div className="stat"><span className={`stat-icon ${danger?'red':''}`}>{icon}</span><div><p>{label}</p><strong>{value}</strong><small>{note}</small></div></div>;}
function RunTable({runs,select}){return <div className="table-scroll"><table><thead><tr><th>Job</th><th>Status</th><th>Started</th><th>Duration</th><th/></tr></thead><tbody>{runs.map(r=><tr key={r.id}><td><strong>{r.jobName}</strong><small>{r.source}</small></td><td><Badge status={r.status}/></td><td className="date">{time(r.startedAt)}</td><td>{duration(r.durationMs)}</td><td><button className="text-button" onClick={()=>select(r)} aria-label={`Details for ${r.jobName} at ${time(r.startedAt)}`}>Details</button></td></tr>)}</tbody></table>{runs.length===0&&<div className="empty">No executions yet. Use “Run now” or wait for a schedule.</div>}</div>;}
function Activity({runs}){
 const groups=Array.from({length:7},(_,i)=>{const date=new Date(Date.now()-(6-i)*86400000);const key=new Intl.DateTimeFormat('en-CA',{timeZone:zone}).format(date);const day=runs.filter(r=>new Intl.DateTimeFormat('en-CA',{timeZone:zone}).format(new Date(r.startedAt))===key);return {label:new Intl.DateTimeFormat('en-GB',{weekday:'short',timeZone:zone}).format(date),success:day.filter(r=>r.status==='SUCCESS').length,failed:day.filter(r=>r.status==='FAILED').length};});
 const max=Math.max(1,...groups.map(g=>g.success+g.failed));
 return <section className="panel"><div className="panel-heading"><div><h2>Execution activity</h2><p>Last 7 days · within latest 200 executions</p></div></div><div className="legend"><span><i/> Success</span><span><i className="red"/> Failed</span></div><div className="chart" aria-label="Daily execution counts">{groups.map((g,i)=><div className="chart-column" key={i}><span className="chart-value">{g.success+g.failed}</span><div className="bar-track"><div className="bar failed-bar" style={{height:`${g.failed/max*100}%`}}/><div className="bar" style={{height:`${g.success/max*100}%`}}/></div><small>{g.label}</small></div>)}</div></section>;
}
function Dialog({title,close,children}){
 const ref=useRef();
 useEffect(()=>{const dialog=ref.current;dialog.showModal();return()=>dialog.close();},[]);
 return <dialog ref={ref} onCancel={close} onClick={e=>{if(e.target===ref.current)close();}}><div className="dialog-heading"><h2>{title}</h2><button onClick={close} aria-label="Close dialog">✕</button></div>{children}</dialog>;
}
function ExecutionDetails({run}){return <><dl><dt>Execution ID</dt><dd className="mono">{run.id}</dd><dt>Started</dt><dd>{time(run.startedAt)}</dd><dt>Finished</dt><dd>{time(run.finishedAt)}</dd><dt>Duration</dt><dd>{duration(run.durationMs)}</dd><dt>Triggered by</dt><dd>{run.source}</dd></dl><h4>Result / error</h4><pre>{run.message||'Task is still running…'}</pre></>;}
function CreateJob({close,saved}){
 const [form,setForm]=useState({name:'',taskType:'REPORT_DEMO',cronExpression:'0 */5 * * * ?',zoneId:'Europe/London'}),[error,setError]=useState(''),[saving,setSaving]=useState(false);
 function change(e){setForm({...form,[e.target.name]:e.target.value});}
 async function submit(e){e.preventDefault();setSaving(true);setError('');try{await api('/jobs',{method:'POST',body:JSON.stringify(form)});await saved();}catch(e){setError(e.message);}finally{setSaving(false);}}
 return <Dialog title="Create scheduled job" close={()=>{if(!saving)close();}}><form onSubmit={submit}>
  <p>Choose a demo task and tell Quartz when to run it.</p>{error&&<div className="alert error" role="alert">{error}</div>}
  <label>Job name<input autoFocus required maxLength={100} name="name" value={form.name} onChange={change} placeholder="e.g. Evening report"/></label>
  <label>Task type<select name="taskType" value={form.taskType} onChange={change}>{Object.entries(labels).map(([value,label])=><option key={value} value={value}>{label}</option>)}</select></label>
  <label>Quartz cron expression<input required maxLength={100} name="cronExpression" className="mono" value={form.cronExpression} onChange={change}/></label>
  <div className="presets">{[['Every minute','0 * * * * ?'],['Every 5 minutes','0 */5 * * * ?'],['Daily 21:00','0 0 21 * * ?']].map(([label,value])=><button type="button" key={label} onClick={()=>setForm({...form,cronExpression:value})}>{label}</button>)}</div>
  <small>Six fields: second, minute, hour, day, month, weekday. Quartz uses “?” for an unspecified day field.</small>
  <label>Timezone<input required name="zoneId" maxLength={80} value={form.zoneId} onChange={change}/></label>
  <div className="dialog-actions"><button type="button" disabled={saving} onClick={close}>Cancel</button><button disabled={saving} className="primary">{saving?'Creating…':'Create job'}</button></div>
 </form></Dialog>;
}
createRoot(document.getElementById('root')).render(<App/>);
