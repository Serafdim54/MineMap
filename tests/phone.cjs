const browserName = process.env.MINEMAP_BROWSER || 'chromium';
const browserType = require('playwright')[browserName];
const fs = require('fs');
const assert = require('node:assert/strict');
const {spawn} = require('node:child_process');
const path = require('node:path');
const readline = require('node:readline');
const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

async function checkBuiltJar(browser) {
 const jar = fs.readdirSync('build/libs').find(name => name.endsWith('.jar') && !name.endsWith('-sources.jar'));
 assert(jar, 'Build the mod before running phone tests');
 const classpath = ['build/classes/java/test', path.join('build/libs',jar)].join(path.delimiter);
 const java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java') : 'java';
 const fixture = spawn(java, ['-cp',classpath,'dev.minemap.core.PhoneServerFixture']);
 let stderr = '';fixture.stderr.on('data',data => stderr+=data);
 const exited = new Promise(resolve => fixture.once('exit', resolve));
 const lines = readline.createInterface({input:fixture.stdout})[Symbol.asyncIterator]();
 const command = async value => {fixture.stdin.write(value+'\n');assert.equal((await lines.next()).value,'ok',stderr)};
 const page = await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors = [];page.on('pageerror', e => errors.push(e.message));
 try {
  const url = (await lines.next()).value;assert(url?.startsWith('http://127.0.0.1:'),stderr);
  const response = await page.goto(url);
  assert.equal(response.status(),200);
  assert(response.headers()['content-security-policy'].includes("connect-src 'self'"));
  await page.waitForFunction(() => document.querySelector('#coords').textContent==='X 15 · Y 64 · Z -24' && chunkImages.size===2);
  await command('update');
  await page.waitForFunction(()=>frame?.x===16.5&&chunkImages.get('1,-2')?.revision===3);
  assert.equal(await page.evaluate(()=>chunkImages.size),2,'unchanged explored chunk was lost');
  const heightResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/view');
  await page.locator('#heightRange').fill('20');await page.locator('#heightRange').dispatchEvent('change');await heightResponse;await command('height20');
  const autoResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/view');
  await page.locator('#autoHeight').click();await autoResponse;await command('auto');
  await page.locator('#addPoint').click();await page.locator('#pointName').fill('Дом');await page.locator('#pointForm button[type=submit]').click();
  await page.waitForFunction(()=>markers.length===1&&markers[0].name==='Дом');
  await command('inactive');
  await page.waitForFunction(() => document.querySelector('#notice strong').textContent==='Зайди в мир');
  await command('active');
  await page.waitForFunction(() => document.querySelector('#coords').textContent==='X 31 · Y 64 · Z 42' && chunkImages.size===1);
  await command('reset');
  await page.waitForFunction(() => document.querySelector('#status').textContent==='Ключ сброшен' && bitmap===null);
  assert.deepEqual(errors,[]);
 } finally {
  await page.close();fixture.stdin.end('quit\n');
  const killer=setTimeout(() => fixture.kill(),3000);await exited;clearTimeout(killer);
 }
}

