package com.thousandlabs.willofd.navigateur

import com.thousandlabs.willofd.donnees.Reglages
import org.json.JSONArray
import org.json.JSONObject

/**
 * Scripts injectés dans les pages. Le pont avec l'application s'appelle « WillOfD » ;
 * il ne fait que recevoir des signalements : aucune fonction sensible n'est exposée aux pages.
 */
object Scripts {

    /** Configuration du garde, recalculée à chaque création de page. */
    fun configGarde(): String = JSONObject()
        .put("fenetres", Reglages.bloquerFenetres)
        .put("calques", Reglages.antiCalques)
        .toString()

    /**
     * Garde exécuté au tout début de chaque document (page et cadres) :
     * fenêtres surgissantes, calques publicitaires invisibles, détection des vidéos et des contenus protégés.
     */
    fun garde(): String = "window.__wodCfg=" + configGarde() + ";" + GARDE

    private const val GARDE = """(function(){
if(window.__wod)return;window.__wod=1;
var cfg=window.__wodCfg||{};var W=window.WillOfD;
function base(h){h=(h||'').toLowerCase();var p=h.split('.');if(p.length<=2)return h;
if(p[p.length-2].length<=3&&p[p.length-1].length==2)return p.slice(-3).join('.');return p.slice(-2).join('.');}
var ici=base(location.hostname);
function autre(u){try{var x=new URL(u,location.href);if(x.protocol!='http:'&&x.protocol!='https:')return true;return base(x.hostname)!=ici;}catch(e){return false;}}
function sig(t,d){try{W&&W.signaler(t,String(d||''));}catch(e){}}
if(cfg.fenetres){var ouvrir=window.open;
window.open=function(u){var s=u?String(u):'';if(s&&!autre(s)){return ouvrir.apply(window,arguments);}sig('fenetre',s);
return {closed:true,close:function(){},focus:function(){},blur:function(){},postMessage:function(){},
document:{write:function(){},writeln:function(){},open:function(){},close:function(){}},location:{href:'',replace:function(){},assign:function(){}}};};}
if(cfg.calques){document.addEventListener('click',function(e){
var vw=window.innerWidth,vh=window.innerHeight,n=e.target;
for(var k=0;n&&n.nodeType==1&&n!==document.body&&n!==document.documentElement&&k<6;k++,n=n.parentElement){
var s=getComputedStyle(n);if(s.position!='fixed'&&s.position!='absolute')continue;
var r=n.getBoundingClientRect();if(r.width*r.height<vw*vh*0.3)continue;
var op=parseFloat(s.opacity),bg=s.backgroundColor||'';
var transp=op<0.2||bg=='transparent'||/rgba\([^)]*,\s*0(\.0+)?\)/.test(bg);
var zi=parseInt(s.zIndex)||0;
if(transp&&zi>=100&&!n.querySelector('video,img,canvas,iframe,input,button,textarea')&&!n.innerText.trim()){
e.preventDefault();e.stopImmediatePropagation();n.remove();sig('calque','');return;}}
},true);}
if(navigator.requestMediaKeySystemAccess){var rq=navigator.requestMediaKeySystemAccess.bind(navigator);
navigator.requestMediaKeySystemAccess=function(k,c){sig('drm',k);return rq(k,c);};}
function videos(){try{document.querySelectorAll('video,video source').forEach(function(v){
var u=v.currentSrc||v.src||v.getAttribute('src')||v.getAttribute('data-src')||'';if(!u)return;
if(u.indexOf('blob:')===0){if(!v.__wodB){v.__wodB=1;try{W&&W.media('','flux',location.href,document.title||'');}catch(e){}}return;}
if(u.indexOf('data:')===0||v.__wodU===u)return;v.__wodU=u;
try{W&&W.media(new URL(u,location.href).href,'element',location.href,document.title||'');}catch(e){}});}catch(e){}}
document.addEventListener('play',function(){setTimeout(videos,400);},true);
document.addEventListener('loadedmetadata',function(){setTimeout(videos,400);},true);
var tours=0;var iv=setInterval(function(){videos();if(++tours>40)clearInterval(iv);},3000);
})();"""

    /** Liste les images de la page (planches d'un chapitre), dans l'ordre du document. */
    const val SCAN_PAGES = """(function(){
function meilleure(img){var c=[img.getAttribute('data-src'),img.getAttribute('data-lazy-src'),img.getAttribute('data-original'),
img.getAttribute('data-url'),img.getAttribute('data-cfsrc'),img.getAttribute('data-full')];
var ss=img.getAttribute('srcset')||img.getAttribute('data-srcset');
if(ss){var m=ss.split(',').map(function(p){var t=p.trim().split(/\s+/);return {u:t[0],w:parseFloat(t[1])||0};}).sort(function(a,b){return b.w-a.w;});if(m.length)c.unshift(m[0].u);}
c.push(img.currentSrc);c.push(img.src);
for(var i=0;i<c.length;i++){var u=c[i];if(u&&u.trim()&&u.indexOf('data:image/gif')!==0&&u.indexOf('data:image/svg')!==0&&!/^\s*about:/.test(u)){
try{return new URL(u.trim(),location.href).href;}catch(e){}}}return '';}
var out=[],vus={};
document.querySelectorAll('img').forEach(function(img){var u=meilleure(img);if(!u||vus[u])return;vus[u]=1;
var r=img.getBoundingClientRect();var paresseuse=!!(img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.getAttribute('data-original'));
out.push({u:u,w:img.naturalWidth||0,h:img.naturalHeight||0,dw:Math.round(r.width),dh:Math.round(r.height),p:paresseuse});});
return JSON.stringify({titre:document.title||'',url:location.href,images:out});})()"""

