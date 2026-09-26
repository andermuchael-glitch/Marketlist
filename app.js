const KEY="marketlist-items-v1";
let items=JSON.parse(localStorage.getItem(KEY)||"[]");
let deferredPrompt=null;
let currentFilter="all";
let editingId=null;
let lastDeleted=null;
const $=id=>document.getElementById(id);
const money=v=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(v)||0);
const save=()=>localStorage.setItem(KEY,JSON.stringify(items));
const itemTotal=i=>(Number(i.price)||0)*(Number(i.quantity)||0);
const total=()=>items.reduce((s,i)=>s+itemTotal(i),0);
const escapeHtml=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]));
const formatQty=q=>Number(q)%1===0?Number(q).toString():Number(q).toLocaleString("pt-BR",{maximumFractionDigits:2});

function toast(message,undo=false){
  let el=$("toast"); if(!el){el=document.createElement("div");el.id="toast";el.className="toast";document.body.appendChild(el)}
  el.innerHTML=undo?escapeHtml(message)+' <button id="undoDelete" style="margin-left:10px;border:0;border-radius:8px;padding:6px 9px;cursor:pointer">Desfazer</button>':escapeHtml(message);
  el.classList.add("show"); clearTimeout(window.toastTimer); window.toastTimer=setTimeout(()=>el.classList.remove("show"),3500);
  if(undo) $("undoDelete").onclick=()=>{if(lastDeleted){items.unshift(lastDeleted);lastDeleted=null;save();render();el.classList.remove("show")}};
}

function render(){
  const term=$("search").value.trim().toLowerCase();
  let visible=items.filter(i=>i.name.toLowerCase().includes(term));
  if(currentFilter==="pending") visible=visible.filter(i=>!i.bought);
  if(currentFilter==="bought") visible=visible.filter(i=>i.bought);

  $("grandTotal").textContent=money(total());
  $("pendingCount").textContent=items.filter(i=>!i.bought).length;
  $("boughtCount").textContent=items.filter(i=>i.bought).length;
  $("emptyState").hidden=visible.length>0;
  if(items.length>0 && visible.length===0){$("emptyState").hidden=false;$("emptyState").innerHTML='<div class="empty-icon">🔎</div><h2>Nenhum item encontrado</h2><p>Tente outra busca ou mude o filtro.</p>'}
  else if(items.length===0){$("emptyState").innerHTML='<div class="empty-icon">🛒</div><h2>Sua lista está vazia</h2><p>Adicione produtos, informe preço e quantidade e acompanhe o total.</p>'}

  $("list").innerHTML=visible.map(i=>{
    const t=itemTotal(i);
    return `<article class="item ${i.bought?"bought":""}">
      <button class="check" data-action="toggle" data-id="${i.id}" aria-label="${i.bought?"Marcar como pendente":"Marcar como comprado"}">${i.bought?"✓":"○"}</button>
      <div><div class="name">${escapeHtml(i.name)}</div><div class="meta">${formatQty(i.quantity)} × ${money(i.price)}</div></div>
      <div><div class="item-total">${money(t)}</div><div class="item-actions"><button class="edit" data-action="edit" data-id="${i.id}" aria-label="Editar">✎</button><button class="delete" data-action="delete" data-id="${i.id}" aria-label="Excluir">✕</button></div></div>
    </article>`;
  }).join("");

  document.querySelectorAll(".filter").forEach(b=>b.classList.toggle("active",b.dataset.filter===currentFilter));
}

function updatePreview(){ $("itemPreview").textContent=money((Number($("price").value)||0)*(Number($("quantity").value)||0)); }

function openDialog(item=null){
  editingId=item?.id||null;
  $("itemForm").reset(); $("quantity").value=item?.quantity??1;
  $("name").value=item?.name??""; $("price").value=item?.price??"";
  $("dialogTitle").textContent=item?"Editar item":"Adicionar item";
  $("submitItem").textContent=item?"Salvar alterações":"Adicionar à lista";
  updatePreview(); $("itemDialog").showModal(); setTimeout(()=>$("name").focus(),50);
}
function closeDialog(){editingId=null;$("itemDialog").close()}

$("addBtn").onclick=()=>openDialog();
$("closeDialog").onclick=closeDialog;
$("cancelDialog").onclick=closeDialog;
$("price").oninput=updatePreview; $("quantity").oninput=updatePreview;
$("search").oninput=render;

document.querySelectorAll(".filter").forEach(b=>b.onclick=()=>{currentFilter=b.dataset.filter;render()});

$("clearBought").onclick=()=>{
  const count=items.filter(i=>i.bought).length;
  if(!count){toast("Não há itens comprados para limpar.");return}
  items=items.filter(i=>!i.bought);save();render();toast(`${count} item(ns) comprado(s) removido(s).`);
};

$("list").onclick=e=>{
  const b=e.target.closest("[data-action]"); if(!b)return;
  const id=b.dataset.id; const item=items.find(i=>i.id===id); if(!item)return;
  if(b.dataset.action==="toggle") item.bought=!item.bought;
  if(b.dataset.action==="edit") {openDialog(item);return}
  if(b.dataset.action==="delete"){lastDeleted={...item};items=items.filter(i=>i.id!==id);toast("Item excluído.",true)}
  save();render();
};

$("itemForm").onsubmit=e=>{
  e.preventDefault();
  const name=$("name").value.trim(),price=Number($("price").value),quantity=Number($("quantity").value);
  if(!name||price<0||quantity<=0)return;
  if(editingId){
    items=items.map(i=>i.id===editingId?{...i,name,price,quantity}:i);
    toast("Item atualizado.");
  }else{
    items.unshift({id:crypto.randomUUID?crypto.randomUUID():Date.now().toString(),name,price,quantity,bought:false});
    toast("Item adicionado.");
  }
  save();render();closeDialog();
};

window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e;$("installBtn").hidden=false});
$("installBtn").onclick=async()=>{if(!deferredPrompt)return;deferredPrompt.prompt();await deferredPrompt.userChoice;deferredPrompt=null;$("installBtn").hidden=true};
window.addEventListener("appinstalled",()=>{$("installBtn").hidden=true});
if("serviceWorker" in navigator)window.addEventListener("load",()=>navigator.serviceWorker.register("sw.js"));
render();
