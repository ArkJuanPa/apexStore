const $ = id => document.getElementById(id);
let cart = new Map(), orderId = null, idempotencyKey = null, events = null;
function log(value){$('log').value += `${new Date().toLocaleTimeString()} ${typeof value==='string'?value:JSON.stringify(value)}\n`; $('log').scrollTop=$('log').scrollHeight;}
async function api(path, options={}){const response=await fetch(`/api${path}`,{...options,headers:{'Content-Type':'application/json',...(options.headers||{})}});const body=await response.json().catch(()=>({}));log({status:response.status,path,body});if(!response.ok&&response.status!==202)throw new Error(body.mensaje||`HTTP ${response.status}`);return body;}
async function catalog(){const items=await api('/productos');$('catalogo').innerHTML=items.map(p=>`<article><b>${p.nombre}</b> — ${p.precioMenor} ${p.moneda} · stock ${p.stock} <button data-id="${p.productoId}">Agregar</button></article>`).join('');$('catalogo').querySelectorAll('button').forEach(b=>b.onclick=()=>{let id=Number(b.dataset.id);cart.set(id,(cart.get(id)||0)+1);drawCart();});}
function drawCart(){$('carrito').textContent=[...cart].map(([id,n])=>`Producto ${id}: ${n}`).join(' · ')||'Vacío';}
$('crearOrden').onclick=async()=>{try{if(!cart.size)throw new Error('Agregue productos al carrito');const o=await api('/ordenes',{method:'POST',body:JSON.stringify({items:[...cart].map(([productoId,cantidad])=>({productoId,cantidad}))})});orderId=o.id;$('ordenId').textContent=orderId;cart.clear();drawCart();watch();}catch(e){log(e.message);}};
async function pay(){try{if(!orderId)throw new Error('Cree una orden primero');if(!idempotencyKey)idempotencyKey=crypto.randomUUID();$('idempotency').textContent=idempotencyKey;await api(`/ordenes/${orderId}/pago`,{method:'POST',headers:{'Idempotency-Key':idempotencyKey},body:JSON.stringify({medio:$('medio').value,tokenPago:tokenizadorSimulado($('token').value)})});await refresh();}catch(e){log(e.message);}}
function tokenizadorSimulado(token){return token;}
$('pagar').onclick=pay;$('repetir').onclick=pay;
async function refresh(){if(!orderId)return;try{$('estado').textContent=JSON.stringify(await api(`/ordenes/${orderId}`),null,2);const m=await api('/dev/metricas');$('metricas').textContent=JSON.stringify(m,null,2);}catch(e){log(e.message);}}
function watch(){if(events)events.close();events=new EventSource(`/api/ordenes/${orderId}/eventos`);events.addEventListener('PagoActualizado',e=>{log({evento:'PagoActualizado',data:JSON.parse(e.data)});refresh();});events.onerror=()=>log('SSE reconectando; consulta periódica activa');refresh();}
setInterval(refresh,2000);catalog().catch(e=>log(e.message));
api('/salud').then(s=>{if(s.estado==='ok'&&s.desarrollo)$('panelDev').hidden=false;}).catch(()=>{});
$('aplicarSim').onclick=async()=>{try{await api(`/dev/simulacion/${$('simMedio').value}`,{method:'PUT',body:JSON.stringify({modo:$('simModo').value,latenciaMs:Number($('latencia').value),probFallo:Number($('probFallo').value)})});}catch(e){log(e.message);}};
