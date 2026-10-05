'use strict';
const $ = id => document.getElementById(id);
const CARRIERS = [
  {id:'dhl', name:'DHL/MNG', prefixes:['416','HTS']},
  {id:'hepsi', name:'HepsiJet', prefixes:['416','H0','HTS']},
  {id:'tex', name:'Tex', prefixes:['73']},
  {id:'aras', name:'Aras', prefixes:['72','P0','A0','FL0']},
  {id:'unknown', name:'Bilinmeyen', prefixes:[]}
];
const STORAGE = 'barkod-kargolar-v8';
let items = [], history = [], selected = '', activeTab = 'dhl';
function validCode(code) {return /^[A-Z0-9]{3,64}$/.test(code);}
try {
  const stored = JSON.parse(localStorage.getItem(STORAGE) || 'null');
  const old = stored ? stored.items : JSON.parse(localStorage.getItem('barkod-listesi') || '[]');
  if(Array.isArray(old)) items = old.filter(x=>typeof x.code==='string' && Number.isSafeInteger(x.quantity) && x.quantity>0).map(x=>({...x,cargo:CARRIERS.some(c=>c.id===x.cargo)?x.cargo:'unknown'}));
  if (stored && CARRIERS.slice(0,4).some(c=>c.id===stored.selected)) selected = stored.selected;
  if (stored && Array.isArray(stored.history)) history=stored.history.slice(-100).filter(x=>Array.isArray(x.items));
  activeTab=selected || (items.length?'unknown':'dhl');
} catch {}
function cargoName(id) {return CARRIERS.find(x=>x.id===id)?.name || 'Bilinmeyen';}
function save() {
  try {localStorage.setItem(STORAGE,JSON.stringify({items,history,selected}));}
  catch {status('Liste saklanamadı. Kapatmadan Excel/TXT olarak indirin.');}
}
function status(text) {$('status').textContent=text;}
function checkpoint() {history.push({items:items.map(x=>({...x})),activeTab});if(history.length>100)history.shift();}
let feedbackTimer;
function showSuccess() {
  clearTimeout(feedbackTimer);$('scan-area').classList.add('scan-success');$('status').classList.add('success');
  feedbackTimer=setTimeout(()=>{$('scan-area').classList.remove('scan-success');$('status').classList.remove('success');},500);
}
const rowCache = new Map();
function render() {
  const fragment = document.createDocumentFragment();
  for(const item of items.filter(x=>x.cargo===activeTab)) {
    const key=item.cargo+':'+item.code;
    let row=rowCache.get(key);
    if(!row) {
      row=document.createElement('tr');
      const cell=document.createElement('td');const label=document.createElement('span');label.textContent=item.code;
      const badge=document.createElement('span');badge.className='duplicate-badge';cell.append(label,badge);
      const actions=document.createElement('td');const remove=document.createElement('button');remove.textContent='Sil';remove.setAttribute('aria-label',item.code+' barkodunu sil');
      remove.onclick=()=>{checkpoint();items=items.filter(x=>x.cargo+':'+x.code!==key);render();save();status('Silindi. Geri al ile geri yükleyebilirsiniz.');};
      actions.append(remove);row.append(cell,actions);rowCache.set(key,row);
    }
    row.classList.toggle('duplicate',item.quantity>1);
    row.querySelector('.duplicate-badge').textContent=item.quantity>1?` (${item.quantity} adet)`:'';
    fragment.append(row);
  }
  $('rows').replaceChildren(fragment);
  $('empty').hidden=items.some(x=>x.cargo===activeTab);
  for(const cargo of CARRIERS) {
    const button=$('tab-'+cargo.id);button.textContent=`${cargo.name} (${items.filter(x=>x.cargo===cargo.id).length})`;
    button.setAttribute('aria-selected',String(activeTab===cargo.id));button.classList.toggle('active',activeTab===cargo.id);
  }
  $('count').textContent=`${cargoName(activeTab)} · ${items.filter(x=>x.cargo===activeTab).length} barkod · Genel toplam ${items.length}`;
  for(const id of ['download','txt','share','clear']) $(id).disabled=!items.length;
  $('undo').disabled=!history.length;
  $('camera').disabled=!selected;
}
function classify(code) {
  const cargo=CARRIERS.find(x=>x.id===selected);
  return cargo?.prefixes.some(prefix=>code.startsWith(prefix))?selected:'unknown';
}
function add(code) {
  if(!selected) {status('Önce kargo seçin.');return false;}
  code=code.trim().toUpperCase();
  if(!validCode(code)) {status('Barkod 3–64 harf veya rakam içermeli.');return false;}
  checkpoint();
  const cargo=classify(code),existing=items.find(x=>x.code===code&&x.cargo===cargo);
  if(existing) {existing.quantity++;items=items.filter(x=>x!==existing);items.unshift(existing);}
  else items.unshift({code,cargo,quantity:1});
  activeTab=cargo;render();save();$('list-area').scrollTop=0;
  status(`${cargoName(cargo)} · ${code}${existing?' tekrar okundu':''}`);showSuccess();return true;
}
$('cargo').value=selected;
$('cargo').onchange=()=>{selected=$('cargo').value;if(selected)activeTab=selected;render();save();status(selected?`${cargoName(selected)} seçildi. Liste korunuyor.`:'Önce kargo seçin.');};
for(const c of CARRIERS) $('tab-'+c.id).onclick=()=>{activeTab=c.id;render();$('list-area').scrollTop=0;};
$('undo').onclick=()=>{const previous=history.pop();if(!previous)return;items=previous.items;activeTab=previous.activeTab;render();save();status('Son işlem geri alındı.');};
$('clear').onclick=()=>{if(confirm('Tüm kargoların listesi temizlensin mi?')) {checkpoint();items=[];render();save();status('Listeler temizlendi. Geri al ile geri yükleyebilirsiniz.');}};
$('form').onsubmit=event=>{event.preventDefault();if(add($('barcode').value))$('barcode').value='';$('barcode').focus();};
function exportRows(cargo) {return [['Barkod','Tekrar sayısı'],...items.filter(x=>x.cargo===cargo).map(x=>[x.code,x.quantity])];}
function filename(extension) {return `kargo-barkodlari-${new Date().toISOString().slice(0,10)}.${extension}`;}
$('download').onclick=()=>{
  if(!window.XLSX) {status('Excel bileşeni yüklenemedi. TXT indir kullanılabilir.');return;}
  stopCamera();const book=XLSX.utils.book_new();
  for(const cargo of CARRIERS) {
    const sheet=XLSX.utils.aoa_to_sheet(exportRows(cargo.id));sheet['!cols']=[{wch:35},{wch:15}];
    XLSX.utils.book_append_sheet(book,sheet,cargo.name.replace('/','-'));
  }
  XLSX.writeFile(book,filename('xlsx'));status('Okutma tamamlandı. Tüm kargolar Excel dosyasına aktarıldı.');
};
function textExport() {return CARRIERS.filter(c=>items.some(x=>x.cargo===c.id)).map(c=>`[${c.name}]\n`+items.filter(x=>x.cargo===c.id).map(x=>x.code+(x.quantity>1?` (${x.quantity} adet)`:'')).join('\n')).join('\n\n');}
function downloadTxt() {
  const url=URL.createObjectURL(new Blob(['\uFEFF'+textExport()],{type:'text/plain;charset=utf-8'}));
  const link=document.createElement('a');link.href=url;link.download=filename('txt');document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
$('txt').onclick=()=>{stopCamera();downloadTxt();status('Tüm kargolar TXT dosyasına kaydedildi.');};
$('share').onclick=async()=>{
  stopCamera();const file=new File(['\uFEFF'+textExport()],filename('txt'),{type:'text/plain'});
  if(!navigator.share) {downloadTxt();status('Bu tarayıcı paylaşımı desteklemiyor. TXT indirildi; dosyadan paylaşabilirsiniz.');return;}
  try {
    if(navigator.canShare?.({files:[file]})) await navigator.share({files:[file],title:'Kargo barkodları'});
    else await navigator.share({text:textExport(),title:'Kargo barkodları'});
    status('Paylaşım tamamlandı.');
  } catch(error) {if(error.name!=='AbortError') {downloadTxt();status('Paylaşım açılamadı. TXT dosyası indirildi.');}}
};
