const {chromium}=require('C:/Users/OmerW11/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('fs');
const assert=require('assert');
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Users/OmerW11/AppData/Local/BraveSoftware/Brave-Browser/Application/brave.exe'});
  try {
    const page=await browser.newPage({viewport:{width:390,height:664}});
    const html=fs.readFileSync('BarkodListe/index.html','utf8').replace(/<script[^>]*>[\s\S]*?<\/script>/g,'');
    await page.route('https://barkod.test/**',r=>r.fulfill({contentType:'text/html',body:html}));
    await page.goto('https://barkod.test');
    await page.evaluate(()=>localStorage.setItem('barkod-listesi',JSON.stringify([{code:'0012345678',quantity:1}])));
    await page.addScriptTag({content:fs.readFileSync('BarkodListe/app.js','utf8')});
    const result=await page.evaluate(()=>{
      function check(value,label){if(!value)throw Error(label);}
      check(items[0].cargo==='unknown','old records preserved');
      check($('camera').disabled,'selection required');check(!add('4161125527'),'manual selection required');
      function select(id){$('cargo').value=id;$('cargo').onchange();}
      select('dhl');add('4161125527');add('HTS12345678901');check(items[0].cargo==='dhl','DHL HTS');
      add('4171125527');check(items[0].cargo==='unknown','417 future disabled');
      select('hepsi');add('4161125527');add('H012345678901');add('HTS998877');check(items[0].cargo==='hepsi','HepsiJet');
      select('tex');add('731234567890123');check(items[0].cargo==='tex','Tex variable length');
      select('aras');for(const prefix of ['72','P0','A0','FL0']){add(prefix+'1234567890');check(items[0].cargo==='aras',prefix);}
      add('ZZ1234567890');check(items[0].cargo==='unknown','unknown routing');
      check(!add('https://example.com'),'QR URL rejected');
      select('tex');add('731234567890123');check(items[0].quantity===2,'repeat');
      check($('rows').querySelector('tr').classList.contains('duplicate'),'red duplicate');
      check($('rows').textContent.includes('(2 pcs)'),'repeat label');
      check($('rows').querySelector('tr').children.length===2,'no quantity column');
      $('undo').onclick();check(items.find(x=>x.cargo==='tex').quantity===1,'undo repeat');
      $('rows').querySelector('button').click();check(!items.some(x=>x.cargo==='tex'),'delete');
      $('undo').onclick();check(items.some(x=>x.cargo==='tex'),'undo delete');
      currentCode='';acceptScan('731234567890123');const quantity=items[0].quantity;acceptScan('731234567890123');check(items[0].quantity===quantity,'frame duplicate ignored');
      acceptScan('7399999999');check(items[0].code==='7399999999','no wait on next code');
      $('undo').onclick();check(!items.some(x=>x.code==='7399999999'),'undo scan');
      const all=items.length;select('dhl');check(items.length===all,'cargo switch preserves all');
      const txt=textExport();for(const name of ['DHL/MNG','HepsiJet','Tex','Aras','Unknown'])check(txt.includes('['+name+']'),'TXT '+name);
      check(exportRows('unknown')[1][0]==='ZZ1234567890','unknown export order');
      return {records:items.length,history:history.length,txt};
    });
    assert(result.records>10);
    await page.addScriptTag({url:'https://cdn.sheetjs.com/xlsx-0.20.3/package/dist/xlsx.full.min.js'});
    const excel=await page.evaluate(()=>{
      XLSX.writeFile=(book)=>window.testBook=book;$('download').onclick();
      const encoded=XLSX.write(testBook,{type:'base64',bookType:'xlsx'});
      const roundtrip=XLSX.read(encoded,{type:'base64'});
      if(roundtrip.SheetNames.length!==5)throw Error('Excel tabs');
      const old=Object.values(roundtrip.Sheets.Unknown).find(c=>c && c.v==='0012345678');
      if(!old || old.t!=='s')throw Error('Excel leading zeros');
      return encoded;
    });
    fs.writeFileSync('BarkodListe/verified-export.xlsx',Buffer.from(excel,'base64'));
    const share=await page.evaluate(async()=>{
      Object.defineProperty(navigator,'share',{configurable:true,value:async data=>window.shared=data});
      Object.defineProperty(navigator,'canShare',{configurable:true,value:()=>true});
      await $('share').onclick();return await shared.files[0].text();
    });
    assert(share.includes('[HepsiJet]')&&share.includes('[Unknown]'));
    await page.reload();await page.addScriptTag({content:fs.readFileSync('BarkodListe/app.js','utf8')});
    assert.equal(await page.evaluate(()=>items.length),result.records);
    await page.evaluate(()=>{$('cargo').value='tex';$('cargo').onchange();$('scan-area').classList.add('scanning');$('camera').hidden=true;$('stop').hidden=false;$('resolution').textContent='3840 × 2160';});
    for(const size of [{width:390,height:664},{width:360,height:640},{width:664,height:390}]){
      await page.setViewportSize(size);
      const boxes=await page.evaluate(()=>{const v=$('video').getBoundingClientRect(),l=$('list-area').getBoundingClientRect(),f=document.querySelector('footer').getBoundingClientRect();return {videoBottom:v.bottom,listTop:l.top,listHeight:l.height,footerBottom:f.bottom,viewport:innerHeight};});
      assert(boxes.videoBottom<=boxes.listTop&&boxes.listHeight>=40&&boxes.footerBottom<=boxes.viewport+1,JSON.stringify(boxes));
    }
    await page.setViewportSize({width:390,height:664});await page.screenshot({path:'BarkodListe/kargo-v8.png'});
    console.log('PASS: all carrier prefixes, selection, migration, duplicate display, undo, instant scanning, persistence, five-sheet XLSX with leading zeros, TXT file share, portrait and landscape layout.');
  } finally {await browser.close();}
})();
