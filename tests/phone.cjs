const {chromium}=require('playwright');const fs=require('fs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:390,height:844},isMobile:true,hasTouch:true});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 let calls=0;let mode='active';
 await page.route('http://minemap.test/**',async route=>{
  if(new URL(route.request().url()).pathname==='/state'){
   if(route.request().headers()['x-minemap-token']!=='testkey')throw Error('missing key');calls++;
   await route.fulfill({status:mode==='unauthorized'?401:200,contentType:'application/json',body:JSON.stringify({active:mode==='active',dimension:'overworld',x:-12.5,z:24,yaw:90,originX:-104,originZ:-104,size:208,revision:1,image:'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII='})});
  }else await route.fulfill({contentType:'text/html',body:fs.readFileSync('src/main/resources/web/index.html','utf8')});
 });
 await page.goto('http://minemap.test/#testkey');
 await page.waitForFunction(()=>document.querySelector('#coords').textContent==='-13 / 24');
 await page.locator('#plus').click();await page.locator('#minus').click();
 await page.mouse.move(150,400);await page.mouse.down();await page.mouse.move(180,450);await page.mouse.up();
 if(await page.locator('#follow').getAttribute('class')==='on')throw Error('pan did not disable follow');
 await page.locator('#follow').click();
 fs.mkdirSync('build',{recursive:true});await page.screenshot({path:'build/phone-preview.png'});
 mode='inactive';await page.waitForFunction(()=>document.querySelector('#notice strong').textContent==='Зайди в мир');
 mode='unauthorized';await page.waitForFunction(()=>document.querySelector('#notice strong').textContent==='Нужно подключиться заново');
 if(errors.length)throw Error(errors.join('\n'));
 console.log('Phone checks passed: render, zoom, pan, follow, world exit, reset. Requests: '+calls);
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
