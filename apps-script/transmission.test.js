const fs = require('fs');
const vm = require('vm');
const assert = require('assert');

const context = {
  console,
  Logger: { log() {} },
  Utilities: { formatDate() { return '2026-10-23 12:00:00'; } },
  PropertiesService: { getScriptProperties() { return { getProperty() { return null; }, setProperty() {}, deleteProperty() {} }; } },
  CacheService: { getScriptCache() { return { get() { return null; }, put() {} }; } },
  UrlFetchApp: {},
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
  source('Manchester City vs Aston Villa: dónde ver', 'Transmisión para Uruguay por ESPN y Disney+ Premium.')
], match('Manchester City','Aston Villa','Premier League'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['ESPN']);
assert.deepEqual(Array.from(result.streaming), ['Disney+ Premium']);

// Club uruguayo y nombres con tilde.
result = context.detectarTransmisionUruguay_([
  source('Peñarol vs Nacional: dónde ver en vivo', 'En Uruguay se televisa por VTV Plus y se transmite por Disney+ Premium.')
], match('Peñarol','Nacional','Primera División · Clausura'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['VTV Plus']);

// Alias internacional: Bayern Múnich debe coincidir con Bayern Munich.
result = context.detectarTransmisionUruguay_([
  source('Bayern Munich vs Arsenal: cómo ver la Champions', 'Disponible en Uruguay por ESPN 2 y Disney+ Premium.')
], match('Bayern Múnich','Arsenal FC','UEFA Champions League'));
assert.equal(result.confirmada, true);
assert.deepEqual(Array.from(result.canales), ['ESPN 2']);

// Una guía para otro país no confirma disponibilidad en Uruguay.
result = context.detectarTransmisionUruguay_([
  source('Bayern Munich vs Arsenal: dónde ver', 'En Argentina se transmite por ESPN y Disney+.', 'https://www.espn.com.ar/futbol/')
], match('Bayern Múnich','Arsenal FC','UEFA Champions League'));
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

console.log('OK: 7 escenarios de transmisión simulados');
