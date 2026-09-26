const KEY="marketlist-items-v1", BUDGET_KEY="marketlist-budget-v1", HISTORY_KEY="marketlist-history-v1";
function readJSON(key,fallback){
  try{const raw=localStorage.getItem(key);return raw?JSON.parse(raw):fallback;}catch(e){return fallback;}
}
function readBudget(){
  try{
    const raw=String(localStorage.getItem(BUDGET_KEY)||"").trim().replace(/R\$\s?/gi,"").replace(/\./g,"").replace(",",".");
    const value=Number(raw);
    return Number.isFinite(value)?Math.max(0,value):0;
  }catch(e){return 0;}
}
let items=readJSON(KEY,[]);
let history=readJSON(HISTORY_KEY,[]);
let budget=readBudget();
let deferredPrompt=null,currentFilter="all",currentCategory="all",editingId=null,lastDeleted=null;
const $=id=>document.getElementById(id);
const money=v=>new Intl.NumberFormat("pt-BR",{style:"currency",currency:"BRL"}).format(Number(v)||0);
function storageError(){toast("Não foi possível salvar. Verifique o espaço de armazenamento do navegador.");}
function save(){try{localStorage.setItem(KEY,JSON.stringify(items));return true}catch(e){storageError();return false}}
function saveBudget(){try{localStorage.setItem(BUDGET_KEY,String(budget));return true}catch(e){storageError();return false}}
function saveHistory(){try{localStorage.setItem(HISTORY_KEY,JSON.stringify(history));return true}catch(e){storageError();return false}}
const itemTotal=i=>(Number(i.price)||0)*(Number(i.quantity)||0);
const total=()=>items.reduce((s,i)=>s+itemTotal(i),0);
const escapeHtml=s=>String(s).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]));
const parseDecimal=value=>{let s=String(value??"").trim().replace(/R\$\s?/gi,"").replace(/\s/g,"");if(!s)return 0;if(s.includes(",")&&s.includes("."))s=s.replace(/\./g,"").replace(",",".");else s=s.replace(",",".");const n=Number(s);return Number.isFinite(n)?n:0;};
const formatPriceInput=v=>Number(v)>0?Number(v).toLocaleString("pt-BR",{minimumFractionDigits:2,maximumFractionDigits:2}):"";
const formatQtyInput=v=>Number(v)%1===0?Number(v).toString():Number(v).toLocaleString("pt-BR",{maximumFractionDigits:2});
const formatQty=q=>Number(q)%1===0?Number(q).toString():Number(q).toLocaleString("pt-BR",{maximumFractionDigits:2});