(async () => {
 const browser = await browserType.launch({headless:true,...(browserName==='chromium'?{args:['--no-sandbox']}: {})});
 try {
  const page = await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  // Older mobile browsers support AbortController but not AbortSignal.timeout.
  await page.addInitScript(() => { AbortSignal.timeout = undefined; });
  let calls = 0, mode = 'active', worldId = 1, revision = 1, manualHeight=false, mapY=64, markerList=[];
  await page.route('http://minemap.test/**', async route => {
   const url = new URL(route.request().url());
   if (url.pathname==='/view') {
    assert.equal(route.request().method(),'POST');manualHeight=url.searchParams.get('y')!=='auto';mapY=manualHeight?Number(url.searchParams.get('y')):64;revision++;
    return route.fulfill({contentType:'application/json',body:'{}'});
   }
   if (url.pathname==='/markers') {
    if(url.searchParams.get('action')==='delete')markerList=[];
    else markerList=[{id:'p',name:url.searchParams.get('name'),color:url.searchParams.get('color'),x:Number(url.searchParams.get('x')),y:Number(url.searchParams.get('y')),z:Number(url.searchParams.get('z'))}];
    return route.fulfill({contentType:'application/json',body:'{}'});
   }
   if (url.pathname !== '/state') {
    return route.fulfill({contentType:'text/html',body:fs.readFileSync('src/main/resources/web/index.html','utf8')});
   }
   assert.equal(route.request().headers()['x-minemap-token'], 'testkey');
   assert(!url.href.includes('testkey'), 'key leaked into request URL');
   calls++;
   if (mode === 'offline') return route.abort();
   if (mode === 'unauthorized') return route.fulfill({status:401,body:'Pair again'});
   const next = {active:mode!=='inactive',worldId,dimension:'overworld',x:-12.5,z:24,yaw:90,originX:-104,originZ:-104,size:208,revision,playerY:64,mapY,minY:-64,maxY:319,slice:manualHeight,manualHeight,viewId:manualHeight?'y'+mapY:'surface',markers:markerList};
   if (Number(url.searchParams.get('revision')) !== revision) next.image = mode==='broken' ? 'invalid' : png;
   await route.fulfill({contentType:'application/json',body:JSON.stringify(next)});
  });
  const waitNotice = text => page.waitForFunction(t => document.querySelector('#notice strong').textContent===t, text);
  const pan = async () => {
   await page.mouse.move(150,400);await page.mouse.down();await page.mouse.move(180,450);await page.mouse.up();
   assert.equal(await page.locator('#follow').getAttribute('class'), '');
  };
  await page.goto('http://minemap.test/#testkey');
  await page.waitForFunction(() => document.querySelector('#coords').textContent==='X -13 · Y 64 · Z 24');
  assert(await page.evaluate(() => bitmap!==null));
  assert.equal(await page.locator('.axes').textContent(), '+X →+Z ↓');
  assert(!await page.locator('body').textContent().then(t=>t.includes('ТВОЙ МИР')));
  await page.locator('#heightRange').fill('20');await page.locator('#heightRange').dispatchEvent('change');
  await page.waitForFunction(()=>frame?.manualHeight&&frame.mapY===20);
  await page.locator('#autoHeight').click();await page.waitForFunction(()=>frame&&!frame.manualHeight);
  await page.locator('#addPoint').click();await page.locator('#pointName').fill('Пещера');
  await page.locator('#palette button').nth(3).click();await page.locator('#pointForm button[type=submit]').click();
  await page.waitForFunction(()=>markers.length===1&&markers[0].name==='Пещера'&&markers[0].color==='#64b5ff');
  await page.locator('#addPoint').click();await page.locator('#pointList button').click();
  await page.waitForFunction(()=>markers.length===0);await page.locator('#closePoints').click();
  const scale = await page.locator('#scale').textContent();
  await page.locator('#plus').click();
  assert.notEqual(await page.locator('#scale').textContent(), scale);
  await page.locator('#minus').click();
  assert.equal(await page.locator('#scale').textContent(), scale);
  await pan();await page.locator('#follow').click();
  assert.deepEqual(await page.evaluate(() => center), {x:-12.5,z:24});
  // Exercise two-finger gestures, including lifting a finger mid-gesture.
  if (browserName==='chromium') {
  const cdp = await page.context().newCDPSession(page);
  await cdp.send('Input.dispatchTouchEvent', {type:'touchStart',touchPoints:[{x:130,y:400,id:0},{x:230,y:400,id:1}]});
  const oldZoom = await page.evaluate(() => zoom);
  await cdp.send('Input.dispatchTouchEvent', {type:'touchMove',touchPoints:[{x:100,y:400,id:0},{x:260,y:400,id:1}]});
  assert(await page.evaluate(() => zoom) > oldZoom);
  await cdp.send('Input.dispatchTouchEvent', {type:'touchEnd',touchPoints:[{x:100,y:400,id:0}]});
  await cdp.send('Input.dispatchTouchEvent', {type:'touchEnd',touchPoints:[]});
  assert.equal(await page.evaluate(() => pointers.size), 0);
  }
  await page.locator('#follow').click();
  // A fast world switch can skip the inactive frame and retain the dimension name.
  await pan();worldId++;revision++;
  await page.waitForFunction(id => frame?.worldId===id && following && bitmap!==null, worldId);
  assert.deepEqual(await page.evaluate(() => center), {x:-12.5,z:24});
  mode='offline';await waitNotice('Связь потеряна');
  mode='active';await page.waitForFunction(() => document.querySelector('#notice').hidden);
  // Retry a corrupt image without advancing the acknowledged revision.
  mode='broken';revision++;
  await waitNotice('Связь потеряна');
  assert.equal(await page.evaluate(() => revision), revision-1);
  mode='active';await page.waitForFunction(r => revision===r, revision);
  // If the player briefly disappears, the image revision may remain unchanged.
  mode='inactive';await waitNotice('Зайди в мир');
  assert(await page.evaluate(() => bitmap===null && revision===-1));
  mode='active';await page.waitForFunction(() => frame?.active && bitmap!==null && document.querySelector('#notice').hidden);
  fs.mkdirSync('build',{recursive:true});
  await page.screenshot({path:'build/phone-preview-'+browserName+'.png'});
  mode='unauthorized';await waitNotice('Нужно подключиться заново');
  assert(await page.evaluate(() => frame===null && bitmap===null));
  assert.equal(await page.locator('#coords').textContent(), '—');
  const stoppedCalls=calls;await page.waitForTimeout(600);assert.equal(calls,stoppedCalls);
  const noKey = await browser.newPage();
  await noKey.route('http://minemap.test/**', route => route.fulfill({contentType:'text/html',body:fs.readFileSync('src/main/resources/web/index.html','utf8')}));
  await noKey.goto('http://minemap.test/');
  assert.equal(await noKey.locator('#status').textContent(), 'Нет ключа подключения');
  assert.deepEqual(errors, []);
  await checkBuiltJar(browser);
  console.log(browserName+': phone checks and real HTTP server from mod JAR passed. Requests: '+calls);
 } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exit(1)});
