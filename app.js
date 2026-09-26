const KEY="marketlist-items-v1";
let items=JSON.parse(localStorage.getItem(KEY)||"[]");
let deferredPrompt=null;
const $=id=>document.getElementById(id);
const money=v=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(v)||0);
const save=()=>localStorage.setItem(KEY,JSON.stringify(items));
const total=()=>items.reduce((s,i)=>s+(Number(i.price)||0)*(Number(i.quantity)||0),0);

function render(){
  const term=$("search").value.trim().toLowerCase();
  const visible=items.filter(i=>i.name.toLowerCase().includes(term));
  $("grandTotal").textContent=money(total());
  $("pendingCount").textContent=items.filter(i=>!i.bought).length;
  $("boughtCount").textContent=items.filter(i=>i.bought).length;
  $("emptyState").hidden=items.length>0;
  $("list").innerHTML=visible.map(i=>{
    const t=(Number(i.price)||0)*(Number(i.quantity)||0);
    return `<article class="item ${i.bought?"bought":""}">
      <button class="check" data-action="toggle" data-id="${i.id}" aria-label="${i.bought?"Marcar como pendente":"Marcar como comprado"}">${i.bought?"✓":"○"}</button>
      <div><div class="name">${escapeHtml(i.name)}</div><div class="meta">${formatQty(i.quantity)} × ${money(i.price)}</div></div>
      <div><div class="item-total">${money(t)}</div><button class="delete" data-action="delete" data-id="${i.id}" aria-label="Excluir">✕</button></div>
    </article>`;
  }).join("");
}
function escapeHtml(s){return s.replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]))}
function formatQty(q){return Number(q)%1===0?Number(q).toString():Number(q).toLocaleString("pt-BR",{maximumFractionDigits:2})}
function openDialog(){ $("itemForm").reset(); $("quantity").value=1; updatePreview(); $("itemDialog").showModal(); setTimeout(()=>$("name").focus(),50)}
function updatePreview(){ $("itemPreview").textContent=money((Number($("price").value)||0)*(Number($("quantity").value)||0)) }

$("addBtn").onclick=openDialog;
$("closeDialog").onclick=()=>$("itemDialog").close();
$("price").oninput=updatePreview; $("quantity").oninput=updatePreview;
$("search").oninput=render;
$("clearBought").onclick=()=>{items=items.filter(i=>!i.bought);save();render()};
$("list").onclick=e=>{const b=e.target.closest("[data-action]");if(!b)return;const id=b.dataset.id;
 if(b.dataset.action==="toggle")items=items.map(i=>i.id===id?{...i,bought:!i.bought}:i);
 if(b.dataset.action==="delete")items=items.filter(i=>i.id!==id);
 save();render();
};
$("itemForm").onsubmit=e=>{e.preventDefault();
 const name=$("name").value.trim(),price=Number($("price").value),quantity=Number($("quantity").value);
 if(!name||price<0||quantity<=0)return;
 items.unshift({id:crypto.randomUUID?crypto.randomUUID():Date.now().toString(),name,price,quantity,bought:false});
 save();render();$("itemDialog").close();
};

window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e;$("installBtn").hidden=false});
$("installBtn").onclick=async()=>{if(!deferredPrompt)return;deferredPrompt.prompt();await deferredPrompt.userChoice;deferredPrompt=null;$("installBtn").hidden=true};
window.addEventListener("appinstalled",()=>{$("installBtn").hidden=true});
if("serviceWorker" in navigator)window.addEventListener("load",()=>navigator.serviceWorker.register("sw.js"));
render();