    /** Déblocage ciblé : clic droit, sélection, calques posés sur les médias, images paresseuses, lecteurs relancés. */
    const val CONTOURNEMENT = """(function(){
var st=document.createElement('style');st.textContent='*{user-select:auto!important;-webkit-user-select:auto!important;-webkit-touch-callout:default!important;}';
(document.head||document.documentElement).appendChild(st);
['contextmenu','selectstart','dragstart','copy'].forEach(function(t){document.addEventListener(t,function(e){e.stopPropagation();},true);});
document.oncontextmenu=null;if(document.body)document.body.oncontextmenu=null;
document.querySelectorAll('div,span,a').forEach(function(n){var s=getComputedStyle(n);
if((s.position=='absolute'||s.position=='fixed')&&parseFloat(s.opacity)<0.1&&n.children.length==0){var r=n.getBoundingClientRect();if(r.width>150&&r.height>150)n.remove();}});
document.querySelectorAll('img').forEach(function(img){var d=img.getAttribute('data-src')||img.getAttribute('data-lazy-src')||img.getAttribute('data-original');
if(d&&(!img.src||img.src.indexOf('data:')===0))img.src=d;try{img.loading='eager';}catch(e){}});
document.querySelectorAll('video').forEach(function(v){try{v.muted=true;var p=v.play();if(p&&p.catch)p.catch(function(){});
setTimeout(function(){try{v.pause();v.muted=false;}catch(e){}},2500);}catch(e){}});
return 'ok';})()"""

    /** Récupère les images depuis la page elle-même (cookies et protections du site compris). */
    fun extraction(tache: String, urls: List<String>): String =
        "(" + EXTRACTION + ")(" + JSONObject.quote(tache) + "," + JSONArray(urls).toString() + ");"

    private const val EXTRACTION = """function(job,urls){var W=window.WillOfD;var i=0;
function trouver(u){var l=document.images;for(var k=0;k<l.length;k++){if(l[k].currentSrc==u||l[k].src==u||l[k].getAttribute('data-src')==u)return l[k];}return null;}
function toile(k,u){var img=trouver(u);if(img&&img.complete&&img.naturalWidth){try{var cv=document.createElement('canvas');cv.width=img.naturalWidth;cv.height=img.naturalHeight;
cv.getContext('2d').drawImage(img,0,0);var d=cv.toDataURL('image/png');W.image(job,k,d.substring(d.indexOf(',')+1),'image/png');return;}catch(e){}}W.imageEchec(job,k);}
function suivant(){if(i>=urls.length){W.extractionFinie(job);return;}var k=i++;var u=urls[k];
fetch(u,{credentials:'include'}).then(function(r){if(!r.ok)throw 0;return r.blob();}).then(function(b){if(b.size<800||(b.type&&b.type.indexOf('text')===0))throw 0;
var fr=new FileReader();fr.onload=function(){var s=String(fr.result);W.image(job,k,s.substring(s.indexOf(',')+1),b.type||'');suivant();};
fr.onerror=function(){toile(k,u);suivant();};fr.readAsDataURL(b);}).catch(function(){toile(k,u);suivant();});}
suivant();}"""

    /** Mode « Masquer un élément » : on touche un élément, il est encadré, puis masqué sur tout le site. */
    const val SELECTEUR = """(function(){
if(window.__wodPick)return;window.__wodPick=true;
var box=document.createElement('div');
box.style.cssText='position:absolute;z-index:2147483647;pointer-events:none;border:2px solid #E8B84A;background:rgba(232,184,74,.18);border-radius:3px;';
document.documentElement.appendChild(box);var cur=null;
function esc(s){return window.CSS&&CSS.escape?CSS.escape(s):s;}
function sel(el){var parts=[];var e=el;while(e&&e.nodeType==1&&e!==document.body&&parts.length<4){
if(e.id&&!/\d{3,}/.test(e.id)){parts.unshift('#'+esc(e.id));break;}
var p=e.tagName.toLowerCase();var cls=Array.prototype.filter.call(e.classList,function(c){return !/\d{3,}/.test(c)&&c.length<40;}).slice(0,2);
if(cls.length)p+='.'+cls.map(esc).join('.');parts.unshift(p);e=e.parentElement;}return parts.join(' > ');}
function show(el){cur=el;var r=el.getBoundingClientRect();box.style.left=(r.left+window.scrollX)+'px';box.style.top=(r.top+window.scrollY)+'px';
box.style.width=r.width+'px';box.style.height=r.height+'px';var s=sel(el);var n=0;try{n=document.querySelectorAll(s).length;}catch(x){}
window.WillOfD.choisi(s,(el.innerText||el.tagName).slice(0,80),n);}
window.__wodPickH=function(e){e.preventDefault();e.stopPropagation();show(e.target);};
document.addEventListener('click',window.__wodPickH,true);
window.wodPick={parent:function(){if(cur&&cur.parentElement&&cur.parentElement!==document.body&&cur.parentElement!==document.documentElement)show(cur.parentElement);},
stop:function(){document.removeEventListener('click',window.__wodPickH,true);box.remove();window.__wodPick=false;},
hide:function(s){try{document.querySelectorAll(s).forEach(function(x){x.style.setProperty('display','none','important');});}catch(x){}}};
})();"""

    fun cssMasquage(css: String): String =
        "(function(){var s=document.getElementById('wod-cos');if(!s){s=document.createElement('style');s.id='wod-cos';" +
            "(document.head||document.documentElement).appendChild(s);}s.textContent=" + JSONObject.quote(css) + ";})();"
}