function toast(message,undo=false){
 let el=$("toast");if(!el){el=document.createElement("div");el.id="toast";el.className="toast";document.body.appendChild(el)}
 el.innerHTML=undo?escapeHtml(message)+' <button id="undoDelete" style="margin-left:10px;border:0;border-radius:8px;padding:6px 9px;cursor:pointer">Desfazer</button>':escapeHtml(message);
 el.classList.add("show");clearTimeout(window.toastTimer);window.toastTimer=setTimeout(()=>el.classList.remove("show"),3500);
 if(undo)$("undoDelete").onclick=()=>{if(lastDeleted){items.unshift(lastDeleted);lastDeleted=null;save();render();el.classList.remove("show")}};
}
function updateBudgetUI(){
 const budgetValue=$("budgetValue"),status=$("budgetStatus"),spentEl=$("budgetSpent"),remainingEl=$("budgetRemaining"),progress=$("budgetProgress");
 const spent=total(),diff=budget-spent;
 if(budgetValue)budgetValue.textContent=budget?money(budget):"Não definido";
 if(spentEl)spentEl.textContent=money(spent);
 if(remainingEl)remainingEl.textContent=budget?money(Math.abs(diff)):"—";
 if(progress)progress.style.width=budget?Math.min(100,Math.max(0,(spent/budget)*100))+"%":"0%";
 if(status){
  if(!budget){status.textContent="Defina um orçamento para acompanhar quanto ainda pode gastar.";status.className="budget-status"}
  else if(diff>=0){status.textContent="Você ainda pode gastar "+money(diff)+".";status.className="budget-status"}
  else{status.textContent="Orçamento ultrapassado em "+money(Math.abs(diff))+".";status.className="budget-status over"}
 }
}
function renderCategories(){
 const cats=[...new Set(items.map(i=>i.category||"Outros"))].sort();
 $("categoryFilters").innerHTML='<button class="filter '+(currentCategory==="all"?"active":"")+'" data-cat="all">Todas</button>'+
 cats.map(c=>`<button class="filter ${currentCategory===c?"active":""}" data-cat="${escapeHtml(c)}">${escapeHtml(c)}</button>`).join("");
 document.querySelectorAll("[data-cat]").forEach(b=>b.onclick=()=>{currentCategory=b.dataset.cat;render()});
}
function updateItemField(id,field,value){
 const item=items.find(i=>i.id===id); if(!item)return;
 if(field==="price") item.price=Math.max(0,parseDecimal(value));
 if(field==="quantity") item.quantity=Math.max(0.01,parseDecimal(value)||0.01);
 save();
 const totalEl=document.querySelector('[data-total-id="'+id+'"]');
 if(totalEl) totalEl.textContent=money(itemTotal(item));
 $("grandTotal").textContent=money(total());
 updateBudgetUI();
}
function changeQuantity(id,delta){
 const item=items.find(i=>i.id===id); if(!item)return;
 item.quantity=Math.max(0.01,(Number(item.quantity)||1)+delta);
 save(); render();
}
function render(){
 const term=$("search").value.trim().toLowerCase();
 let visible=items.filter(i=>i.name.toLowerCase().includes(term));
 if(currentFilter==="pending")visible=visible.filter(i=>!i.bought);
 if(currentFilter==="bought")visible=visible.filter(i=>i.bought);
 if(currentCategory!=="all")visible=visible.filter(i=>(i.category||"Outros")===currentCategory);
 $("grandTotal").textContent=money(total());
 $("pendingCount").textContent=items.filter(i=>!i.bought).length;
 $("boughtCount").textContent=items.filter(i=>i.bought).length;
 $("emptyState").hidden=visible.length>0;
 $("finishShopping").hidden=items.filter(i=>i.bought).length===0;
 if(items.length>0&&visible.length===0){$("emptyState").hidden=false;$("emptyState").innerHTML='<div class="empty-icon">🔎</div><h2>Nenhum item encontrado</h2><p>Tente outra busca ou mude o filtro.</p>'}
 else if(items.length===0){$("emptyState").innerHTML='<div class="empty-icon">🛒</div><h2>Sua lista está vazia</h2><p>Adicione produtos, informe preço e quantidade e acompanhe o total.</p>'}
 $("list").innerHTML=visible.map(i=>`<article class="item ${i.bought?"bought":""}">
 <button class="check" data-action="toggle" data-id="${i.id}" aria-label="${i.bought?"Marcar como pendente":"Marcar como comprado"}">${i.bought?"✓":"○"}</button>
 <div class="item-main">
   <div class="item-head">
     <div><div class="name">${escapeHtml(i.name)}</div><div class="meta">${escapeHtml(i.category||"Outros")}</div></div>
     <div class="item-actions-inline"><button class="edit" type="button" data-action="edit" data-id="${i.id}" aria-label="Editar ${escapeHtml(i.name)}" title="Editar">✎</button><div class="item-total" data-total-id="${i.id}">${money(itemTotal(i))}</div></div>
   </div>
   <div class="market-controls">
     <div class="qty-control" aria-label="Quantidade">
       <button type="button" data-action="qty-minus" data-id="${i.id}">−</button>
       <input type="text" inputmode="decimal" value="${formatQtyInput(i.quantity)}" data-action="qty-input" data-id="${i.id}" aria-label="Quantidade">
       <button type="button" data-action="qty-plus" data-id="${i.id}">+</button>
     </div>
     <label class="price-control"><span>R$</span><input type="text" inputmode="decimal" value="${formatPriceInput(i.price)}" placeholder="0,00" data-action="price-input" data-id="${i.id}" aria-label="Preço unitário"></label>
     <button class="delete" data-action="delete" data-id="${i.id}" aria-label="Excluir">✕</button>
   </div>
   <div class="market-hint">${i.bought?"Comprado":"No mercado: ajuste preço e quantidade aqui"}</div>
 </div>
 </article>`).join("");
 document.querySelectorAll(".filter").forEach(b=>{if(b.dataset.filter)b.classList.toggle("active",b.dataset.filter===currentFilter)});
 renderCategories();updateBudgetUI();
}
function updatePreview(){const preview=$("itemPreview");if(preview)preview.textContent=money((parseDecimal($("price").value)||0)*(parseDecimal($("quantity").value)||0))}
function openDialog(item=null){
 editingId=item?.id||null;$("itemForm").reset();$("quantity").value=item?.quantity??1;$("name").value=item?.name??"";$("price").value=item?.price??"";
 $("category").value=item?.category||"Outros";$("dialogTitle").textContent=item?"Editar item":"Adicionar item";$("submitItem").textContent=item?"Salvar alterações":"Adicionar à lista";updatePreview();$("itemDialog").showModal();setTimeout(()=>$("name").focus(),50);
}
function closeDialog(){editingId=null;$("itemDialog").close()}
function renderHistory(){
 const box=$("historyList");
 if(!history.length){box.innerHTML='<div class="empty"><div class="empty-icon">🧾</div><h2>Nenhuma compra salva</h2><p>Finalize uma compra para ela aparecer aqui.</p></div>';return}
 box.innerHTML=history.map(h=>`<article class="history-entry"><strong>${escapeHtml(h.date)} — ${money(h.total)}</strong><small>${h.count} item(ns)</small><div class="history-items">${h.items.map(i=>escapeHtml(i.name)+ " ("+money(itemTotal(i))+")").join(" · ")}</div></article>`).join("");
}

