// node web-demo/selftest.js -- executes the actual inline script, with no duplicated rules.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
function boot(stored = null) {
  const storage = new Map(stored == null ? [] : [['huixue_web_v1', stored]]);
  const context = vm.createContext({
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    document: {
      getElementById: () => ({ innerHTML: '' }),
      querySelectorAll: () => [], addEventListener() {}
    },
    window: { scrollTo() {} }, TextEncoder, confirm: () => true,
    setInterval: () => 1, clearInterval() {}
  });
  vm.runInContext(source, context, { timeout: 2000 });
  return expression => vm.runInContext(expression, context, { timeout: 2000 });
}

test('normal startup and the four tabs render', () => {
  assert.equal(boot()(`['home','pool','graph','mine'].every(s => {
    S.screen=s; return typeof view()==='string';
  })`), true);
});

test('a session survives reload and preserves its original action snapshot', () => {
  const run=boot();
  run(`startAction(POOL.find(a=>a.id==='water'));stopTimer();
    const original=JSON.stringify(data.session.action);
    data.actions=data.actions.map(a=>a.id==='water'?{...a,t:'修改后的水'}:a);save();`);
  const stored=run('localStorage.getItem(KEY)');
  const restored=boot(stored);
  assert.equal(restored('data.session.action.t'), '接一杯水慢慢喝');
  assert.equal(restored('data.actions.find(a=>a.id==="water").t'), '修改后的水');
  assert.equal(restored('validWebSession(data.session)'), true);
});

test('deadline remaining handles suspended timers, completion and pause', () => {
  const run=boot();
  assert.equal(run(`webRemaining({action:{m:1},phase:'RUNNING',deadline:100000},70000)`),30);
  assert.equal(run(`webRemaining({action:{m:1},phase:'RUNNING',deadline:100000},120000)`),0);
  assert.equal(run(`webRemaining({action:{m:1},phase:'PAUSED',pausedSeconds:23},120000)`),23);
});

test('archived actions stay in history but never return in recommendations', () => {
  const run=boot();
  assert.equal(run(`(() => {
    data.records=[{id:'water',action:{...POOL.find(a=>a.id==='water')},rate:4,ts:123456,hour:12}];
    commitWeb({...data,archived:POOL.map(a=>a.id)});
    return recsFor(10,false,[]).length===0&&data.records.length===1&&viewGraph().includes('接一杯水慢慢喝');
  })()`),true);
});

test('feedback saves once and captures the action at session start', () => {
  const run=boot();
  assert.equal(run(`(() => {
    startAction(POOL.find(a=>a.id==='water'));finishWebSession();
    S.rateSel=4;saveWebFeedback();saveWebFeedback();
    return data.records.length===1&&data.records[0].action.t==='接一杯水慢慢喝'&&data.session===null;
  })()`),true);
});

test('backup round trip and repeated import do not duplicate records', () => {
  const run=boot();
  assert.equal(run(`(() => {
    startAction(POOL.find(a=>a.id==='water'));finishWebSession();S.rateSel=5;saveWebFeedback();
    const backup=webBackupText();const next=decodeWebBackup(backup);
    importWebBackup(next);importWebBackup(decodeWebBackup(backup));
    return data.records.length===1&&!!localStorage.getItem(KEY+'_before_import');
  })()`),true);
});

test('incomplete backups, duplicate sessions and invalid catalog entries are rejected', () => {
  const run=boot();
  assert.equal(run(`(() => {
    const failures=[]; const b=JSON.parse(webBackupText());
    for(const mutate of [o=>{o.version=9},o=>{o.platform='android'},o=>{o.data.actions[0].m=0},
      o=>{delete o.data.favorites},o=>{o.data.actions.push({...o.data.actions[0]})},
      o=>{o.data.favorites=['missing']},o=>{o.data.records=[{id:'water',rate:9,hour:12,ts:1}]}]){
      const o=JSON.parse(JSON.stringify(b));mutate(o);
      try{decodeWebBackup(JSON.stringify(o));failures.push(false)}catch(e){failures.push(true)}
    }
    return failures.every(Boolean)&&data.records.length===0;
  })()`),true);
});

