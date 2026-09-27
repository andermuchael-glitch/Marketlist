const CACHE="marketlist-pwa-v7";
const ASSETS=["./","./index.html","./styles.css","./app.js","./manifest.webmanifest","./icon.svg"];

self.addEventListener("install",event=>{
  event.waitUntil(
    caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting())
  );
});

self.addEventListener("activate",event=>{
  event.waitUntil(
    caches.keys()
      .then(keys=>Promise.all(keys.filter(key=>key.startsWith("marketlist-pwa-")&&key!==CACHE).map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

self.addEventListener("fetch",event=>{
  if(event.request.method!=="GET") return;

  const url=new URL(event.request.url);
  const isAppAsset=["/","/index.html","/styles.css","/app.js","/manifest.webmanifest","/icon.svg"].some(path=>url.pathname.endsWith(path));

  if(isAppAsset){
    event.respondWith(
      fetch(event.request,{cache:"no-store"})
        .then(response=>{
          if(!response.ok) throw new Error("HTTP "+response.status);
          const copy=response.clone();
          caches.open(CACHE).then(cache=>cache.put(event.request,copy));
          return response;
        })
        .catch(()=>caches.match(event.request,{ignoreSearch:true}).then(cached=>cached||caches.match("./index.html")))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request,{ignoreSearch:true}).then(cached=>cached||fetch(event.request))
  );
});
