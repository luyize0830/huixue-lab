// node web-demo/selftest.js -- executes the actual inline script, with no duplicated rules.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const { test } = require('node:test');

const html = fs.readFileSync(path.join(__dirname, 'index.html'), 'utf8');
const source = html.match(/<script>([\s\S]*?)<\/script>/)[1];
function boot(stored = null) {
  const context = vm.createContext({
    localStorage: { getItem: () => stored, setItem() {} },
    document: {
      getElementById: () => ({ innerHTML: '' }),
      querySelectorAll: () => [], addEventListener() {}
    },
    window: { scrollTo() {} }, setInterval, clearInterval
  });
  vm.runInContext(source, context, { timeout: 2000 });
  return expression => vm.runInContext(expression, context, { timeout: 2000 });
}

test('normal startup and the four tabs render', () => {
  assert.equal(boot()(`['home','pool','graph','mine'].every(s => {
    S.screen=s; return typeof view()==='string';
  })`), true);
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