test('user-authored action titles and cues are escaped in all main views', () => {
  const run=boot();
  assert.equal(run(`(() => {
    const a={id:'custom_xss',t:'<img src=x onerror=alert(1)>',m:1,c:'CALM',cue:'<script>alert(1)</script>'};
    commitWeb({...data,actions:[...POOL,a]});S.poolFilter='ALL';
    const pool=viewPool();startAction(a);stopTimer();const timer=viewTimer();
    return !pool.includes('<img src=x')&&!pool.includes('<script>')&&!timer.includes('<script>')&&pool.includes('&lt;script&gt;');
  })()`),true);
});

test('storage failure does not replace the in-memory snapshot during import', () => {
  const run=boot();
  assert.equal(run(`(() => {
    const before=JSON.stringify(data);const next=decodeWebBackup(webBackupText());next.records=[];
    localStorage.setItem=()=>{throw Error('storage full')};
    try{importWebBackup(next)}catch(e){}
    return JSON.stringify(data)===before;
  })()`),true);
});

test('all companion expressions and category illustrations resolve to bundled files', () => {
  const run=boot();
  const snippets=JSON.parse(run(`JSON.stringify([
    ...['happy','idle','worried'].map(m=>girl(m,76)),
    ...Object.keys(CATS).map(c=>categoryArt(c))
  ])`));
  for(const snippet of snippets){
    const src=snippet.match(/src="([^"]+)"/)[1];
    assert.ok(fs.existsSync(path.join(__dirname,src)),src);
  }
});

test('one-minute budget is respected', () => {
  assert.equal(boot()('recsFor(1, false, []).every(a => a.m <= 1)'), true);
});

test('zero-minute budget returns an honest empty state', () => {
  const run = boot();
  assert.equal(run('reply("只剩0分钟").recs.length'), 0);
  assert.equal(run('reply("只剩0分钟").text.includes("没剩下合适的")'), true);
});

test('library restriction survives candidate exhaustion', () => {
  assert.equal(boot()(`(() => {
    data.demo.hour=12; S.chatScene='在图书馆';
    const excluded=POOL.filter(a => !['SOCIAL','REST'].includes(a.c)).map(a => a.id);
    return recsFor(5,false,excluded).length;
  })()`), 0);
});

test('night fallback keeps budget and exclusions', () => {
  assert.equal(boot()(`(() => {
    const excluded=['cold','water'];
    return recsFor(1,true,excluded).every(a => a.m<=1 && !excluded.includes(a.id));
  })()`), true);
});

test('night candidates stay quiet when enough are available', () => {
  assert.equal(boot()('recsFor(5,true,[]).every(a => ["REST","CALM","FUEL"].includes(a.c))'), true);
});

test('invalid stored JSON and unexpected root types recover', () => {
  for (const stored of ['null','[]','42','"bad"','{broken']) {
    assert.equal(boot(stored)('Array.isArray(data.records) && Number.isFinite(data.demo.hour)'), true);
  }
});

test('partial storage recovers missing fields', () => {
  assert.equal(boot('{"records":[]}')('data.demo.focus'), 125);
});

test('malformed records are dropped and valid records survive', () => {
  const valid = { id:'water', rate:4, hour:12, ts:123456 };
  const stored = JSON.stringify({ records:[valid,null,{}, { ...valid, rate:99 }], demo:{} });
  assert.equal(boot(stored)('data.records.length'), 1);
  assert.equal(boot(stored)('data.records[0].rate'), 4);
});

test('invalid demo fields do not produce NaN or impossible hours', () => {
  const run=boot(JSON.stringify({records:[], demo:{ focus:'oops', screen:-4, hour:99 }}));
  assert.equal(run('data.demo.focus'), 125);
  assert.equal(run('data.demo.screen'), 0);
  assert.equal(run('data.demo.hour'), 23);
});

test('sleep shortage lowers the state score like the Android rules', () => {
  const short=boot(JSON.stringify({records:[], demo:{ focus:0, screen:0, hour:12, sleep:270 }}));
  assert.equal(short('assess().score'), 84);
  assert.equal(short('assess().reasons.some(r => r.includes("没睡够"))'), true);
  const enough=boot(JSON.stringify({records:[], demo:{ focus:0, screen:0, hour:12, sleep:480 }}));
  assert.equal(enough('assess().score'), 100);
  assert.equal(enough('data.demo.sleep'), 480);
});