function createBackup(){return {app:"Marketlist",version:2,exportedAt:new Date().toISOString(),items:items.map(i=>({...i})),history:history.map(h=>({...h,items:Array.isArray(h.items)?h.items.map(i=>({...i})):[]})),budget:Number(budget)||0};}
function downloadBackup(){const data=JSON.stringify(createBackup(),null,2);const blob=new Blob([data],{type:"application/json"});const url=URL.createObjectURL(blob);const a=document.createElement("a");const stamp=new Date().toISOString().slice(0,10);a.href=url;a.download="marketlist-backup-"+stamp+".json";document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url);toast("Backup baixado com sucesso.");}
function restoreBackup(file){const reader=new FileReader();reader.onload=()=>{try{const data=JSON.parse(reader.result);if(data?.app!=="Marketlist"||!Array.isArray(data.items)||!Array.isArray(data.history))throw new Error("invalid");if(!confirm("Restaurar este backup? A lista e o histórico atuais serão substituídos pelos dados do arquivo."))return;items=data.items.filter(i=>i&&i.name).map(i=>({id:i.id||crypto.randomUUID?.()||String(Date.now()+Math.random()),name:String(i.name),price:Math.max(0,parseDecimal(i.price)),quantity:Math.max(0.01,parseDecimal(i.quantity)||0.01),category:String(i.category||"Outros"),bought:Boolean(i.bought)}));history=data.history.slice(0,30);budget=Math.max(0,parseDecimal(data.budget));save();saveHistory();saveBudget();render();toast("Backup restaurado. Sua lista voltou com sucesso.");}catch(e){toast("Não foi possível restaurar: arquivo de backup inválido.");}};reader.readAsText(file);}
$("backupBtn").onclick=()=>$("backupDialog").showModal();$("closeBackup").onclick=()=>$("backupDialog").close();$("downloadBackup").onclick=downloadBackup;$("restoreBackup").onclick=()=>$("backupFile").click();$("backupFile").onchange=e=>{const file=e.target.files?.[0];if(file)restoreBackup(file);e.target.value="";};
$("closeDialog").onclick=closeDialog;$("cancelDialog").onclick=closeDialog;
$("price").oninput=updatePreview;$("quantity").oninput=updatePreview;$("search").oninput=render;
const budgetBtn=$("budgetBtn"),budgetDialog=$("budgetDialog"),closeBudget=$("closeBudget"),budgetForm=$("budgetForm"),budgetEdit=$("budgetEdit");
if(budgetBtn&&budgetDialog)budgetBtn.addEventListener("click",()=>{
  if(budgetEdit)budgetEdit.value=budget?Number(budget).toLocaleString("pt-BR",{minimumFractionDigits:2,maximumFractionDigits:2}):"";
  if(typeof budgetDialog.showModal==="function")budgetDialog.showModal();
  else budgetDialog.setAttribute("open","");
  setTimeout(()=>budgetEdit?.focus(),50);
});
if(closeBudget&&budgetDialog)closeBudget.addEventListener("click",()=>budgetDialog.close());
if(budgetForm&&budgetDialog)budgetForm.addEventListener("submit",e=>{
  e.preventDefault();
  budget=Math.max(0,parseDecimal(budgetEdit?.value||""));
  if(!saveBudget())return;
  updateBudgetUI();
  budgetDialog.close();
  toast(budget?"Orçamento de "+money(budget)+" salvo.":"Orçamento removido.");
});
const budgetEditButton=$("budgetEditButton");
if(budgetEditButton)budgetEditButton.onclick=()=>budgetBtn?.click();
document.querySelectorAll("[data-nav]").forEach(btn=>btn.onclick=()=>{
 const target=btn.dataset.nav;
 if(target==="list")window.scrollTo({top:0,behavior:"smooth"});
 if(target==="history")$("historyBtn")?.click();
 if(target==="backup")$("backupBtn")?.click();
 if(target==="budget")budgetBtn?.click();
 document.querySelectorAll("[data-nav]").forEach(n=>n.classList.toggle("active",n===btn));
});

