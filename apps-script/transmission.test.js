const fs = require('fs');
const vm = require('vm');
const assert = require('assert');
const fakeCache = new Map();

const context = {
  console,
  Logger: { log() {} },
  Utilities: {
    DigestAlgorithm:{SHA_256:'sha256'},
    formatDate() { return '2026-10-23 12:00:00'; },
    computeDigest(algorithm,value) { return Array.from(require('crypto').createHash(algorithm).update(value).digest()); },
    base64EncodeWebSafe(bytes) { return Buffer.from(bytes).toString('base64').replace(/\+/g,'-').replace(/\//g,'_'); }
  },
  PropertiesService: { getScriptProperties() { return { getProperty() { return null; }, setProperty() {}, deleteProperty() {} }; } },
  CacheService: { getScriptCache() { return { get(key) { return fakeCache.get(key) || null; }, put(key,value) { fakeCache.set(key,value); } }; } },
  UrlFetchApp: { fetchAll() { throw new Error('fetchAll no fue configurado en la prueba'); } },
  ContentService: { MimeType: { JSON: 'json' } }
};
vm.createContext(context);
vm.runInContext(fs.readFileSync(__dirname + '/Codigo_Tavily_transmisiones_UY_v11.gs', 'utf8'), context);

function source(title, content, url='https://www.montevideo.com.uy/Deportes/partido') {
  return { tipoBusqueda:'TRANSMISION', title, content, url, published_date:'2026-10-23' };
}
function match(local, visitante, competition='') {
  return { fixtureId:'test', local, visitante, competicion:competition, fecha:'25/10/2026' };
}

// Caso que ya funcionaba: debe seguir reconociendo una fuente uruguaya exacta.
let result = context.detectarTransmisionUruguay_([
  source('Manchester City vs Aston Villa: dónde ver', 'Manchester City vs Aston Villa: transmisión para Uruguay por ESPN y Disney+ Premium.')
], match('Manchester City','Aston Villa','Premier League'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['ESPN']);
assert.deepEqual(Array.from(result.streaming), ['Disney+ Premium']);

// Club uruguayo y nombres con tilde.
result = context.detectarTransmisionUruguay_([
  source('Peñarol vs Nacional: dónde ver en vivo', 'Peñarol vs Nacional: en Uruguay se televisa por VTV Plus y se transmite por Disney+ Premium.')
], match('Peñarol','Nacional','Primera División · Clausura'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['VTV Plus']);

// Alias internacional: Bayern Múnich debe coincidir con Bayern Munich.
result = context.detectarTransmisionUruguay_([
  source('Bayern Munich vs Arsenal: cómo ver la Champions', 'Bayern Munich vs Arsenal: disponible en Uruguay por ESPN 2 y Disney+ Premium.')
], match('Bayern Múnich','Arsenal FC','UEFA Champions League'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['ESPN 2']);

// Una guía para otro país no confirma disponibilidad en Uruguay.
result = context.detectarTransmisionUruguay_([
  source('Bayern Munich vs Arsenal: dónde ver', 'En Argentina se transmite por ESPN y Disney+.', 'https://www.espn.com.ar/futbol/')
], match('Bayern Múnich','Arsenal FC','UEFA Champions League'));
assert.equal(result.confirmada, false);

// A league's general rights do not establish where this particular fixture is shown.
result = context.detectarTransmisionUruguay_([
  source('Arsenal vs Everton: Premier League', 'La Premier League se transmite en Uruguay por ESPN.')
], match('Arsenal','Everton','Premier League'));
assert.equal(result.confirmada, false);

// Both clubs and Uruguay must appear with the broadcast attribution itself.
result = context.detectarTransmisionUruguay_([
  source('Arsenal vs Everton: dónde ver', 'La Premier League se transmite en Uruguay por ESPN. Arsenal juega ante Everton el sábado.')
], match('Arsenal','Everton','Premier League'));
assert.equal(result.confirmada, false);

// Sin datos y respuesta incompleta de Gemini.
assert.equal(context.detectarTransmisionUruguay_([], match('Peñarol','Nacional')).confirmada, false);
const detected = {pais:'Uruguay',confirmada:true,canales:['VTV Plus'],streaming:[],texto:'Confirmada'};
assert.equal(context.resolverTransmisionUruguay_({}, detected, null).confirmada, true);

// Una actualización incompleta conserva una transmisión confirmada guardada;
// esto cubre también el paso de partido próximo a partido en vivo.
const saved = {transmision:{pais:'Uruguay',confirmada:true,canales:['VTV Plus'],streaming:[],texto:'Confirmada'}};
result = context.resolverTransmisionUruguay_({}, {pais:'Uruguay',confirmada:false,canales:[],streaming:[]}, saved);
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['VTV Plus']);

// A manual refresh reuses cached research and forces only the transmission search.
const consultas = [
  {tipo:'PARTIDO',query:'match',topic:'news',maxResults:8},
  {tipo:'LOCAL',query:'home',topic:'general',searchDepth:'advanced',maxResults:10},
  {tipo:'VISITANTE',query:'away',topic:'general',searchDepth:'advanced',maxResults:10},
  {tipo:'TRANSMISION',query:'where to watch',topic:'general',searchDepth:'advanced',maxResults:8}
];
for (const type of ['PARTIDO','LOCAL','VISITANTE','TRANSMISION']) {
  const prefix = type === 'TRANSMISION' ? 'tavily_q_transmision_uy_v4_' : 'tavily_q_v2_';
  fakeCache.set(prefix+'fixture-1_'+type, JSON.stringify([{title:'cached '+type,url:'https://source.test',content:'cached'}]));
}
let reserved = 0, fetchCount = 0;
context.reservarCreditosTavily_ = credits => { reserved += credits; };
context.registrarActividadTavily_ = () => {};
context.UrlFetchApp.fetchAll = requests => {
  fetchCount += requests.length;
  return requests.map(() => ({getResponseCode(){return 200;},getContentText(){return JSON.stringify({results:[{title:'Fresh transmission',url:'https://source.test/new',content:'fresh',score:1}]});}}));
};
let update = context.buscarTavilyPartidosEnParalelo_('mock-key',consultas,'Uruguay','fixture-1',false,false);
assert.equal(fetchCount,0);
assert.equal(update.busquedas.cacheadas.length,4);
assert.equal(update.busquedas.resultadosNuevos,0);

update = context.buscarTavilyPartidosEnParalelo_('mock-key',consultas,'Uruguay','fixture-1',false,true);
assert.equal(fetchCount,1);
assert.equal(reserved,2);
assert.equal(update.busquedas.cacheadas.length,3);
assert.deepEqual(Array.from(update.busquedas.actualizadas),['TRANSMISION']);
assert.equal(update.busquedas.resultadosNuevos,1);

context.UrlFetchApp.fetchAll = requests => requests.map(() => ({getResponseCode(){return 429;},getContentText(){return 'simulated rate limit';}}));
update = context.buscarTavilyPartidosEnParalelo_('mock-key',consultas,'Uruguay','fixture-1',false,true);
assert.equal(update.busquedas.fallidas.length,1);
assert.equal(update.busquedas.resultadosNuevos,0);

// A normal summary read stays on server cache and exposes why.
fakeCache.set('matchSummary_tavily_lite_v13_fixture-cached',JSON.stringify({
  ok:true,encontrado:true,fixtureId:'fixture-cached',contexto:'saved summary',
  actualizado:'2026-10-23T12:00:00.000Z',desdeCache:false
}));
const cachedSummary = context.obtenerResumenPartido({
  fixtureId:'fixture-cached',local:'Peñarol',visitante:'Nacional',fecha:'25/10/2026'
},false,false);
assert.equal(cachedSummary.desdeCache,true);
assert.equal(cachedSummary.estadoActualizacion,'cache');
assert.ok(cachedSummary.motivoCache.includes('resumen guardado'));

// The HTTP route carries manual refresh through and never grants a daily-limit bypass.
let routedArgs = null;
context.validateAccess_ = () => {};
context.json_ = value => value;
context.obtenerResumenPartido = (...args) => { routedArgs = args; return {ok:true}; };
const route = context.doGet({parameter:{action:'matchSummary',fixtureId:'fixture-route',local:'Peñarol',visitante:'Nacional',fecha:'25/10/2026',refresh:'true',allowOverLimit:'true'}});
assert.equal(route.ok,true);
assert.equal(routedArgs[1],true);
assert.equal(routedArgs[2],false);

// Live polling uses an isolated four-minute shared cache; normal sync keeps
// its existing 15-minute cache.
context.PropertiesService = { getScriptProperties() { return { getProperty(name) { return name === 'GOAL_API_KEY' ? 'mock-goal-key' : 'mock-access-token'; } }; } };
let goalCalls = 0;
const goalCacheTtls = [];
const originalFetchApi = context.fetchApi_;
context.fetchApi_ = (path,query,ttl,liveRefresh) => { goalCacheTtls.push({ttl,liveRefresh}); return originalFetchApi(path,query,ttl,liveRefresh); };
context.UrlFetchApp.fetch = () => {
  goalCalls++;
  return {getResponseCode(){return 200;},getContentText(){return JSON.stringify({success:true,data:[],pagination:{hasMore:false}});}};
};
const goalRoute = params => context.doGet({parameter:Object.assign({action:'fixturesByDate',date:'2026-10-28',limit:'500',token:'mock-access-token'},params)});
const normalResponse = goalRoute({});
assert.equal(normalResponse.ok,true,JSON.stringify(normalResponse));
goalRoute({});
assert.equal(goalCalls,1,'las lecturas normales deben reutilizar caché');
assert.equal(goalCacheTtls[0].ttl,900,'la sincronización normal conserva la caché existente');
goalRoute({liveRefresh:'true'});
goalRoute({liveRefresh:'true'});
assert.equal(goalCalls,2,'los sondeos comparten su propia respuesta GOAL');
assert.equal(goalCacheTtls[2].ttl,240,'la caché del sondeo es inferior al intervalo de cinco minutos');
assert.equal(goalCacheTtls[2].liveRefresh,true);

console.log('OK: 15 escenarios simulados (emisión, caché, actualización, límites, rutas HTTP y cadencia GOAL)');