$("fabAdd").onclick=()=>openDialog();
document.querySelectorAll("[data-filter]").forEach(b=>b.onclick=()=>{currentFilter=b.dataset.filter;render()});
$("clearBought").onclick=()=>{const count=items.filter(i=>i.bought).length;if(!count){toast("Não há itens comprados para limpar.");return}items=items.filter(i=>!i.bought);save();render();toast(`${count} item(ns) comprado(s) removido(s).`)};
$("list").onclick=e=>{const b=e.target.closest("[data-action]");if(!b)return;const id=b.dataset.id,item=items.find(i=>i.id===id);if(!item)return;
 if(b.dataset.action==="edit"){openDialog(item);return}
 if(b.dataset.action==="toggle"){item.bought=!item.bought;save();render();return}
 if(b.dataset.action==="qty-plus"){changeQuantity(id,1);return}
 if(b.dataset.action==="qty-minus"){changeQuantity(id,-1);return}
 if(b.dataset.action==="delete"){lastDeleted={...item};items=items.filter(i=>i.id!==id);save();render();toast("Item excluído.",true)}
};
$("list").oninput=e=>{const b=e.target.closest("[data-action]");if(!b)return;
 if(b.dataset.action==="price-input")updateItemField(b.dataset.id,"price",b.value);
 if(b.dataset.action==="qty-input")updateItemField(b.dataset.id,"quantity",b.value);
};
$("list").onfocusout=e=>{const b=e.target.closest("[data-action]");if(!b)return;
 if(b.dataset.action==="price-input")updateItemField(b.dataset.id,"price",b.value);
 if(b.dataset.action==="qty-input")updateItemField(b.dataset.id,"quantity",b.value);
};
$("list").onkeydown=e=>{const b=e.target.closest("[data-action]");if(!b)return;
 if((b.dataset.action==="price-input"||b.dataset.action==="qty-input")&&e.key==="Enter"){e.preventDefault();b.blur();}
};
let authClient=null, authConfigured=false, authMode="login";
function setAuthMessage(message,isError=false){const el=$("authMessage");if(el){el.textContent=message||"";el.classList.toggle("error",isError)}}
function showApp(){const auth=$("authScreen"),app=document.querySelector(".app-shell");if(auth)auth.hidden=true;if(app)app.hidden=false}
function showAuth(){const auth=$("authScreen"),app=document.querySelector(".app-shell");if(auth)auth.hidden=false;if(app)app.hidden=true}
async function setupAuth(){
 const cfg=window.MARKETLIST_SUPABASE||{};
 if(!cfg.url||!cfg.key||!window.supabase){showAuth();setAuthMessage("O login em nuvem ainda precisa da configuração do Supabase.");return false}
 try{
  authClient=window.supabase.createClient(cfg.url,cfg.key,{auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}});
  authConfigured=true;
  const {data}=await authClient.auth.getSession();
  if(data?.session)showApp();else showAuth();
  authClient.auth.onAuthStateChange((_event,session)=>{if(session)showApp();else showAuth()});
  return true;
 }catch(e){showAuth();setAuthMessage("Não foi possível iniciar o login. Verifique a configuração do Supabase.",true);return false}
}
async function handleLogin(e){
 e.preventDefault();if(!authClient){setAuthMessage("Supabase ainda não está configurado.",true);return}
 const email=$("loginEmail").value.trim(),password=$("loginPassword").value;
 if(!email||!password)return;
 const btn=$("loginSubmit");btn.disabled=true;btn.textContent=authMode==="signup"?"Criando...":"Entrando...";
 setAuthMessage("");
 try{
  if(authMode==="signup"){
   const {data,error}=await authClient.auth.signUp({email,password});
   if(error)throw error;
   if(data.session){setAuthMessage("Conta criada com sucesso.");showApp()}else setAuthMessage("Conta criada. Confira seu e-mail para confirmar o acesso.");
  }else{
   const {error}=await authClient.auth.signInWithPassword({email,password});
   if(error)throw error;
  }
 }catch(error){setAuthMessage(error?.message||"Não foi possível entrar.",true)}
 finally{btn.disabled=false;btn.textContent=authMode==="signup"?"Criar conta":"Entrar"}
}
async function forgotPassword(){
 if(!authClient){setAuthMessage("Supabase ainda não está configurado.",true);return}
 const email=$("loginEmail").value.trim();
 if(!email){setAuthMessage("Digite seu e-mail para receber o link de recuperação.",true);$("loginEmail").focus();return}
 try{
  const {error}=await authClient.auth.resetPasswordForEmail(email,{redirectTo:window.location.origin+window.location.pathname});
  if(error)throw error;setAuthMessage("Enviamos as instruções de recuperação para seu e-mail.");
 }catch(error){setAuthMessage(error?.message||"Não foi possível enviar a recuperação.",true)}
}
function setupAuthUI(){
 const form=$("loginForm"),signup=$("signupBtn"),forgot=$("forgotPassword"),toggle=$("togglePassword"),offline=$("offlineBtn");
 form?.addEventListener("submit",handleLogin);
 signup?.addEventListener("click",()=>{authMode=authMode==="login"?"signup":"login";$("authHeading")?.replaceChildren();$("loginSubmit").textContent=authMode==="signup"?"Criar conta":"Entrar";signup.textContent=authMode==="signup"?"Já tenho uma conta":"Criar minha conta";$("loginPassword").setAttribute("autocomplete",authMode==="signup"?"new-password":"current-password");setAuthMessage(authMode==="signup"?"Crie sua conta com e-mail e senha.":"")});
 forgot?.addEventListener("click",forgotPassword);
 toggle?.addEventListener("click",()=>{const input=$("loginPassword");input.type=input.type==="password"?"text":"password";toggle.textContent=input.type==="password"?"◉":"◌"});
 offline?.addEventListener("click",()=>{sessionStorage.setItem("marketlist-offline","1");showApp();setAuthMessage("")});
}
function init(){
setupAuthUI();
setupAuth();
$("itemForm").onsubmit=e=>{
 e.preventDefault();
 const name=$("name").value.trim();
 const priceText=$("price").value.trim();
 const quantityText=$("quantity").value.trim();
 const price=priceText?parseDecimal(priceText):0;
 const quantity=quantityText?parseDecimal(quantityText):1;
 const category=$("category").value||"Outros";
 if(!name||price<0||quantity<=0){toast("Informe pelo menos o nome e uma quantidade válida.");return;}
 const id=editingId||(crypto.randomUUID?crypto.randomUUID():Date.now().toString());
 if(editingId)items=items.map(i=>i.id===editingId?{...i,name,price,quantity,category}:i);
 else items.unshift({id,name,price,quantity,category,bought:false});
 save();render();closeDialog();toast(editingId?"Item atualizado e salvo.":"Item adicionado e salvo na lista.");
};
$("historyBtn").onclick=()=>{$("historyDialog").showModal();renderHistory()};$("closeHistory").onclick=()=>$("historyDialog").close();
$("clearHistory").onclick=()=>{if(confirm("Apagar todo o histórico de compras?")){history=[];saveHistory();renderHistory();toast("Histórico apagado.")}};
$("finishShopping").onclick=()=>{
 if(!items.length){toast("Adicione pelo menos um item antes de arquivar.");return}
 const boughtItems=items.filter(i=>i.bought);
 if(!boughtItems.length){toast("Marque os itens comprados antes de arquivar. Os pendentes continuam na lista.");return}
 const now=new Date(),date=now.toLocaleString("pt-BR",{dateStyle:"short",timeStyle:"short"});
 history.unshift({
   id:Date.now(),
   date,
   total:boughtItems.reduce((sum,i)=>sum+itemTotal(i),0),
   count:boughtItems.length,
   items:boughtItems.map(i=>({...i}))
 });
 history=history.slice(0,30);
 saveHistory();
 items=items.filter(i=>!i.bought);
 save();
 render();
 toast("Compra arquivada. Os itens pendentes continuam na lista.");
};
window.addEventListener("beforeinstallprompt",e=>{e.preventDefault();deferredPrompt=e;$("installBtn").hidden=false});
$("installBtn").onclick=async()=>{if(!deferredPrompt)return;deferredPrompt.prompt();await deferredPrompt.userChoice;deferredPrompt=null;$("installBtn").hidden=true};
window.addEventListener("appinstalled",()=>{$("installBtn").hidden=true});
if("serviceWorker"in navigator)navigator.serviceWorker.register("sw.js").catch(()=>{});
render();
}
document.addEventListener("DOMContentLoaded",init);
