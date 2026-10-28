const API_BASE = 'https://api.goal-api.com/v1';

/**
 * Intermediario GOAL API para Mis Partidos.
 * Propiedades obligatorias del script:
 *   GOAL_API_KEY = clave privada de GOAL API
 *   ACCESS_TOKEN = texto largo elegido por el usuario
 */
function doGet(e) {

  try {

    const params =
      (e && e.parameter) || {};


    const action =
      params.action || 'health';


    // ==========================================================
    // VALIDAR ACCESO
    // ==========================================================
    // La consulta de diagnóstico Tavily se permite sin token para
    // poder verla directamente desde el navegador. Todas las demás
    // acciones conservan exactamente la validación existente.

    if (action !== 'tavilyDiagnostic' || String(params.reset || '').toLowerCase() === 'true') {
      validateAccess_(params.token);
    }


    // ==========================================================
    // HEALTH
    // ==========================================================

    if (action === 'health') {

      return json_({
        ok: true,
        service: 'mis-partidos-proxy',
        provider: 'GOAL API',
        version: 5
      });

    }


    // ==========================================================
    // DIAGNÓSTICO
    // ==========================================================

    if (action === 'diagnostic') {

      return json_(
        diagnostic_()
      );

    }


    // ==========================================================
    // DIAGNÓSTICO DE CONSUMO TAVILY
    // ==========================================================

    if (action === 'tavilyDiagnostic') {
      return json_(obtenerDiagnosticoTavily_(
        String(params.reset || '').toLowerCase() === 'true'
      ));
    }

    if (action === 'tavilyBudget') {
      return json_({ok:true, action:action, data:estadoPresupuestoTavily_()});
    }

    if (action === 'transmissionDiagnostic') {
      return json_(obtenerDiagnosticoTransmision_(params.fixtureId));
    }


    // ==========================================================
    // LIGAS POR PAÍS
    // ==========================================================

    if (action === 'countryLeagues') {

      return json_({

        ok: true,

        action: action,

        data:
          countryLeagues_(
            params.country
          )

      });

    }


    // ==========================================================
    // RESUMEN IA DEL PARTIDO
    // ==========================================================

    if (action === 'matchSummary') {

      const datosPartido = {

        fixtureId:
          params.fixtureId || '',

        local:
          params.local || '',

        visitante:
          params.visitante || '',

        pais:
          params.pais || '',

        competicion:
          params.competicion || '',

        fecha:
          params.fecha || '',

        hora:
          params.hora || '',

        localId:
          params.localId || '',

        visitanteId:
          params.visitanteId || ''

      };


      const forzarActualizacion =
        String(
          params.refresh || ''
        ).toLowerCase() === 'true';


      const resultado =
        obtenerResumenPartido(
          datosPartido,
          forzarActualizacion,
          false
        );


      return json_({

        ok: true,

        action: action,

        data: resultado

      });

    }


    // ==========================================================
    // RESTO DE ACCIONES GOAL API
    // ==========================================================

    const request =
      buildRequest_(
        action,
        params
      );


    const data =
      action === 'fixturesByDate'

        ? fetchPagedFixtures_(
            request.path,
            Number(request.query.limit),
            String(params.liveRefresh || '').toLowerCase() === 'true' ? 240 : request.cacheSeconds,
            String(params.liveRefresh || '').toLowerCase() === 'true'
          )

        : fetchApi_(
            request.path,
            request.query,
            request.cacheSeconds
          );


    return json_({

      ok: true,

      action: action,

      data: data

    });


  }
  catch (error) {

    if (error && (error.code === 'TAVILY_DAILY_LIMIT' || error.code === 'TAVILY_MONTHLY_LIMIT')) {
      return json_({ok:false, errorCode:error.code, error:error.message, budget:error.budget});
    }
    return json_({

      ok: false,

      error:
        String(
          error &&
          error.message ||
          error
        )

    });

  }

}

function validateAccess_(providedToken) {
  const expected = PropertiesService.getScriptProperties().getProperty('ACCESS_TOKEN');
  if (!expected) throw new Error('Falta configurar ACCESS_TOKEN en las propiedades del script.');
  if (!providedToken || providedToken !== expected) throw new Error('Acceso no autorizado.');
}

function buildRequest_(action, p) {
  switch (action) {
    case 'teams':
      const search = required_(p.search, 'search');
      if (search.length < 2) throw new Error('La búsqueda debe tener al menos 2 caracteres.');
      return {
        path: '/teams',
        query: compact_({ search: clean_(search, 80), country: clean_(p.country, 80), limit: limit_(p.limit, 20) }),
        cacheSeconds: 21600
      };
    case 'teamUpcoming':
      return {
        path: '/teams/' + encodeURIComponent(required_(p.team, 'team')) + '/upcoming',
        query: { limit: limit_(p.limit, 10) },
        cacheSeconds: 1800
      };
    case 'teamFixtures':
      return {
        path: '/teams/' + encodeURIComponent(required_(p.team, 'team')) + '/fixtures',
        query: { limit: limit_(p.limit, 100) },
        cacheSeconds: 1800
      };
    case 'leagueTeams':
      return {
        path: '/leagues/' + encodeURIComponent(required_(p.league, 'league')) + '/teams',
        query: { limit: limit_(p.limit, 100) },
        cacheSeconds: 21600
      };
    case 'fixturesByDate':
      return {
        path: '/fixtures/date/' + date_(p.date),
        query: { limit: limit_(p.limit, 100) },
        cacheSeconds: 900
      };
    default:
      throw new Error('Acción no permitida: ' + action);
  }
}

function countryLeagues_(countryName) {
  const wanted = required_(countryName, 'country');
  const countriesResponse = fetchApi_('/countries', { search: wanted, limit: '20' }, 21600);
  const countries = dataArray_(countriesResponse);
  let selected = null;
  for (let i = 0; i < countries.length; i++) {
    if (normalize_(countries[i].name) === normalize_(wanted)) { selected = countries[i]; break; }
  }
  if (!selected && countries.length) selected = countries[0];
  if (!selected || !selected.id) throw new Error('No se encontró el país en GOAL API.');
  return fetchApi_('/countries/' + encodeURIComponent(String(selected.id)) + '/leagues', { limit: '100' }, 21600);
}

/** Prueba Peñarol, sus próximos partidos y los partidos de hoy. */
function diagnostic_() {
  let teamsResponse = fetchApi_('/teams', { search: 'Penarol', country: 'Uruguay', limit: '20' }, 300);
  let selected = selectPenarol_(dataArray_(teamsResponse));
  if (!selected) {
    teamsResponse = fetchApi_('/teams', { search: 'Peñarol', country: 'Uruguay', limit: '20' }, 300);
    selected = selectPenarol_(dataArray_(teamsResponse));
  }
  if (!selected) throw new Error('GOAL API respondió, pero no encontró a Peñarol en Uruguay.');

  const teamId = selected.id || (selected.team && selected.team.id);
  if (!teamId) throw new Error('GOAL API encontró a Peñarol, pero no devolvió su identificador.');

  const upcomingResponse = fetchApi_('/teams/' + encodeURIComponent(String(teamId)) + '/upcoming', { limit: '10' }, 300);
  const today = Utilities.formatDate(new Date(), 'America/Montevideo', 'yyyy-MM-dd');
  const todayResponse = fetchPagedFixtures_('/fixtures/date/' + today, 100, 300);
  const upcoming = dataArray_(upcomingResponse);
  const todayFixtures = dataArray_(todayResponse);

  return {
    ok: true,
    service: 'mis-partidos-proxy',
    provider: 'GOAL API',
    version: 4,
    dateUruguay: today,
    checks: {
      penarol: { ok: true, team: selected },
      upcoming: { ok: true, count: upcoming.length, sample: upcoming.slice(0, 3) },
      today: { ok: true, count: todayFixtures.length, sample: todayFixtures.slice(0, 3) }
    }
  };
}

/**
 * El contrato público admite hasta 500, pero el endpoint de fechas puede
 * rechazar ese tamaño. Se pagina en bloques de 100 y se unifica la respuesta.
 */
function fetchPagedFixtures_(path, requestedLimit, cacheSeconds, liveRefresh) {
  // GOAL puede devolver más de 100 fixtures en un día. Cada llamada sigue
  // limitada a bloques de 100, pero recorremos páginas hasta un máximo de 500.
  const target = Math.max(1, Math.min(Number(requestedLimit) || 500, 500));
  const pageSize = Math.min(target, 100);
  let all = [];
  let offset = 0;

  while (all.length < target) {
    const response = fetchApi_(path, { limit: String(pageSize), offset: String(offset) }, cacheSeconds, liveRefresh);
    const page = dataArray_(response);
    all = all.concat(page);

    const pagination = response && response.pagination;
    const hasMore = pagination ? pagination.hasMore === true : page.length === pageSize;
    if (!hasMore || page.length === 0) break;
    offset += page.length;
  }

  return {
    success: true,
    data: all.slice(0, target),
    pagination: {
      limit: target,
      offset: 0,
      total: all.length,
      hasMore: all.length > target
    }
  };
}

function fetchApi_(path, query, cacheSeconds, liveRefresh) {
  const key = PropertiesService.getScriptProperties().getProperty('GOAL_API_KEY');
  if (!key) throw new Error('Falta configurar GOAL_API_KEY en las propiedades del script.');

  const queryString = Object.keys(query || {}).sort().map(function (name) {
    return encodeURIComponent(name) + '=' + encodeURIComponent(query[name]);
  }).join('&');
  const url = API_BASE + path + (queryString ? '?' + queryString : '');
  const cache = CacheService.getScriptCache();
  const cacheKey = (liveRefresh ? 'goal_live_' : 'goal_') + digest_(url);
  const cached = cache.get(cacheKey);
  if (cached) return JSON.parse(cached);

  const response = UrlFetchApp.fetch(url, {
    method: 'get',
    headers: { Authorization: 'Bearer ' + key, Accept: 'application/json' },
    muteHttpExceptions: true
  });
  const status = response.getResponseCode();
  const body = response.getContentText();
  let parsed;
  try { parsed = JSON.parse(body); } catch (_) { throw new Error('Respuesta inválida de GOAL API (HTTP ' + status + ').'); }
  if (status < 200 || status >= 300) {
    throw new Error('GOAL API respondió HTTP ' + status + ': ' + compactError_(parsed, body));
  }

  try { cache.put(cacheKey, JSON.stringify(parsed), cacheSeconds); } catch (_) {}
  return parsed;
}

function dataArray_(response) {
  if (Array.isArray(response)) return response;
  if (response && Array.isArray(response.data)) return response.data;
  if (response && response.data && Array.isArray(response.data.items)) return response.data.items;
  if (response && Array.isArray(response.items)) return response.items;
  return [];
}

function selectPenarol_(teams) {
  if (!teams.length) return null;
  const exact = teams.filter(function (item) {
    const team = item.team || item;
    return normalize_(team.name || '') === 'penarol';
  });
  return exact[0] || teams[0];
}

function normalize_(value) {
  return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();
}

function compactError_(parsed, fallback) {
  if (parsed && parsed.message) return String(parsed.message).substring(0, 500);
  if (parsed && parsed.error) return String(parsed.error).substring(0, 500);
  return String(fallback || '').substring(0, 500);
}

function compact_(value) {
  const result = {};
  Object.keys(value).forEach(function (key) {
    if (value[key] !== '' && value[key] !== null && value[key] !== undefined) result[key] = value[key];
  });
  return result;
}

function required_(value, name) {
  const cleaned = clean_(value || '', 120);
  if (!cleaned) throw new Error('Falta el parámetro ' + name + '.');
  return cleaned;
}

function clean_(value, maxLength) {
  return String(value || '').trim().substring(0, maxLength);
}

function limit_(value, defaultValue) {
  const raw = value === undefined || value === null || value === '' ? String(defaultValue) : String(value);
  if (!/^\d+$/.test(raw)) throw new Error('El límite no es válido.');
  const number = Number(raw);
  if (number < 1 || number > 500) throw new Error('El límite debe estar entre 1 y 500.');
  return String(number);
}

function date_(value) {
  const date = String(value || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error('La fecha debe tener formato AAAA-MM-DD.');
  return date;
}

function digest_(value) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, value);
  return Utilities.base64EncodeWebSafe(bytes).replace(/=+$/, '').substring(0, 40);
}

function json_(value) {
  return ContentService.createTextOutput(JSON.stringify(value)).setMimeType(ContentService.MimeType.JSON);
}


function probarGemini() {
  const apiKey = PropertiesService
    .getScriptProperties()
    .getProperty('GEMINI_API_KEY');

  if (!apiKey) {
    throw new Error('No existe GEMINI_API_KEY en Script Properties');
  }

  const url =
    'https://generativelanguage.googleapis.com/v1beta/interactions';

  const prompt = `
Respondé en español.

Estoy desarrollando una aplicación de fútbol.

Quiero una ficha muy breve sobre estos dos equipos:

AC Milan
Inter de Milán

Devolvé únicamente:

MILAN
- Descripción:
- País:
- Estadio:

INTER
- Descripción:
- País:
- Estadio:

Máximo 120 palabras.
`;

  const payload = {
    model: 'gemini-3.6-flash',
    input: prompt
  };

  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    headers: {
      'x-goog-api-key': apiKey
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });

  const status = response.getResponseCode();
  const body = response.getContentText();

  Logger.log('HTTP: ' + status);
  Logger.log(body);

  if (status !== 200) {
    throw new Error(
      'Error Gemini HTTP ' + status + ': ' + body
    );
  }

  const data = JSON.parse(body);

  let texto = '';

  if (data.outputs) {
    texto = data.outputs
      .filter(o => o.type === 'text')
      .map(o => o.text || '')
      .join('\n');
  }

  // Compatibilidad con la estructura de respuesta actual
  if (!texto && data.steps) {
    data.steps.forEach(step => {
      if (step.content) {
        step.content.forEach(item => {
          if (item.type === 'text' && item.text) {
            texto += item.text + '\n';
          }
        });
      }
    });
  }

  Logger.log('----------------');
  Logger.log('RESPUESTA GEMINI:');
  Logger.log(texto);

  return texto;
}

function probarGeminiConBusqueda() {

  const apiKey = PropertiesService
    .getScriptProperties()
    .getProperty('GEMINI_API_KEY');

  if (!apiKey) {
    throw new Error('No existe GEMINI_API_KEY en Script Properties');
  }

  const url =
    'https://generativelanguage.googleapis.com/v1beta/interactions';

  const prompt = `
Estamos desarrollando una aplicación de fútbol.

Buscá información ACTUAL en Internet sobre:

Peñarol vs Liverpool FC de Uruguay.

Quiero información reciente y relevante para una persona
que está por mirar este partido.

Respondé en español y de forma breve.

Estructura:

CONTEXTO
Breve explicación del partido y la competición.

CÓMO LLEGA PEÑAROL
Resumen muy corto de su situación y resultados recientes.

CÓMO LLEGA LIVERPOOL
Resumen muy corto de su situación y resultados recientes.

ÚLTIMAS NOVEDADES
- Máximo 3 novedades relevantes.

BAJAS IMPORTANTES
- Solamente lesiones, suspensiones o ausencias que estén
  confirmadas por fuentes recientes.
- Si no encontrás información confiable, indicá
  "Sin información confirmada".

No inventes datos.
Priorizá información reciente.
`;

  const payload = {
    model: 'gemini-3.6-flash',
    input: prompt,

    tools: [
      {
        type: 'google_search'
      }
    ]
  };

  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    headers: {
      'x-goog-api-key': apiKey
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });

  const status = response.getResponseCode();
  const body = response.getContentText();

  Logger.log('HTTP: ' + status);
  Logger.log(body);

  if (status !== 200) {
    throw new Error(
      'Error Gemini HTTP ' + status + ': ' + body
    );
  }

  const data = JSON.parse(body);

  let texto = '';

  if (data.steps) {
    data.steps.forEach(step => {

      if (step.type === 'model_output' && step.content) {

        step.content.forEach(item => {

          if (item.type === 'text' && item.text) {
            texto += item.text + '\n';
          }

        });

      }

    });
  }

  Logger.log('==============================');
  Logger.log('RESPUESTA CON BÚSQUEDA:');
  Logger.log(texto);
  Logger.log('==============================');

  return texto;
}

function probarTavily() {

  const apiKey = PropertiesService
    .getScriptProperties()
    .getProperty('TAVILY_API_KEY');

  if (!apiKey) {
    throw new Error(
      'No existe TAVILY_API_KEY en Script Properties'
    );
  }

  const url = 'https://api.tavily.com/search';

  const equipoLocal = 'Peñarol';
  const equipoVisitante = 'Liverpool';
  const pais = 'Uruguay';
  const fechaPartido = '20 septiembre 2026';

  const consulta =
    `"${equipoLocal}" "${equipoVisitante}" ` +
    `${pais} fútbol masculino ` +
    `${fechaPartido} ` +
    `partido previa últimas noticias ` +
    `lesiones bajas suspendidos alineaciones`;

  Logger.log('Consulta enviada:');
  Logger.log(consulta);

  const payload = {

    query: consulta,

    topic: 'news',

    search_depth: 'advanced',

    max_results: 10,

    include_answer: false,

    include_raw_content: false,

    // Queremos información reciente
    days: 14,

    // Le damos preferencia a fuentes deportivas
    // que sabemos que cubren fútbol uruguayo.
    include_domains: [
      'montevideo.com.uy',
      'elpais.com.uy',
      'elobservador.com.uy',
      'espn.com.uy',
      'auf.org.uy'
    ]

  };

  reservarCreditosTavily_(2, false);
  registrarActividadTavily_('prueba_manual', 'PRUEBA', '', 'TAVILY', 'llamada real');

  const response = UrlFetchApp.fetch(url, {
    method: 'post',
    contentType: 'application/json',
    headers: {
      'Authorization': 'Bearer ' + apiKey
    },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true
  });

  const status = response.getResponseCode();
  const body = response.getContentText();

  Logger.log('HTTP: ' + status);

  if (status !== 200) {
    Logger.log(body);

    throw new Error(
      'Error Tavily HTTP ' + status + ': ' + body
    );
  }

  const data = JSON.parse(body);

  Logger.log('==============================');
  Logger.log('RESULTADOS TAVILY');
  Logger.log('==============================');

  if (!data.results || data.results.length === 0) {
    Logger.log('No se encontraron resultados recientes.');
    return;
  }

  data.results.forEach(function(resultado, index) {

    Logger.log('');
    Logger.log('RESULTADO ' + (index + 1));

    Logger.log(
      'Título: ' +
      resultado.title
    );

    Logger.log(
      'Publicado: ' +
      (resultado.published_date || 'Sin fecha')
    );

    Logger.log(
      'Score: ' +
      resultado.score
    );

    Logger.log(
      'URL: ' +
      resultado.url
    );

    Logger.log(
      'Contenido: ' +
      resultado.content
    );

  });

  Logger.log('');
  Logger.log('==============================');

  Logger.log(
    'Total encontrados: ' +
    data.results.length
  );
}

function probarResumenIA() {

  const props = PropertiesService.getScriptProperties();

  const tavilyKey = props.getProperty('TAVILY_API_KEY');
  const geminiKey = props.getProperty('GEMINI_API_KEY');

  // GOAL API es la fuente principal para la forma reciente. Tavily y
  // Gemini enriquecen el resumen, pero su ausencia no debe ocultar los
  // resultados deportivos que sí están disponibles.


  // ============================================================
  // 1. DATOS DEL PARTIDO DE PRUEBA
  // ============================================================

  const partido = {
    fixtureId: 'PRUEBA_001',
    local: 'Liverpool FC (Uruguay)',
    visitante: 'Racing Club de Montevideo',
    pais: 'Uruguay',
    competicion: 'Liga AUF Uruguaya - Torneo Clausura 2026 - Fecha 7',
    fecha: '20 septiembre 2026',
    hora: '20:00'
  };


  Logger.log('======================================');
  Logger.log('PARTIDO');
  Logger.log('======================================');

  Logger.log(
    partido.local +
    ' vs ' +
    partido.visitante
  );

  Logger.log(
    partido.fecha +
    ' - ' +
    partido.hora +
    ' Uruguay'
  );

  Logger.log(partido.competicion);


  // ============================================================
  // 2. ARMAR LAS 3 BÚSQUEDAS
  // ============================================================

  const consultas = [

    {
      tipo: 'PARTIDO',

      query:
        `"Liverpool FC Uruguay" "Racing Montevideo" ` +
        `"20 septiembre 2026" Uruguay fútbol masculino`
    },

    {
      tipo: 'LIVERPOOL',

      query:
        `"Liverpool FC Uruguay" fútbol ` +
        `últimas noticias septiembre 2026`
    },

    {
      tipo: 'RACING',

      query:
        `"Racing Club Montevideo" Uruguay fútbol ` +
        `últimas noticias septiembre 2026`
    }

  ];


  // ============================================================
  // 3. EJECUTAR BÚSQUEDAS
  // ============================================================

  let todosLosResultados = [];


  consultas.forEach(function(consulta) {

    Logger.log('');
    Logger.log('======================================');
    Logger.log('BÚSQUEDA: ' + consulta.tipo);
    Logger.log('======================================');

    Logger.log(consulta.query);


    const encontrados =
      buscarTavilyPartido(
        tavilyKey,
        consulta.query,
        consulta.tipo
      );


    Logger.log(
      'Resultados obtenidos: ' +
      encontrados.length
    );


    encontrados.forEach(function(resultado) {

      todosLosResultados.push(resultado);

    });

  });


  // ============================================================
  // 4. ELIMINAR DUPLICADOS
  // ============================================================

  const resultados =
    eliminarDuplicadosTavily(
      todosLosResultados
    );


  Logger.log('');
  Logger.log('======================================');
  Logger.log('RESUMEN DE BÚSQUEDA');
  Logger.log('======================================');

  Logger.log(
    'Resultados antes de deduplicar: ' +
    todosLosResultados.length
  );

  Logger.log(
    'Resultados únicos: ' +
    resultados.length
  );


  // ============================================================
  // 5. SIN RESULTADOS
  // ============================================================

  if (resultados.length === 0) {

    const sinResultados = {

      ok: true,

      fixtureId: partido.fixtureId,

      encontrado: false,

      contexto:
        'No encontramos información reciente sobre este partido.',

      local: {
        nombre: partido.local,
        comoLlega: '',
        calidadInformacion: 'sin_informacion'
      },

      visitante: {
        nombre: partido.visitante,
        comoLlega: '',
        calidadInformacion: 'sin_informacion'
      },

      novedades: [],

      bajas: [],

      fuentes: [],

      actualizado:
        new Date().toISOString()

    };


    Logger.log(
      JSON.stringify(
        sinResultados,
        null,
        2
      )
    );


    return sinResultados;

  }


  // ============================================================
  // 6. PREPARAR FUENTES PARA GEMINI
  // ============================================================

  let fuentesTexto = '';


  resultados.forEach(function(resultado, index) {

    fuentesTexto +=
      '\n\n====================================\n';

    fuentesTexto +=
      'FUENTE ' +
      (index + 1) +
      '\n';

    fuentesTexto +=
      'TIPO: ' +
      resultado.tipoBusqueda +
      '\n';

    fuentesTexto +=
      'TÍTULO: ' +
      resultado.title +
      '\n';

    fuentesTexto +=
      'FECHA: ' +
      (
        resultado.published_date ||
        'Sin fecha'
      ) +
      '\n';

    fuentesTexto +=
      'URL: ' +
      resultado.url +
      '\n';

    fuentesTexto +=
      'CONTENIDO:\n' +
      resultado.content +
      '\n';

  });


  // ============================================================
  // 7. PROMPT PARA GEMINI
  // ============================================================

  const prompt = `
Sos un asistente especializado en fútbol.

Tu tarea es analizar fuentes encontradas en Internet
y generar información estructurada para una aplicación
de seguimiento de partidos.


==================================================
PARTIDO
==================================================

Local:
${partido.local}

Visitante:
${partido.visitante}

País:
${partido.pais}

Competición:
${partido.competicion}

Fecha:
${partido.fecha}

Hora:
${partido.hora} (Uruguay)


==================================================
IDENTIDAD EXACTA DE LOS EQUIPOS
==================================================

Liverpool significa EXCLUSIVAMENTE:

Liverpool Fútbol Club
Montevideo
Uruguay


Racing significa EXCLUSIVAMENTE:

Racing Club de Montevideo
Uruguay


==================================================
REGLAS OBLIGATORIAS
==================================================

1.

Utilizá EXCLUSIVAMENTE la información contenida
en las fuentes proporcionadas.

No utilices conocimiento interno para completar
información faltante.


2.

Descartá cualquier fuente que corresponda a
otro equipo con un nombre parecido.

Esto incluye, entre otros:

- Liverpool FC de Inglaterra
- Liverpool femenino
- Liverpool juvenil
- Racing Club de Avellaneda
- Racing Santander
- otros clubes llamados Racing


3.

Este partido corresponde al fútbol masculino
de Primera División de Uruguay.


4.

No inventes:

- resultados
- lesiones
- suspensiones
- jugadores
- alineaciones
- entrenadores
- posiciones
- puntos
- estadísticas
- rachas


5.

MUY IMPORTANTE:

No interpretes una estadística de la tabla del torneo
como si fuera una racha reciente.

Ejemplo:

Si una fuente dice:

6 partidos jugados
4 ganados
1 empatado
1 perdido

podés decir:

"Registra 4 victorias, 1 empate y 1 derrota
en 6 partidos del torneo."

NO podés decir:

"Llega con una racha de 4 victorias,
1 empate y 1 derrota."

Solamente hablá de "racha", "últimos partidos"
o "forma reciente" cuando la fuente indique
explícitamente que se trata de partidos recientes.


6.

Podés utilizar noticias individuales de cada equipo
aunque no mencionen específicamente este partido,
siempre que quede claro que corresponden al club
uruguayo correcto.


7.

No conviertas rumores en hechos.


8.

Si dos fuentes se contradicen,
no elijas arbitrariamente una.

Explicá brevemente que la información
no es concluyente.


9.

No es obligatorio utilizar todas las fuentes.

Ignorá las irrelevantes.


10.

La calidad de información debe tener
EXACTAMENTE uno de estos valores:

"buena"

"limitada"

"sin_informacion"


Usá:

"buena"

cuando existan varias fuentes recientes y relevantes
que permitan describir razonablemente al equipo.


Usá:

"limitada"

cuando exista alguna información útil,
pero sea escasa.


Usá:

"sin_informacion"

cuando no exista información útil y confiable.


11.

Las bajas solamente pueden incluirse si la ausencia
está explícitamente confirmada en alguna fuente.


==================================================
RESPUESTA
==================================================

Respondé ÚNICAMENTE con JSON válido.

No uses Markdown.

No escribas explicaciones antes ni después del JSON.

No uses bloques de código.


La estructura debe ser EXACTAMENTE esta:


{
  "contexto": "texto breve",
  "local": {
    "nombre": "${partido.local}",
    "comoLlega": "texto breve",
    "calidadInformacion": "buena"
  },
  "visitante": {
    "nombre": "${partido.visitante}",
    "comoLlega": "texto breve",
    "calidadInformacion": "limitada"
  },
  "novedades": [
    "novedad 1",
    "novedad 2"
  ],
  "bajas": [
    {
      "equipo": "nombre equipo",
      "jugador": "nombre jugador",
      "motivo": "motivo confirmado"
    }
  ],
  "fuentesUtilizadas": [
    1,
    4,
    7
  ]
}


REGLAS DEL JSON:

"contexto":
máximo 3 frases.

"comoLlega":
máximo 3 frases por equipo.

"novedades":
máximo 4 elementos.

Si no existen novedades:
[]

Si no existen bajas confirmadas:
[]

"fuentesUtilizadas":
incluí únicamente los números de las fuentes
que realmente utilizaste.


==================================================
FUENTES DISPONIBLES
==================================================

${fuentesTexto}
`;


  // ============================================================
  // 8. CONSULTAR GEMINI
  // ============================================================

  Logger.log('');
  Logger.log('======================================');
  Logger.log('ENVIANDO A GEMINI');
  Logger.log('======================================');


  const geminiPayload = {

    model: 'gemini-3.6-flash',

    input: prompt

  };


  const geminiResponse =
    UrlFetchApp.fetch(
      'https://generativelanguage.googleapis.com/v1beta/interactions',
      {

        method: 'post',

        contentType: 'application/json',

        headers: {
          'x-goog-api-key': geminiKey
        },

        payload:
          JSON.stringify(geminiPayload),

        muteHttpExceptions: true

      }
    );


  const geminiStatus =
    geminiResponse.getResponseCode();


  Logger.log(
    'Gemini HTTP: ' +
    geminiStatus
  );


  if (geminiStatus !== 200) {

    const errorGemini =
      geminiResponse.getContentText();

    Logger.log(errorGemini);

    throw new Error(
      'Error Gemini HTTP ' +
      geminiStatus +
      ': ' +
      errorGemini
    );

  }


  const geminiData =
    JSON.parse(
      geminiResponse.getContentText()
    );


  // ============================================================
  // 9. EXTRAER RESPUESTA DE GEMINI
  // ============================================================

  let textoGemini = '';
  let fuentesGrounding = [];


  if (geminiData.steps) {

    geminiData.steps.forEach(function(step) {

      if (
        step.type === 'model_output' &&
        step.content
      ) {

        step.content.forEach(function(item) {

          if (
            item.type === 'text' &&
            item.text
          ) {

              textoGemini +=
                item.text;

              if (Array.isArray(item.annotations)) {
                item.annotations.forEach(function(anotacion) {
                  if (anotacion && anotacion.type === 'url_citation' && anotacion.url) {
                    fuentesGrounding.push({
                      titulo: anotacion.title || anotacion.url,
                      url: anotacion.url,
                      fecha: '',
                      tipo: 'PARTIDO'
                    });
                  }
                });
              }

          }

        });

      }

    });

  }


  textoGemini =
    textoGemini.trim();


  // ============================================================
  // 10. LIMPIAR POSIBLES BLOQUES MARKDOWN
  // ============================================================

  if (
    textoGemini.startsWith('```')
  ) {

    textoGemini =
      textoGemini.replace(
        /^```json\s*/i,
        ''
      );

    textoGemini =
      textoGemini.replace(
        /^```\s*/i,
        ''
      );

    textoGemini =
      textoGemini.replace(
        /\s*```$/,
        ''
      );

  }


  // ============================================================
  // 11. CONVERTIR RESPUESTA EN JSON REAL
  // ============================================================

  let resumenIA;


  try {

    resumenIA =
      JSON.parse(textoGemini);

  }
  catch (error) {

    Logger.log('');
    Logger.log('======================================');
    Logger.log('ERROR AL INTERPRETAR JSON');
    Logger.log('======================================');

    Logger.log(textoGemini);

    throw new Error(
      'Gemini respondió, pero el JSON no es válido: ' +
      error.message
    );

  }


  // ============================================================
  // 12. CONSTRUIR LISTA REAL DE FUENTES
  // ============================================================

  const fuentesFinales = [];


  const numerosFuentes =
    Array.isArray(
      resumenIA.fuentesUtilizadas
    )
      ? resumenIA.fuentesUtilizadas
      : [];


  numerosFuentes.forEach(function(numeroFuente) {

    const indice =
      Number(numeroFuente) - 1;


    if (
      indice >= 0 &&
      indice < resultados.length
    ) {

      const fuente =
        resultados[indice];


      fuentesFinales.push({

        titulo:
          fuente.title,

        url:
          fuente.url,

        fecha:
          fuente.published_date || '',

        tipo:
          fuente.tipoBusqueda

      });

    }

  });


  // ============================================================
  // 13. CONSTRUIR RESPUESTA FINAL
  // ============================================================

  const respuestaFinal = {

    ok: true,

    encontrado: true,

    fixtureId:
      partido.fixtureId,

    partido: {

      local:
        partido.local,

      visitante:
        partido.visitante,

      competicion:
        partido.competicion,

      fecha:
        partido.fecha,

      hora:
        partido.hora

    },

    contexto:
      resumenIA.contexto || '',

    local:
      resumenIA.local || {

        nombre:
          partido.local,

        comoLlega: '',

        calidadInformacion:
          'sin_informacion'

      },

    visitante:
      resumenIA.visitante || {

        nombre:
          partido.visitante,

        comoLlega: '',

        calidadInformacion:
          'sin_informacion'

      },

    novedades:
      Array.isArray(
        resumenIA.novedades
      )
        ? resumenIA.novedades
        : [],

    bajas:
      Array.isArray(
        resumenIA.bajas
      )
        ? resumenIA.bajas
        : [],

    transmision:
      validarTransmisionUruguay_(resumenIA.transmision),

    fuentes:
      fuentesFinales,

    actualizado:
      new Date().toISOString()

  };


  // ============================================================
  // 14. MOSTRAR RESULTADO
  // ============================================================

  Logger.log('');
  Logger.log('======================================');
  Logger.log('RESPUESTA FINAL JSON');
  Logger.log('======================================');


  Logger.log(
    JSON.stringify(
      respuestaFinal,
      null,
      2
    )
  );


  Logger.log('');
  Logger.log('======================================');
  Logger.log('FIN');
  Logger.log('======================================');


  return respuestaFinal;

}

function buscarTavilyPartido(
  apiKey,
  query,
  tipoBusqueda
) {

  const url =
    'https://api.tavily.com/search';


  const payload = {

    query: query,

    topic: 'news',

    search_depth: 'basic',

    max_results: 7,

    include_answer: false,

    include_raw_content: false,

    days: 14

  };


  reservarCreditosTavily_(1, false);
  registrarActividadTavily_('legacy_buscarTavilyPartido', tipoBusqueda || 'GENERAL', '', 'TAVILY', 'llamada real');

  const response =
    UrlFetchApp.fetch(
      url,
      {

        method: 'post',

        contentType: 'application/json',

        headers: {
          'Authorization':
            'Bearer ' + apiKey
        },

        payload:
          JSON.stringify(payload),

        muteHttpExceptions: true

      }
    );


  const status =
    response.getResponseCode();


  if (status !== 200) {

    Logger.log(
      'Error Tavily en búsqueda ' +
      tipoBusqueda
    );

    Logger.log(
      response.getContentText()
    );

    return [];

  }


  const data =
    JSON.parse(
      response.getContentText()
    );


  const resultados =
    data.results || [];


  // Agregamos a cada resultado
  // de qué búsqueda provino.

  return resultados.map(
    function(resultado) {

      return {

        title:
          resultado.title || '',

        url:
          resultado.url || '',

        content:
          resultado.content || '',

        score:
          resultado.score || 0,

        published_date:
          resultado.published_date || '',

        tipoBusqueda:
          tipoBusqueda

      };

    }
  );

}

/**
 * Diagnóstico manual y seguro de Gemini.
 * Ejecutar desde el editor de Apps Script; no requiere desplegar el Web App.
 * No registra ni devuelve la API key.
 */
function diagnosticarGeminiAhora() {
  const apiKey = PropertiesService.getScriptProperties().getProperty('GEMINI_API_KEY');
  if (!apiKey) {
    Logger.log('DIAGNOSTICO_GEMINI: SIN_API_KEY');
    return 'SIN_API_KEY';
  }

  const pruebas = [
    {
      nombre: 'INTERACTIONS_3_8',
      url: 'https://generativelanguage.googleapis.com/v1beta/interactions',
      payload: {
        model: 'gemini-3.8-flash',
        input: 'Respondé solamente: OK'
      }
    },
    {
      nombre: 'INTERACTIONS_3_1_LITE',
      url: 'https://generativelanguage.googleapis.com/v1beta/interactions',
      payload: {
        model: 'gemini-3.1-flash-lite',
        input: 'Respondé solamente: OK'
      }
    }
  ];

  const resumen = [];
  pruebas.forEach(function(prueba) {
    try {
      const respuesta = UrlFetchApp.fetch(prueba.url, {
        method: 'post',
        contentType: 'application/json',
        headers: { 'x-goog-api-key': apiKey },
        payload: JSON.stringify(prueba.payload),
        muteHttpExceptions: true
      });
      const estado = respuesta.getResponseCode();
      let detalle = respuesta.getContentText();
      try {
        const json = JSON.parse(detalle);
        if (estado === 200) {
          detalle = 'OK';
        }
        else {
          detalle = String(
            json.error && (json.error.status || json.error.message) || 'ERROR_SIN_DETALLE'
          );
        }
      }
      catch (ignorado) {
        detalle = detalle.substring(0, 300);
      }
      const linea = prueba.nombre + ': HTTP ' + estado + ' - ' + detalle;
      Logger.log(linea);
      resumen.push(linea);
    }
    catch (error) {
      const lineaError = prueba.nombre + ': ERROR_CONEXION - ' + error.message;
      Logger.log(lineaError);
      resumen.push(lineaError);
    }
  });

  Logger.log('DIAGNOSTICO_GEMINI_COMPLETO: ' + resumen.join(' | '));
  return resumen.join('\n');
}

/**
 * Guarda un único historial compacto de actividad Tavily en Script Properties.
 * No crea una propiedad por llamada: mantiene como máximo los últimos 80 eventos.
 */
function registrarActividadTavily_(origen, tipo, fixtureId, destino, detalle) {
  try {
    const props = PropertiesService.getScriptProperties();
    const key = 'TAVILY_DIAGNOSTIC_V1';
    let historial = [];
    try {
      historial = JSON.parse(props.getProperty(key) || '[]');
      if (!Array.isArray(historial)) historial = [];
    } catch (ignorado) {
      historial = [];
    }

    historial.push({
      fecha: Utilities.formatDate(new Date(), 'America/Montevideo', 'yyyy-MM-dd HH:mm:ss'),
      origen: String(origen || ''),
      tipo: String(tipo || ''),
      fixtureId: String(fixtureId || ''),
      destino: String(destino || ''),
      detalle: String(detalle || '').substring(0, 180)
    });

    if (historial.length > 80) historial = historial.slice(historial.length - 80);
    props.setProperty(key, JSON.stringify(historial));
  } catch (error) {
    Logger.log('No se pudo registrar diagnóstico Tavily: ' + error.message);
  }
}

function obtenerDiagnosticoTavily_(reset) {
  const props = PropertiesService.getScriptProperties();
  const key = 'TAVILY_DIAGNOSTIC_V1';
  if (reset) {
    props.deleteProperty(key);
    return { ok: true, action: 'tavilyDiagnostic', reset: true, eventos: [], resumen: {} };
  }

  let eventos = [];
  try {
    eventos = JSON.parse(props.getProperty(key) || '[]');
    if (!Array.isArray(eventos)) eventos = [];
  } catch (ignorado) {
    eventos = [];
  }

  const resumen = {};
  eventos.forEach(function(e) {
    const clave = (e.destino || 'DESCONOCIDO') + ' | ' + (e.origen || '') + ' | ' + (e.tipo || '');
    resumen[clave] = (resumen[clave] || 0) + 1;
  });

  return {
    ok: true,
    action: 'tavilyDiagnostic',
    totalEventos: eventos.length,
    resumen: resumen,
    eventos: eventos.slice().reverse()
  };
}

/**
 * Ejecuta las búsquedas independientes en paralelo. Una respuesta fallida no
 * invalida las demás y evita sumar tres tiempos de espera consecutivos.
 */
function buscarTavilyPartidosEnParalelo_(apiKey, consultas, pais, fixtureId, permitirExceso, forzarTransmision) {
  const url = 'https://api.tavily.com/search';
  const cache = CacheService.getScriptCache();
  const ttlSegundos = 2 * 60 * 60;
  const idPartido = String(fixtureId || 'sin_fixture').replace(/[^a-zA-Z0-9_-]/g, '_').substring(0, 80);
  const resultadosPorTipo = {};
  const resultadosActualizados = [];
  const pendientes = [];
  const busquedas = { cacheadas: [], actualizadas: [], fallidas: [], resultadosNuevos: 0 };

  // Cada búsqueda se cachea por separado. Así evitamos una entrada enorme y,
  // si sólo falta una fuente, no volvemos a pagar las demás búsquedas.
  consultas.forEach(function(consulta) {
    const tipo = String(consulta.tipo || 'GENERAL').toUpperCase();
    // Nueva búsqueda: se invalida únicamente la caché de transmisión.
    const cacheKey = (tipo === 'TRANSMISION' ? 'tavily_q_transmision_uy_v4_' : 'tavily_q_v2_') + idPartido + '_' + tipo;
    let recuperados = null;

    try {
      const raw = tipo === 'TRANSMISION' && forzarTransmision ? null : cache.get(cacheKey);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) recuperados = parsed;
      }
    }
    catch (errorCache) {
      Logger.log('TAVILY_CACHE_ERROR ' + tipo + ': ' + errorCache.message);
    }

    if (recuperados !== null) {
      resultadosPorTipo[tipo] = recuperados;
      busquedas.cacheadas.push(tipo);
      Logger.log('TAVILY ' + tipo + ' -> CACHE (' + recuperados.length + ' resultados)');
      registrarActividadTavily_('matchSummary', tipo, fixtureId, 'CACHE', recuperados.length + ' resultados');
    }
    else {
      pendientes.push({ consulta: consulta, tipo: tipo, cacheKey: cacheKey });
      busquedas.actualizadas.push(tipo);
      Logger.log('TAVILY ' + tipo + ' -> TAVILY');
      // Se registra como llamada real solamente después de reservar el presupuesto.
    }
  });

  if (pendientes.length > 0) {
    reservarCreditosTavily_(pendientes.reduce(function(total, item) {
      return total + (item.consulta.searchDepth === 'advanced' ? 2 : 1);
    }, 0), permitirExceso);
    pendientes.forEach(function(item) {
      registrarActividadTavily_('matchSummary', item.tipo, fixtureId, 'TAVILY', 'llamada real');
    });
    const solicitudes = pendientes.map(function(item) {
      const consulta = item.consulta;
      return {
        url: url,
        method: 'post',
        contentType: 'application/json',
        headers: { 'Authorization': 'Bearer ' + apiKey },
        payload: JSON.stringify({
          query: consulta.query,
          topic: consulta.topic || 'news',
          search_depth: consulta.searchDepth || 'basic',
          max_results: consulta.maxResults || 8,
          include_answer: false,
          include_raw_content: false,
          ...(consulta.topic === 'general' ? {} : { days: 45 })
        }),
        muteHttpExceptions: true
      };
    });

    const respuestas = UrlFetchApp.fetchAll(solicitudes);
    respuestas.forEach(function(respuesta, indice) {
      const item = pendientes[indice];
      const tipo = item.tipo;
      const status = respuesta.getResponseCode();
      if (status !== 200) {
        Logger.log('Tavily ' + tipo + ' respondió HTTP ' + status);
        busquedas.actualizadas = busquedas.actualizadas.filter(function(value) { return value !== tipo; });
        busquedas.fallidas.push(tipo + ' (HTTP ' + status + ')');
        resultadosPorTipo[tipo] = [];
        return;
      }

      try {
        const data = JSON.parse(respuesta.getContentText());
        busquedas.actualizadas = busquedas.actualizadas.filter(function(value) { return value !== tipo; });
        busquedas.actualizadas.push(tipo);
        const encontrados = Array.isArray(data.results) ? data.results : [];
        busquedas.resultadosNuevos += encontrados.length;
        const compactos = encontrados.slice(0, 10).map(function(resultado) {
          return {
            title: String(resultado.title || '').substring(0, 500),
            url: String(resultado.url || '').substring(0, 1000),
            content: String(resultado.content || '').substring(0, 3000),
            score: resultado.score || 0,
            published_date: resultado.published_date || '',
            tipoBusqueda: tipo
          };
        });
        resultadosPorTipo[tipo] = compactos;
        resultadosActualizados.push.apply(resultadosActualizados, compactos);

        {
          try {
            const serializado = JSON.stringify(compactos);
            // Evita repetir búsquedas sin resultados durante 10 minutos.
            // Los resultados útiles conservan la caché habitual de 2 horas.
            cache.put(item.cacheKey, serializado, compactos.length > 0 ? ttlSegundos : 10 * 60);
            Logger.log('TAVILY_CACHE_SAVE ' + tipo + ' (' + serializado.length + ' caracteres)');
          }
          catch (errorGuardar) {
            Logger.log('TAVILY_CACHE_SAVE_ERROR ' + tipo + ': ' + errorGuardar.message);
          }
        }
      }
      catch (error) {
        Logger.log('Respuesta inválida de Tavily ' + tipo + ': ' + error.message);
        busquedas.actualizadas = busquedas.actualizadas.filter(function(value) { return value !== tipo; });
        busquedas.fallidas.push(tipo + ' (respuesta inválida)');
        resultadosPorTipo[tipo] = [];
      }
    });
  }

  let todos = [];
  consultas.forEach(function(consulta) {
    const tipo = String(consulta.tipo || 'GENERAL').toUpperCase();
    todos = todos.concat(resultadosPorTipo[tipo] || []);
  });
  return { resultados: todos, resultadosActualizados: resultadosActualizados, busquedas: busquedas };
}
function motivoActualizacionTavily_(busquedas) {
  busquedas = busquedas || { cacheadas: [], actualizadas: [], fallidas: [] };
  const partes = [];
  if (busquedas.actualizadas && busquedas.actualizadas.length) partes.push('consultadas ahora: ' + busquedas.actualizadas.join(', '));
  if (busquedas.cacheadas && busquedas.cacheadas.length) partes.push('reutilizadas desde caché: ' + busquedas.cacheadas.join(', '));
  if (busquedas.fallidas && busquedas.fallidas.length) partes.push('fallidas: ' + busquedas.fallidas.join(', '));
  return partes.length ? partes.join('; ') + '.' : 'No se realizaron búsquedas Tavily.';
}

function paisTavily_(pais) {
  const mapa = {
    'uruguay': 'uruguay', 'argentina': 'argentina', 'espana': 'spain',
    'spain': 'spain', 'inglaterra': 'united kingdom', 'reino unido': 'united kingdom',
    'francia': 'france', 'italia': 'italy', 'alemania': 'germany',
    'brasil': 'brazil', 'chile': 'chile', 'colombia': 'colombia',
    'mexico': 'mexico', 'portugal': 'portugal', 'paraguay': 'paraguay',
    'bolivia': 'bolivia', 'ecuador': 'ecuador', 'peru': 'peru',
    'venezuela': 'venezuela', 'estados unidos': 'united states', 'usa': 'united states'
  };
  return mapa[normalizar_(pais)] || '';
}

function buscarTavilyGeneralRespaldo_(apiKey, partido, permitirExceso) {
  const query = 'fútbol masculino ' + partido.local + ' ' + partido.visitante + ' ' +
    (partido.pais || '') + ' ' + (partido.competicion || '') +
    ' últimos partidos resultados previa lesiones suspendidos';
  reservarCreditosTavily_(1, permitirExceso);
  registrarActividadTavily_('respaldo', 'GENERAL', partido.fixtureId || '', 'TAVILY', 'llamada real de respaldo');
  const response = UrlFetchApp.fetch('https://api.tavily.com/search', {
    method: 'post',
    contentType: 'application/json',
    headers: { 'Authorization': 'Bearer ' + apiKey },
    payload: JSON.stringify({
      query: query,
      topic: 'general',
      search_depth: 'basic',
      max_results: 10,
      include_answer: false,
      include_raw_content: false
    }),
    muteHttpExceptions: true
  });
  if (response.getResponseCode() !== 200) {
    Logger.log('Tavily respaldo respondió HTTP ' + response.getResponseCode());
    return [];
  }

  try {
    const data = JSON.parse(response.getContentText());
    return (data.results || []).map(function(resultado) {
      return {
        title: resultado.title || '', url: resultado.url || '',
        content: resultado.content || '', score: resultado.score || 0,
        published_date: resultado.published_date || '', tipoBusqueda: 'PARTIDO'
      };
    });
  }
  catch (error) {
    Logger.log('Respuesta inválida de Tavily respaldo: ' + error.message);
    return [];
  }
}

function eliminarDuplicadosTavily(
  resultados
) {

  const urlsVistas = {};

  const unicos = [];


  resultados.forEach(function(resultado) {

    if (!resultado.url) {
      return;
    }


    // Normalizamos un poco la URL
    // para detectar duplicados.

    let urlNormalizada =
      resultado.url
        .toLowerCase()
        .trim();


    // Quitamos "/" final.

    if (
      urlNormalizada.endsWith('/')
    ) {

      urlNormalizada =
        urlNormalizada.slice(
          0,
          -1
        );

    }


    // Si todavía no apareció,
    // la conservamos.

    if (
      !urlsVistas[urlNormalizada]
    ) {

      urlsVistas[urlNormalizada] =
        true;

      unicos.push(resultado);

    }

  });


  return unicos;

}

function evaluarFuenteTavilyPartido_(resultado, partido) {
  const texto = ' ' + normalizarTextoComparacion_((resultado.title || '') + ' ' + (resultado.content || '')) + ' ';
  const prohibidas = [' femenino', ' women', ' femeni', ' sub 17', ' sub 19', ' sub 20', ' u17', ' u19', ' u20', ' juvenil'];
  if (prohibidas.some(function(palabra) { return texto.indexOf(palabra) !== -1; })) {
    return {aceptada:false, motivo:'Descartada: categoría juvenil o femenina'};
  }
  function variantes(nombre) {
    const base = obtenerVariantesNombreEquipo_(normalizarTextoComparacion_(nombre));
    const extras = [];
    base.forEach(function(n) {
      const corto = n.replace(/^(sv|fc|cf|ac|sc|cd|rc|club)\s+/, '').trim();
      if (corto.length >= 5 && corto !== n) extras.push(corto);
    });
    return base.concat(extras);
  }
  const local = variantes(partido.local);
  const visitante = variantes(partido.visitante);
  // Una programación regional de la competición sirve como contexto para
  // Gemini, pero NO confirma por sí sola la transmisión de este encuentro.
  const tituloFuente = normalizarTextoComparacion_(resultado.title || '');
  const urlFuente = String(resultado.url || '').toLowerCase();
  const programacionRegional = resultado.tipoBusqueda === 'TRANSMISION' &&
    (/espn\.com|espndeportes\.espn\.com/.test(urlFuente)) &&
    (/champions|programacion|como ver|donde ver/.test(tituloFuente)) &&
    (/uruguay|\buru\b/.test(texto)) &&
    (/disney|espn/.test(texto));
  function coincide(nombres) {
    return nombres.some(function(n) {
      if (n.length < 4) return false;
      return texto.indexOf(' ' + n + ' ') !== -1;
    });
  }
  const localOk = coincide(local);
  const visitanteOk = coincide(visitante);
  if (resultado.tipoBusqueda === 'TRANSMISION') {
    if (!localOk || !visitanteOk) {
      if (programacionRegional) return {aceptada:true, motivo:'Programación regional de contexto; NO confirma este partido'};
      return {aceptada:false, motivo:'Descartada: falta coincidencia de ' + (!localOk && !visitanteOk ? 'ambos equipos' : !localOk ? 'equipo local' : 'equipo visitante')};
    }
    return {aceptada:true, motivo:'Ambos equipos identificados; disponibilidad para Uruguay pendiente de validación'};
  }
  const ok = resultado.tipoBusqueda === 'LOCAL' ? localOk : resultado.tipoBusqueda === 'VISITANTE' ? visitanteOk : (localOk || visitanteOk);
  return {aceptada:ok, motivo:ok ? 'Coincidencia de equipo' : 'Descartada: no coincide ningún equipo relevante'};
}

function filtrarResultadosTavilyPartido_(resultados, partido) {
  // Reservamos espacio a las fuentes de transmisión: el límite global anterior
  // podía descartarlas aunque hubieran superado correctamente la validación.
  const aceptados = resultados.filter(function(r) { return evaluarFuenteTavilyPartido_(r, partido).aceptada; });
  // Priorizar fuentes que realmente tratan sobre dónde ver el partido.
  // Las páginas de marcadores, redes sociales y guías de otros países no
  // deben desplazar una nota periodística específica sobre la transmisión.
  function prioridadTransmision(r) {
    const titulo = normalizarTextoComparacion_(r.title || '');
    const contenido = normalizarTextoComparacion_(r.content || '');
    const url = String(r.url || '').toLowerCase();
    const contexto = evaluarFuenteTavilyPartido_(r, partido).motivo.indexOf('Programación regional') === 0;
    let puntos = 0;
    if (/uruguay|\buru\b/.test(contenido + ' ' + titulo)) puntos += 5;
    if (/donde ver|como ver|where to watch|tv channel|transmision|ver en vivo|live stream/.test(titulo)) puntos += 5;
    if (/disney|espn|directv|dsports|dgo/.test(contenido + ' ' + titulo)) puntos += 3;
    if (/espndeportes\.espn\.com|espn\.com\.uy/.test(url)) puntos += 3;
    if (contexto) puntos -= 4; // Sirve de contexto, no confirma el encuentro.
    if (/facebook\.com|instagram\.com|tiktok\.com|fandom\.com/.test(url)) puntos -= 12;
    if (/live score|resultados en vivo|odds|estadisticas|fixtures|schedule/.test(titulo)) puntos -= 8;
    if (/en-us|united states|paramount|\busa\b/.test(url + ' ' + contenido)) puntos -= 4;
    return puntos;
  }
  const transmision = aceptados.filter(function(r) { return r.tipoBusqueda === 'TRANSMISION'; })
    .sort(function(a, b) { return prioridadTransmision(b) - prioridadTransmision(a); })
    .slice(0, 6);
  const otras = aceptados.filter(function(r) { return r.tipoBusqueda !== 'TRANSMISION'; }).slice(0, 15 - transmision.length);
  return otras.concat(transmision);
}

function obtenerResumenPartido(datosPartido, forzarActualizacion, permitirExceso) {

  const props = PropertiesService.getScriptProperties();

  const tavilyKey = props.getProperty('TAVILY_API_KEY');
  const geminiKey = props.getProperty('GEMINI_API_KEY');

  // ============================================================
  // 1. VALIDAR DATOS
  // ============================================================

  if (!datosPartido) {
    throw new Error('No se recibieron datos del partido');
  }

  if (!datosPartido.fixtureId) {
    throw new Error('Falta fixtureId');
  }

  if (!datosPartido.local) {
    throw new Error('Falta equipo local');
  }

  if (!datosPartido.visitante) {
    throw new Error('Falta equipo visitante');
  }

  if (!datosPartido.fecha) {
    throw new Error('Falta fecha');
  }


  const partido = {

    fixtureId:
      String(datosPartido.fixtureId),

    local:
      String(datosPartido.local),

    visitante:
      String(datosPartido.visitante),

    pais:
      String(datosPartido.pais || ''),

    competicion:
      String(datosPartido.competicion || ''),

    fecha:
      String(datosPartido.fecha),

    hora:
      String(datosPartido.hora || ''),

    localId:
      String(datosPartido.localId || ''),

    visitanteId:
      String(datosPartido.visitanteId || '')

  };


  // ============================================================
  // 2. CACHE
  // ============================================================

  const cache =
    CacheService.getScriptCache();


  const cacheKey =
    'matchSummary_tavily_lite_v13_' +
    partido.fixtureId;

  // El respaldo persistente sobrevive a CacheService y a cambios de versión.
  // V12 se consulta sólo para migrar una transmisión ya confirmada.
  const respaldoAnterior = obtenerResumenRespaldo_(cacheKey) ||
    obtenerResumenRespaldo_('matchSummary_tavily_lite_v12_' + partido.fixtureId);


  // Si NO se pidió actualizar manualmente,
  // primero buscamos el resumen en caché.

  if (!forzarActualizacion) {

    const cacheGuardado =
      cache.get(cacheKey);


    if (cacheGuardado) {

      try {

        const respuestaCache =
          JSON.parse(cacheGuardado);


        respuestaCache.desdeCache = true;
        respuestaCache.estadoActualizacion = 'cache';
        respuestaCache.motivoCache = 'No se solicitó una actualización manual; se reutilizó el resumen guardado en el servidor.';


        return respuestaCache;

      }
      catch (error) {

        Logger.log(
          'El cache existente no pudo interpretarse: ' +
          error.message
        );

      }

    }

  }

  // ============================================================
  // 3. FORMA RECIENTE DESDE GOAL API
  // ============================================================

  // Los marcadores confirmados salen del proveedor deportivo, no de una
  // búsqueda web. Si alguna ruta histórica no está disponible, el flujo
  // continúa con Tavily sin convertir ese fallo secundario en un error.
  let formaGoal = { hayDatos: false, local: [], visitante: [] };
  try {
    formaGoal = obtenerFormaRecienteGoal_(partido);
  }
  catch (errorForma) {
    Logger.log('Forma reciente GOAL no disponible: ' + errorForma.message);
  }


  // ============================================================
  // 3. PREPARAR CONSULTAS TAVILY
  // ============================================================

  const contextoPais =
    partido.pais
      ? ' ' + partido.pais
      : '';


  const contextoCompeticion =
    partido.competicion
      ? ' "' + partido.competicion + '"'
      : '';

  const fechaConsulta = Utilities.formatDate(
    new Date(),
    'America/Montevideo',
    'dd/MM/yyyy'
  );


  const consultaPartido =
    `"${partido.local}" "${partido.visitante}" ` +
    `"${partido.fecha}"` +
    contextoPais +
    contextoCompeticion +
    ` fútbol masculino previa novedades actualidad al ${fechaConsulta}`;


  const consultaLocal =
    `"${partido.local}"` +
    contextoPais +
    ` fútbol masculino temporada 2026 últimos 5 partidos marcadores posición tabla actual tabla anual Clausura Apertura copa internacional Sudamericana Libertadores fase vigente semifinales actualidad al ${fechaConsulta} cómo llega lesiones suspendidos bajas ` +
    partido.fecha;


  const consultaVisitante =
    `"${partido.visitante}"` +
    contextoPais +
    ` fútbol masculino temporada 2026 últimos 5 partidos marcadores posición tabla actual tabla anual Clausura Apertura copa internacional Sudamericana Libertadores fase vigente semifinales actualidad al ${fechaConsulta} cómo llega lesiones suspendidos bajas ` +
    partido.fecha;


  const consultas = [

    {
      tipo: 'PARTIDO',
      query: consultaPartido,
      topic: 'news',
      maxResults: 8
    },

    {
      tipo: 'LOCAL',
      query: consultaLocal,
      topic: 'general',
      searchDepth: 'advanced',
      maxResults: 10
    },

    {
      tipo: 'VISITANTE',
      query: consultaVisitante,
      topic: 'general',
      searchDepth: 'advanced',
      maxResults: 10
    },

    {
      tipo: 'TRANSMISION',
      query:
        // Búsqueda más corta y con nombres alternativos: los medios suelen
        // publicar "Atlético Mineiro" y "City Torque", no el nombre de GOAL.
        `${variantesBusquedaTransmision_(partido.local)} ` +
        `${variantesBusquedaTransmision_(partido.visitante)} ` +
        `${partido.competicion || ''} ${partido.fecha} ` +
        `previa actualidad novedades cómo llegan ambos equipos lesiones bajas sanciones ` +
        `Uruguay dónde ver en vivo Disney+ Premium ESPN ` +
        `programación televisación streaming canal fecha horario ` +
        `ESPN Uruguay Disney Plus Uruguay FútbolUy`,
      topic: 'general',
      searchDepth: 'advanced',
      maxResults: 8
    }

  ];


  // ============================================================
  // 4. EJECUTAR BÚSQUEDAS TAVILY
  // ============================================================

  let todosLosResultados = [];
  let resultadosTavilyActualizados = [];
  let busquedasTavily = { cacheadas: [], actualizadas: [], fallidas: [] };

  // Tavily obtiene las fuentes. Gemini queda dedicado a analizarlas, evitando
  // que un fallo de Google Search Grounding deje vacío todo el resumen.
  if (tavilyKey) {
    try {
      const consultaTavily = buscarTavilyPartidosEnParalelo_(
        tavilyKey,
        consultas,
        partido.pais,
        partido.fixtureId,
        permitirExceso,
        forzarActualizacion
      );
      todosLosResultados = consultaTavily.resultados;
      resultadosTavilyActualizados = consultaTavily.resultadosActualizados;
      busquedasTavily = consultaTavily.busquedas;
    }
    catch (error) {
      if (error && (error.code === 'TAVILY_DAILY_LIMIT' || error.code === 'TAVILY_MONTHLY_LIMIT')) throw error;
      Logger.log('Tavily no disponible: ' + error.message);
      busquedasTavily.fallidas.push(String(error.message || 'Tavily no disponible'));
      todosLosResultados = [];
      resultadosTavilyActualizados = [];
    }
  }


  // ============================================================
  // 5. ELIMINAR DUPLICADOS
  // ============================================================

  const resultadosUnicos = eliminarDuplicadosTavily(todosLosResultados);
  const resultadosFiltrados = filtrarResultadosTavilyPartido_(resultadosUnicos, partido);
  const transmisionConfirmada = detectarTransmisionUruguay_(resultadosFiltrados, partido);
  busquedasTavily.resultadosUtilesNuevos = filtrarResultadosTavilyPartido_(resultadosTavilyActualizados, partido).length;
  guardarDiagnosticoTransmision_(partido, resultadosUnicos, resultadosFiltrados);

  // Nunca reenviamos resultados que no mencionen a ninguno de los equipos:
  // esa relajación fue la que permitió noticias de Manchester City en un
  // partido de Japón y Uruguay.
  const resultados = resultadosFiltrados.sort(function(a, b) {
    const fechaA = Date.parse(a.published_date || '') || 0;
    const fechaB = Date.parse(b.published_date || '') || 0;
    if (fechaA !== fechaB) return fechaB - fechaA;
    return Number(b.score || 0) - Number(a.score || 0);
  });


  Logger.log(
    'Resultados Tavily: ' + resultadosUnicos.length +
    ' / luego de filtrar: ' + resultadosFiltrados.length +
    ' / enviados a Gemini: ' + resultados.length
  );


  // ============================================================
  // 6. SI TAVILY NO ENCONTRÓ NADA
  // ============================================================

  if (resultados.length === 0 && !formaGoal.hayDatos) {

    // Antes de devolver vacío, vemos si existe
    // un resumen anterior del partido.

    const respaldo =
      obtenerResumenRespaldo_(
        cacheKey
      );


    if (respaldo) {

      respaldo.desdeCache = true;

      respaldo.respaldoPorError = true;

      respaldo.motivoRespaldo =
        'No se encontró información nueva';
      respaldo.estadoActualizacion = 'informacion_anterior';
      respaldo.motivoActualizacion = 'La consulta manual no encontró fuentes nuevas útiles; se conserva el último resumen guardado.';
      respaldo.busquedasTavily = busquedasTavily;


      return respaldo;

    }


    const respuestaVacia = {

      ok: true,

      encontrado: false,

      fixtureId:
        partido.fixtureId,

      partido: partido,

      contexto:
        'No encontramos información reciente sobre este partido.',

      local: {

        nombre:
          partido.local,

        comoLlega:
          '',

        calidadInformacion:
          'sin_informacion'

      },

      visitante: {

        nombre:
          partido.visitante,

        comoLlega:
          '',

        calidadInformacion:
          'sin_informacion'

      },

      novedades: [],

      bajas: [],

      fuentes: [],

      actualizado:
        new Date().toISOString(),

      desdeCache:
        false,
      estadoActualizacion: 'sin_informacion_nueva',
      motivoActualizacion: 'La consulta terminó sin encontrar información nueva para este partido.',
      busquedasTavily: busquedasTavily

    };


    guardarCacheResumenPartido(
      cache,
      cacheKey,
      respuestaVacia
    );


      return respuestaVacia;

  }


  // ============================================================
  // 7. PREPARAR FUENTES PARA GEMINI
  // ============================================================

  let fuentesTexto = '';


  resultados.forEach(function(resultado, index) {

    fuentesTexto +=
      '\n\n==============================\n';


    fuentesTexto +=
      'FUENTE ' +
      (index + 1) +
      '\n';


    fuentesTexto +=
      'TIPO: ' +
      resultado.tipoBusqueda +
      '\n';


    fuentesTexto +=
      'TÍTULO: ' +
      resultado.title +
      '\n';


    fuentesTexto +=
      'FECHA: ' +
      (
        resultado.published_date ||
        'Sin fecha'
      ) +
      '\n';


    fuentesTexto +=
      'URL: ' +
      resultado.url +
      '\n';


    fuentesTexto +=
      'CONTENIDO:\n' +
      resultado.content +
      '\n';

  });

  const formaGoalTexto = construirTextoFormaGoal_(formaGoal);


  // ============================================================
  // 8. PROMPT GENÉRICO PARA GEMINI
  // ============================================================

  const prompt = `
Sos un asistente especializado en fútbol.

Generá información estructurada para una aplicación
de seguimiento de partidos.


==================================================
PARTIDO
==================================================

Equipo local:
${partido.local}

Equipo visitante:
${partido.visitante}

País:
${partido.pais || 'No especificado'}

Competición:
${partido.competicion || 'No especificada'}

Fecha:
${partido.fecha}

Hora:
${partido.hora || 'No especificada'}

Fecha en que se realiza esta consulta:
${fechaConsulta}


==================================================
IDENTIDAD DE LOS EQUIPOS
==================================================

El equipo local es EXACTAMENTE:

${partido.local}

País/contexto:
${partido.pais || 'No especificado'}

ID del proveedor:
${partido.localId || 'No disponible'}


El equipo visitante es EXACTAMENTE:

${partido.visitante}

País/contexto:
${partido.pais || 'No especificado'}

ID del proveedor:
${partido.visitanteId || 'No disponible'}


==================================================
REGLAS OBLIGATORIAS
==================================================

1.

Analizá las fuentes proporcionadas para este partido y la actualidad
reciente de ambos equipos. Utilizá únicamente información que
puedas respaldar con esas fuentes.

No utilices conocimiento interno para completar
información faltante.


2.

Es fundamental identificar correctamente los equipos.

Ignorá cualquier fuente que corresponda a otro club,
selección o categoría con un nombre similar.

Usá el país, la competición y el contexto del partido
para desambiguar los equipos.


3.

El partido corresponde al fútbol masculino.

Ignorá fútbol femenino o categorías juveniles salvo
que los datos del partido indiquen explícitamente
lo contrario.


4.

No inventes:

- resultados
- lesiones
- suspensiones
- jugadores
- alineaciones
- entrenadores
- posiciones
- puntos
- estadísticas
- rachas


4.bis

Para posiciones de tabla y fases de una copa, priorizá siempre la fuente
con fecha de publicación más reciente. Una noticia antigua que diga
"avanzó a cuartos" describe aquel momento y no demuestra que el equipo
siga actualmente en cuartos. Si una fuente posterior confirma semifinales,
esa es la fase vigente. Si no podés resolver la actualidad con una fuente
fechada y explícita, omití la fase o posición en lugar de presentar un dato
histórico como actual.


5.

Los resultados de DATOS GOAL API sí son partidos recientes
confirmados y son la fuente prioritaria para "comoLlega".

No interpretes estadísticas acumuladas del torneo como una
racha reciente.

Si una fuente informa:

6 partidos jugados
4 ganados
1 empatado
1 perdido

podés describir ese balance.

NO podés afirmar que se trata de una racha
o de los últimos seis partidos salvo que la fuente
lo indique explícitamente.


6.

Podés utilizar noticias individuales de cada equipo aunque no
mencionen este encuentro, siempre que correspondan claramente
al equipo correcto. Buscá también sus últimos resultados oficiales.


7.

No conviertas rumores en hechos.

7.bis

No repitas expresiones temporales relativas tomadas de una noticia antigua,
como "en los próximos días", "el próximo lunes", "espera el sorteo" o
"el sorteo está previsto". Solo describí un hecho futuro si una fuente actual
lo confirma con una fecha absoluta posterior a la fecha de hoy. Si no, omitilo.


8.

Si dos fuentes confiables se contradicen,
no elijas arbitrariamente una versión.


9.

No es obligatorio utilizar todas las fuentes.

Ignorá las irrelevantes.


10.

La calidad de información de cada equipo debe ser
EXACTAMENTE uno de estos valores:

"buena"
"limitada"
"sin_informacion"


"buena":
hay varias fuentes recientes y útiles.

"limitada":
hay alguna información útil, pero es escasa.

"sin_informacion":
no existe información útil suficiente.


11.

Una baja solamente puede incluirse cuando la ausencia
del jugador esté explícitamente confirmada en las fuentes.


12.

Para "transmision", informá EXCLUSIVAMENTE opciones confirmadas
para ver el partido desde Uruguay.

No supongas que un canal transmite el partido por tener derechos
generales de la competición.
Las fuentes de programación regional sin este partido específico son SOLO contexto; nunca prueban la emisión de este encuentro.

Incluí un canal de TV o servicio de streaming solamente si una
fuente proporcionada confirma este partido concreto o una programación
inequívoca para la fecha indicada.

Si no hay confirmación suficiente, devolvé:
"confirmada": false,
"canales": [],
"streaming": [],
"texto": "Transmisión no confirmada para Uruguay."

No incluyas señales o plataformas disponibles únicamente en otros países.
No confundas los derechos generales de una liga con la emisión de este encuentro.
Si una fuente sólo indica "dónde ver" para Argentina, España, México,
Estados Unidos u otro país, no la uses como confirmación para Uruguay.
Una página de programación sin fecha, equipos y competición coincidentes
no confirma la transmisión.
Si hay evidencia concreta de TV pero no de streaming, completá sólo
"canales"; si hay evidencia de streaming pero no de TV, completá sólo
"streaming". No deduzcas que un canal y su aplicación transmiten lo mismo.
No presentes como confirmada una transmisión que sea una posibilidad,
una predicción o una emisión de otro partido de los mismos equipos.
Para transmisiones priorizá fuentes uruguayas recientes (por ejemplo,
Montevideo Portal / FútbolUy) si mencionan ambos equipos, fecha y señal.
Reconocé alias de prensa como Atlético-MG / Atlético Mineiro y
City Torque / Montevideo City Torque.
Revisá explícitamente si las fuentes mencionan ESPN (y sus señales),
Disney+, DSports / DirecTV Sports, DGO, VTV, Tenfield u otras opciones.
DSports es una señal de TV y DGO es streaming: no los confundas.
Que una plataforma no aparezca en las fuentes no demuestra que no emita
el partido. Nunca deduzcas disponibilidad por derechos generales. Una fuente de Uruguay que
confirme explícitamente la emisión de este encuentro es válida,
aunque no use la expresión literal "disponible en Uruguay".
No agregues ESPN, Disney+, DSports, DGO ni otras plataformas sólo
porque aparecen en la consulta de búsqueda: deben estar respaldadas
por el contenido de una fuente concreta.


==================================================
RESPUESTA
==================================================

Respondé ÚNICAMENTE JSON válido.

No uses Markdown.

No escribas texto antes o después del JSON.

La estructura debe ser EXACTAMENTE:

{
  "contexto": "texto",
  "local": {
    "nombre": "${partido.local}",
    "comoLlega": "texto",
    "calidadInformacion": "buena"
  },
  "visitante": {
    "nombre": "${partido.visitante}",
    "comoLlega": "texto",
    "calidadInformacion": "buena"
  },
  "novedades": [
    "novedad"
  ],
  "bajas": [
    {
      "equipo": "equipo",
      "jugador": "jugador",
      "motivo": "motivo confirmado"
    }
  ],
  "transmision": {
    "pais": "Uruguay",
    "confirmada": false,
    "canales": [],
    "streaming": [],
    "texto": "Transmisión no confirmada para Uruguay."
  },
  "fuentes": [
    {
      "titulo": "título de la fuente",
      "url": "https://...",
      "fecha": "fecha si está disponible",
      "tipo": "PARTIDO, LOCAL o VISITANTE"
    }
  ]
}


"contexto":
máximo 3 frases.

"comoLlega":
máximo 3 frases por equipo.

"novedades":
máximo 4 elementos.

Si no hay novedades:
[]

Si no hay bajas confirmadas:
[]

"transmision":
debe referirse únicamente a disponibilidad desde Uruguay.
"canales" y "streaming" deben ser arrays de texto sin duplicados.
"confirmada" sólo puede ser true cuando una fuente respalda
la transmisión de este partido concreto.

"fuentes":
incluí solamente fuentes realmente utilizadas y directamente
relacionadas con este partido o con uno de los dos equipos.
No incluyas resultados de búsqueda irrelevantes.


==================================================
DATOS GOAL API - FORMA RECIENTE CONFIRMADA
==================================================

${formaGoalTexto}


==================================================
FUENTES
==================================================

${fuentesTexto}
`;


  // ============================================================
  // 9. PREPARAR PETICIÓN GEMINI
  // ============================================================

  const geminiPayload = {

    model:
      'gemini-3.1-flash-lite',

    input:
      prompt,

    // Desde junio de 2026 Interactions API exige este formato para salidas
    // estructuradas. Evita que una respuesta correcta termine descartada por
    // Markdown, texto adicional o pequeñas variaciones de JSON.
    response_format: {
      type: 'text',
      mime_type: 'application/json',
      schema: {
        type: 'object',
        required: ['contexto', 'local', 'visitante', 'novedades', 'bajas', 'transmision', 'fuentes'],
        properties: {
          contexto: { type: 'string' },
          local: {
            type: 'object',
            required: ['nombre', 'comoLlega', 'calidadInformacion'],
            properties: {
              nombre: { type: 'string' },
              comoLlega: { type: 'string' },
              calidadInformacion: {
                type: 'string',
                enum: ['buena', 'limitada', 'sin_informacion']
              }
            }
          },
          visitante: {
            type: 'object',
            required: ['nombre', 'comoLlega', 'calidadInformacion'],
            properties: {
              nombre: { type: 'string' },
              comoLlega: { type: 'string' },
              calidadInformacion: {
                type: 'string',
                enum: ['buena', 'limitada', 'sin_informacion']
              }
            }
          },
          novedades: { type: 'array', items: { type: 'string' } },
          bajas: {
            type: 'array',
            items: {
              type: 'object',
              required: ['equipo', 'jugador', 'motivo'],
              properties: {
                equipo: { type: 'string' },
                jugador: { type: 'string' },
                motivo: { type: 'string' }
              }
            }
          },
          transmision: {
            type: 'object',
            required: ['pais', 'confirmada', 'canales', 'streaming', 'texto'],
            properties: {
              pais: { type: 'string' },
              confirmada: { type: 'boolean' },
              canales: { type: 'array', items: { type: 'string' } },
              streaming: { type: 'array', items: { type: 'string' } },
              texto: { type: 'string' }
            }
          },
          fuentes: {
            type: 'array',
            items: {
              type: 'object',
              required: ['titulo', 'url', 'fecha', 'tipo'],
              properties: {
                titulo: { type: 'string' },
                url: { type: 'string' },
                fecha: { type: 'string' },
                tipo: { type: 'string' }
              }
            }
          }
        }
      }
    }

  };


  // ============================================================
  // 10. GEMINI CON REINTENTOS + FALLBACK
  // ============================================================

  let geminiData;


  try {

    if (!geminiKey) {
      throw new Error('Servicio de IA no configurado');
    }

    geminiData =
      consultarGeminiConReintentos_(
        geminiKey,
        geminiPayload
      );

  }
  catch (error) {

    Logger.log(
      'Gemini no disponible: ' +
      error.message
    );


    // ==========================================================
    // BUSCAR ÚLTIMO RESUMEN VÁLIDO
    // ==========================================================

    const respaldo =
      obtenerResumenRespaldo_(
        cacheKey
      );


    if (respaldo) {

      respaldo.desdeCache = true;

      respaldo.respaldoPorError = true;

      respaldo.motivoRespaldo =
        'Servicio de IA temporalmente no disponible';
      respaldo.estadoActualizacion = 'informacion_anterior';
      respaldo.motivoActualizacion = 'Gemini no respondió; se conserva el último resumen guardado.';
      respaldo.busquedasTavily = busquedasTavily;


      Logger.log(
        'Se devuelve el último resumen disponible.'
      );


      return respaldo;

    }


    // Aunque Gemini esté temporalmente caído, devolvemos un resumen útil
    // y determinista con los datos confirmados de GOAL API.
    const resumenSinIA = construirResumenSinIa_(partido, formaGoal, resultados);
    resumenSinIA.transmision = resolverTransmisionUruguay_(null, transmisionConfirmada, respaldoAnterior);
    guardarCacheResumenPartido(cache, cacheKey, resumenSinIA);
    return resumenSinIA;

  }


  // ============================================================
  // 11. EXTRAER TEXTO DE GEMINI
  // ============================================================

  let textoGemini = '';
  let fuentesGrounding = [];

  // Compatibilidad defensiva con respuestas antiguas y nuevas de la API.
  // En REST el campo de conveniencia output_text puede no estar presente,
  // pero lo usamos si Google lo devuelve.
  if (typeof geminiData.output_text === 'string') {
    textoGemini = geminiData.output_text;
  }


  if (geminiData.steps) {

    geminiData.steps.forEach(function(step) {

      if (
        step.type === 'model_output' &&
        step.content
      ) {

        step.content.forEach(function(item) {

          if (
            item.type === 'text' &&
            item.text
          ) {

              if (!textoGemini) textoGemini += item.text;

            if (Array.isArray(item.annotations)) {
              item.annotations.forEach(function(anotacion) {
                if (anotacion && anotacion.type === 'url_citation' && anotacion.url) {
                  fuentesGrounding.push({
                    titulo: anotacion.title || anotacion.url,
                    url: anotacion.url,
                    fecha: '',
                    tipo: 'PARTIDO'
                  });
                }
              });
            }

          }

        });

      }

    });

  }

  // Compatibilidad con la respuesta anterior de Interactions API.
  if (!textoGemini && Array.isArray(geminiData.outputs)) {
    geminiData.outputs.forEach(function(item) {
      if (item && item.type === 'text' && item.text) textoGemini += item.text;
    });
  }


  // ============================================================
  // 12. LIMPIAR JSON
  // ============================================================

  textoGemini =
    limpiarRespuestaJsonGemini(
      textoGemini
    );


  let resumenIA;


  try {

    resumenIA =
      JSON.parse(
        textoGemini
      );

  }
  catch (error) {

    Logger.log(
      'Gemini devolvió JSON inválido.'
    );


    // Si el JSON generado estuviera mal,
    // también intentamos usar el último resumen válido.

    const respaldo =
      obtenerResumenRespaldo_(
        cacheKey
      );


    if (respaldo) {

      respaldo.desdeCache = true;

      respaldo.respaldoPorError = true;

      respaldo.motivoRespaldo =
        'La actualización generó una respuesta inválida';
      respaldo.estadoActualizacion = 'informacion_anterior';
      respaldo.motivoActualizacion = 'Gemini devolvió una respuesta inválida; se conserva el último resumen guardado.';
      respaldo.busquedasTavily = busquedasTavily;


      return respaldo;

    }


    throw new Error(
      'Gemini devolvió JSON inválido: ' +
      error.message +
      '\n\nRespuesta:\n' +
      textoGemini
    );

  }


  // ============================================================
  // 13. CONSTRUIR FUENTES VISIBLES
  // ============================================================

  const fuentesFinales = [];


  const fuentesUsadas =
    Array.isArray(
      resumenIA.fuentesUtilizadas
    )
      ? resumenIA.fuentesUtilizadas
      : [];


  fuentesUsadas.forEach(function(numero) {

    const indice =
      Number(numero) - 1;


    if (
      indice < 0 ||
      indice >= resultados.length
    ) {

      return;

    }


    const fuente =
      resultados[indice];


    // Gemini puede haber utilizado una fuente indirecta
    // para obtener información válida.
    //
    // Eso NO significa necesariamente que debamos
    // mostrársela al usuario.

    if (
      !fuenteVisibleParaPartido(
        fuente,
        partido
      )
    ) {

      Logger.log(
        'Fuente utilizada internamente pero no visible: ' +
        fuente.title
      );


      return;

    }


    fuentesFinales.push({

      titulo:
        fuente.title || '',

      url:
        normalizarUrlFuente(
          fuente.url
        ),

      fecha:
        fuente.published_date || '',

      tipo:
        fuente.tipoBusqueda || ''

    });

  });


  const fuentesDeclaradas = fuentesGrounding.length > 0
    ? fuentesGrounding
    : (Array.isArray(resumenIA.fuentes) ? resumenIA.fuentes : []);

  if (fuentesDeclaradas.length > 0) {
    const urlsFuentes = {};
    fuentesDeclaradas.slice(0, 8).forEach(function(fuente) {
      if (!fuente) return;
      const url = normalizarUrlFuente(fuente.url || '');
      if (!/^https?:\/\//i.test(url) || urlsFuentes[url]) return;
      urlsFuentes[url] = true;
      fuentesFinales.push({
        titulo: String(fuente.titulo || url),
        url: url,
        fecha: String(fuente.fecha || ''),
        tipo: String(fuente.tipo || 'PARTIDO')
      });
    });
  }

  // ============================================================
  // 14. RESPUESTA FINAL
  // ============================================================

  const respuestaFinal = {

    ok: true,

    encontrado: true,

    fixtureId:
      partido.fixtureId,

    partido: {

      local:
        partido.local,

      visitante:
        partido.visitante,

      pais:
        partido.pais,

      competicion:
        partido.competicion,

      fecha:
        partido.fecha,

      hora:
        partido.hora,

      localId:
        partido.localId,

      visitanteId:
        partido.visitanteId

    },

    contexto:
      resumenIA.contexto || '',

    local:
      limpiarResumenTemporal_(validarResumenEquipo(
        resumenIA.local,
        partido.local
      )),

    visitante:
      limpiarResumenTemporal_(validarResumenEquipo(
        resumenIA.visitante,
        partido.visitante
      )),

    novedades: filtrarNovedadesPartido_(
      Array.isArray(resumenIA.novedades) ? resumenIA.novedades : [],
      partido
    ).map(limpiarAfirmacionesTemporales_).filter(function(texto) { return !!texto; }).slice(0, 4),

    bajas:
      Array.isArray(
        resumenIA.bajas
      )
        ? resumenIA.bajas
        : [],

    // La confirmación sale de las fuentes, no de una inferencia del modelo.
    // Gemini puede completar la redacción, pero no crear plataformas nuevas.
    transmision: resolverTransmisionUruguay_(resumenIA.transmision, transmisionConfirmada, respaldoAnterior),

    fuentes:
      fuentesFinales,

    actualizado:
      new Date().toISOString(),

    desdeCache:
      false,

    respaldoPorError:
      false,

    estadoActualizacion: busquedasTavily.resultadosUtilesNuevos > 0
      ? 'informacion_actualizada'
      : (busquedasTavily.fallidas.length > 0 ? 'parcial' : 'sin_informacion_nueva'),

    motivoActualizacion: motivoActualizacionTavily_(busquedasTavily),

    busquedasTavily: busquedasTavily

  };

  if (!transmisionConfirmada.confirmada && respaldoAnterior && validarTransmisionUruguay_(respaldoAnterior.transmision).confirmada) {
    respuestaFinal.transmision.conservadaDeConsultaAnterior = true;
    respuestaFinal.transmision.motivoConservacion = 'No se encontró una confirmación nueva; se conserva la transmisión confirmada anteriormente.';
  }


  // ============================================================
  // 15. GUARDAR CACHE + RESPALDO
  // ============================================================

  Logger.log('TRANSMISION_GEMINI ' + JSON.stringify({fixtureId: partido.fixtureId, decision: resumenIA.transmision || null, enviada: respuestaFinal.transmision}));

  guardarCacheResumenPartido(
    cache,
    cacheKey,
    respuestaFinal
  );


  Logger.log(
    'Resumen generado correctamente.'
  );


  return respuestaFinal;

}

/**
 * Obtiene los últimos partidos terminados de ambos equipos directamente de
 * GOAL API. La API ha usado distintas rutas a lo largo del tiempo; se prueban
 * las rutas de historial conocidas y se conserva en caché la primera válida.
 */
function obtenerFormaRecienteGoal_(partido) {
  const local = obtenerPartidosRecientesEquipoGoal_(partido.localId, partido.local, partido.fixtureId);
  const visitante = obtenerPartidosRecientesEquipoGoal_(partido.visitanteId, partido.visitante, partido.fixtureId);

  return {
    hayDatos: local.length > 0 || visitante.length > 0,
    local: local,
    visitante: visitante
  };
}

function obtenerPartidosRecientesEquipoGoal_(teamId, nombreEquipo, fixtureIdActual) {
  if (!teamId) return [];

  const id = encodeURIComponent(String(teamId));
  const rutas = [
    '/teams/' + id + '/fixtures',
    '/teams/' + id + '/results',
    '/results/team/' + id
  ];
  let partidos = [];

  for (let i = 0; i < rutas.length; i++) {
    try {
      const respuesta = fetchApi_(rutas[i], { limit: rutas[i].indexOf('/fixtures') !== -1 ? '100' : '12' }, 300);
      const candidatos = dataArray_(respuesta);
      if (candidatos.length) {
        partidos = candidatos;
        break;
      }
    }
    catch (error) {
      Logger.log('Ruta de forma GOAL no disponible (' + rutas[i] + '): ' + error.message);
    }
  }

  // No considerar resultados sin estado final confirmado ni encuentros futuros.
  const estadosFinales = ['finished', 'full time', 'fulltime', 'ft', 'after extra time', 'aet', 'after penalties', 'penalties', 'finalizado', 'terminado'];
  const terminados = partidos.filter(function(f) {
    const idFixture = String(f.id || f.fixtureId || f.apiId || f.match_id || '');
    if (idFixture && idFixture === String(fixtureIdActual || '')) return false;
    const estado = normalizar_(f.matchStatus || f.match_status || f.status || f.matchPeriod || '');
    if (!estadosFinales.some(function(e) { return estado === e || estado.indexOf(e + ' ') === 0; })) return false;
    if (numeroMarcadorGoal_(f, true) === null || numeroMarcadorGoal_(f, false) === null) return false;
    const fecha = f.kickoffUtc || f.matchDate || f.match_date || f.date || '';
    if (fecha) {
      const instante = new Date(fecha).getTime();
      if (!isNaN(instante) && instante > Date.now()) return false;
    }
    const localNombre = String(f.homeTeamName || f.match_hometeam_name || (f.homeTeam && f.homeTeam.name) || '');
    const visitanteNombre = String(f.awayTeamName || f.match_awayteam_name || (f.awayTeam && f.awayTeam.name) || '');
    const homeId = f.homeTeamId || f.match_hometeam_id || (f.homeTeam && f.homeTeam.id) || '';
    const awayId = f.awayTeamId || f.match_awayteam_id || (f.awayTeam && f.awayTeam.id) || '';
    // Si hay identificadores, exigir coincidencia; los nombres son respaldo solamente.
    if (homeId || awayId) return String(homeId) === String(teamId) || String(awayId) === String(teamId);
    return normalizar_(localNombre) === normalizar_(nombreEquipo) || normalizar_(visitanteNombre) === normalizar_(nombreEquipo);
  });

  terminados.sort(function(a, b) {
    return String(b.kickoffUtc || b.matchDate || b.match_date || b.date || '').localeCompare(
      String(a.kickoffUtc || a.matchDate || a.match_date || a.date || '')
    );
  });

  return terminados.slice(0, 5).map(function(f) {
    const localNombre = String(f.homeTeamName || f.match_hometeam_name || (f.homeTeam && f.homeTeam.name) || 'Local');
    const visitanteNombre = String(f.awayTeamName || f.match_awayteam_name || (f.awayTeam && f.awayTeam.name) || 'Visitante');
    const golesLocal = numeroMarcadorGoal_(f, true);
    const golesVisitante = numeroMarcadorGoal_(f, false);
    const homeId = f.homeTeamId || f.match_hometeam_id || (f.homeTeam && f.homeTeam.id) || '';
    const equipoEsLocal = homeId ? String(homeId) === String(teamId) : normalizar_(localNombre) === normalizar_(nombreEquipo);
    let resultado = 'empate';
    if (golesLocal !== golesVisitante) {
      const ganoLocal = golesLocal > golesVisitante;
      resultado = (equipoEsLocal === ganoLocal) ? 'victoria' : 'derrota';
    }
    return {
      fecha: String(f.matchDate || f.match_date || f.date || f.kickoffUtc || ''),
      competicion: String(f.leagueName || f.league_name || (f.league && f.league.name) || ''),
      local: localNombre,
      visitante: visitanteNombre,
      golesLocal: golesLocal,
      golesVisitante: golesVisitante,
      resultadoEquipo: resultado
    };
  });
}

function numeroMarcadorGoal_(fixture, local) {
  const claves = local
    ? ['homeTeamFtScore', 'homeTeamScore', 'homeScore', 'match_hometeam_score']
    : ['awayTeamFtScore', 'awayTeamScore', 'awayScore', 'match_awayteam_score'];
  for (let i = 0; i < claves.length; i++) {
    const valor = fixture[claves[i]];
    if (valor !== null && valor !== undefined && valor !== '' && !isNaN(Number(valor))) return Number(valor);
  }
  if (fixture.score) {
    const valorScore = local ? fixture.score.home : fixture.score.away;
    if (valorScore !== null && valorScore !== undefined && !isNaN(Number(valorScore))) return Number(valorScore);
  }
  return null;
}

function construirTextoFormaGoal_(formaGoal) {
  function bloque(nombre, partidos) {
    if (!partidos.length) return nombre + ': sin resultados recientes disponibles en GOAL API.';
    return nombre + ':\n' + partidos.map(function(p) {
      return '- ' + p.fecha + ': ' + p.local + ' ' + p.golesLocal + '-' + p.golesVisitante + ' ' + p.visitante +
        (p.competicion ? ' (' + p.competicion + ')' : '') + '. Resultado del equipo: ' + p.resultadoEquipo + '.';
    }).join('\n');
  }
  return bloque('Equipo local', formaGoal.local) + '\n\n' + bloque('Equipo visitante', formaGoal.visitante);
}

function describirFormaGoal_(partidos) {
  if (!partidos.length) return '';
  let victorias = 0, empates = 0, derrotas = 0;
  partidos.forEach(function(p) {
    if (p.resultadoEquipo === 'victoria') victorias++;
    else if (p.resultadoEquipo === 'derrota') derrotas++;
    else empates++;
  });
  const detalle = partidos.map(function(p) {
    return p.local + ' ' + p.golesLocal + '-' + p.golesVisitante + ' ' + p.visitante;
  }).join('; ');
  return 'En sus últimos ' + partidos.length + ' partidos registrados obtuvo ' + victorias + ' victoria(s), ' +
    empates + ' empate(s) y ' + derrotas + ' derrota(s). Resultados: ' + detalle + '.';
}

function construirResumenSinIa_(partido, formaGoal, resultados) {
  const localTexto = describirFormaGoal_(formaGoal.local);
  const visitanteTexto = describirFormaGoal_(formaGoal.visitante);
  const hayContenido = !!localTexto || !!visitanteTexto || resultados.length > 0;
  return {
    ok: true,
    encontrado: hayContenido,
    fixtureId: partido.fixtureId,
    partido: partido,
    contexto: partido.local + ' enfrenta a ' + partido.visitante +
      (partido.competicion ? ' por ' + partido.competicion : '') +
      ' el ' + partido.fecha + (partido.hora ? ' a las ' + partido.hora : '') + '.',
    local: { nombre: partido.local, comoLlega: localTexto, calidadInformacion: localTexto ? 'buena' : 'sin_informacion' },
    visitante: { nombre: partido.visitante, comoLlega: visitanteTexto, calidadInformacion: visitanteTexto ? 'buena' : 'sin_informacion' },
    // No mostramos títulos crudos de Tavily como si fueran novedades
    // redactadas: pueden estar en otro idioma o ser simples fichas de score.
    novedades: [],
    bajas: [],
    transmision: {
      pais: 'Uruguay',
      confirmada: false,
      canales: [],
      streaming: [],
      texto: 'Transmisión no confirmada para Uruguay.'
    },
    fuentes: resultados.slice(0, 6).map(function(resultado) {
      return {
        titulo: String(resultado.title || resultado.url || ''),
        url: normalizarUrlFuente(resultado.url || ''),
        fecha: String(resultado.published_date || ''),
        tipo: String(resultado.tipoBusqueda || '')
      };
    }).filter(function(fuente) { return !!fuente.url; }),
    actualizado: new Date().toISOString(),
    desdeCache: false,
    respaldoPorError: false
  };
}

function limpiarAfirmacionesTemporales_(texto) {
  return String(texto || '').split(/(?<=[.!?])\s+/).filter(function(frase) {
    const normalizada = normalizarTextoComparacion_(frase);
    const sorteoIncierto = normalizada.indexOf('sorteo') !== -1 &&
      /(proxim|espera|previst|participara|participa en)/.test(normalizada);
    const futuroRelativo = /en los proximos dias|el proximo (lunes|martes|miercoles|jueves|viernes|sabado|domingo)/.test(normalizada);
    return !sorteoIncierto && !futuroRelativo;
  }).join(' ').trim();
}

function limpiarResumenTemporal_(resumen) {
  if (!resumen) return resumen;
  resumen.comoLlega = limpiarAfirmacionesTemporales_(resumen.comoLlega || '');
  if (!resumen.comoLlega) resumen.calidadInformacion = 'sin_informacion';
  return resumen;
}

function filtrarNovedadesPartido_(novedades, partido) {
  const nombres = obtenerVariantesNombreEquipo_(
    normalizarTextoComparacion_(partido.local)
  ).concat(obtenerVariantesNombreEquipo_(
    normalizarTextoComparacion_(partido.visitante)
  ));

  return novedades.filter(function(novedad) {
    const texto = normalizarTextoComparacion_(String(novedad || ''));
    if (!texto) return false;

    // Los marcadores y fichas de partidos anteriores pertenecen a
    // "Cómo llega", no a "Últimas novedades".
    if (/\blive score\b|\bmarcador\b|\bresultado\b/.test(texto)) return false;

    return nombres.some(function(nombre) {
      return nombre.length >= 4 && texto.indexOf(nombre) !== -1;
    });
  });
}


function validarTransmisionUruguay_(datos) {

  const vacia = {
    pais: 'Uruguay',
    confirmada: false,
    canales: [],
    streaming: [],
    texto: 'Transmisión no confirmada para Uruguay.'
  };

  if (!datos || datos.confirmada !== true) {
    return vacia;
  }

  function listaLimpia(valores) {
    if (!Array.isArray(valores)) return [];
    const vistos = {};
    return valores.map(function(valor) {
      return String(valor || '').trim();
    }).filter(function(valor) {
      if (!valor) return false;
      const clave = normalizarTextoComparacion_(valor);
      if (!clave || vistos[clave]) return false;
      vistos[clave] = true;
      return true;
    }).slice(0, 6);
  }

  const canales = listaLimpia(datos.canales);
  const streaming = listaLimpia(datos.streaming);

  if (canales.length === 0 && streaming.length === 0) {
    return vacia;
  }

  return {
    pais: 'Uruguay',
    confirmada: true,
    canales: canales,
    streaming: streaming,
    texto: String(datos.texto || '').trim()
  };
}

/**
 * Extrae señales únicamente de fuentes que identifican este encuentro y su
 * disponibilidad en Uruguay. No depende de Gemini y no realiza búsquedas.
 */
function detectarTransmisionUruguay_(resultados, partido) {
  const canales = [], streaming = [], fuentes = [];
  const vistosCanal = {}, vistosStreaming = {};

  function agregar(destino, vistos, valor) {
    const clave = normalizarTextoComparacion_(valor);
    if (!clave || vistos[clave]) return;
    vistos[clave] = true; destino.push(valor);
  }

  (resultados || []).filter(function(resultado) {
    return resultado && resultado.tipoBusqueda === 'TRANSMISION';
  }).forEach(function(resultado) {
    const evaluacion = evaluarFuenteTavilyPartido_(resultado, partido);
    if (!evaluacion.aceptada || evaluacion.motivo.indexOf('Programación regional') === 0) return;

    const titulo = normalizarTextoComparacion_(resultado.title || '');
    const contenido = normalizarTextoComparacion_(resultado.content || '');
    const contenidoOriginal = String(resultado.content || '');
    const url = String(resultado.url || '').toLowerCase();
    const texto = ' ' + titulo + ' ' + contenido + ' ';
    const dominioUruguayo = /(?:\.com\.uy|\.uy)(?:\/|$)/.test(url);
    const mencionaUruguay = /\buruguay\b|\buruguayo\b|\buru\b/.test(texto);
    if (!dominioUruguayo && !mencionaUruguay) return;

    // El canal debe aparecer atribuido en una frase que hable de emisión y
    // disponibilidad uruguaya, no sólo en el texto general de derechos de liga.
    const frases = [String(resultado.title || '')].concat(contenidoOriginal.split(/(?<=[.!?])\s+|\n+/));
    const evidenciaEmision = frases.filter(function(frase) {
      const normalizada = normalizarTextoComparacion_(frase);
      const normalizadaConEspacios = ' ' + normalizada + ' ';
      const localizacionUY = dominioUruguayo || /\buruguay\b|\buruguayo\b|\buru\b/.test(normalizada);
      const hablaDeEmision = /donde ver|como ver|televisa|televisacion|transmision|ver en vivo|canal de tv|streaming|programacion|disponible/.test(normalizada);
      const nombraProveedor = /espn|dsports|directv sports|vtv|tenfield|tnt sports|disney|dgo|auf tv|antel tv/.test(normalizada);
      function mencionaEquipo(nombre) {
        return obtenerVariantesNombreEquipo_(normalizarTextoComparacion_(nombre)).some(function(variant) {
          return variant.length >= 4 && normalizadaConEspacios.indexOf(' ' + variant + ' ') !== -1;
        });
      }
      // La misma frase de emisión debe identificar a ambos equipos; esto evita
      // convertir derechos generales de una liga en una señal para un partido.
      return localizacionUY && hablaDeEmision && nombraProveedor && mencionaEquipo(partido.local) && mencionaEquipo(partido.visitante);
    }).join(' ');
    const evidencia = normalizarTextoComparacion_(evidenciaEmision);
    if (!evidencia) return;

    if (/\bdsports 2\b|\bdirectv sports 2\b/.test(evidencia)) agregar(canales,vistosCanal,'DSports 2');
    else if (/\bdsports\b|\bdirectv sports\b/.test(evidencia)) agregar(canales,vistosCanal,'DSports');
    if (/\bespn premium\b/.test(evidencia)) agregar(canales,vistosCanal,'ESPN Premium');
    else if (/\bespn 4\b/.test(evidencia)) agregar(canales,vistosCanal,'ESPN 4');
    else if (/\bespn 3\b/.test(evidencia)) agregar(canales,vistosCanal,'ESPN 3');
    else if (/\bespn 2\b/.test(evidencia)) agregar(canales,vistosCanal,'ESPN 2');
    else if (/\bespn\b/.test(evidencia)) agregar(canales,vistosCanal,'ESPN');
    if (/\bvtv plus\b|\bvtv \+\b/.test(evidencia)) agregar(canales,vistosCanal,'VTV Plus');
    else if (/\bvtv\b/.test(evidencia)) agregar(canales,vistosCanal,'VTV');
    if (/\btenfield\b/.test(evidencia)) agregar(canales,vistosCanal,'Tenfield');
    if (/\btnt sports\b/.test(evidencia)) agregar(canales,vistosCanal,'TNT Sports');

    if (/\bdisney plus premium\b|\bdisney premium\b/.test(evidencia)) agregar(streaming,vistosStreaming,'Disney+ Premium');
    else if (/\bdisney plus\b|\bdisney\b/.test(evidencia)) agregar(streaming,vistosStreaming,'Disney+');
    if (/\bdgo\b/.test(evidencia)) agregar(streaming,vistosStreaming,'DGO');
    if (/\bauf tv\b/.test(evidencia)) agregar(streaming,vistosStreaming,'AUF TV');
    if (/\bantel tv\b/.test(evidencia)) agregar(streaming,vistosStreaming,'Antel TV');

    if (canales.length || streaming.length) fuentes.push(String(resultado.url || ''));
  });

  const confirmada = canales.length > 0 || streaming.length > 0;
  return {
    pais: 'Uruguay', confirmada: confirmada,
    canales: canales.slice(0,6), streaming: streaming.slice(0,6),
    texto: confirmada ? 'Transmisión confirmada para Uruguay.' : 'Transmisión no confirmada para Uruguay.',
    fuentesConfirmacion: fuentes.slice(0,6)
  };
}

function resolverTransmisionUruguay_(gemini, detectada, respaldoAnterior) {
  const confirmada = validarTransmisionUruguay_(detectada);
  if (confirmada.confirmada) {
    // La redacción de Gemini sólo se conserva si no altera las señales
    // verificadas de forma determinista.
    const textoGemini = gemini && gemini.confirmada === true ? String(gemini.texto || '').trim() : '';
    if (textoGemini) confirmada.texto = textoGemini;
    return confirmada;
  }
  const anterior = respaldoAnterior && validarTransmisionUruguay_(respaldoAnterior.transmision);
  if (anterior && anterior.confirmada) return anterior;
  return validarTransmisionUruguay_(null);
}

function limpiarRespuestaJsonGemini(texto) {

  let limpio =
    String(texto || '').trim();


  limpio =
    limpio.replace(
      /^```json\s*/i,
      ''
    );


  limpio =
    limpio.replace(
      /^```\s*/i,
      ''
    );


  limpio =
    limpio.replace(
      /\s*```$/,
      ''
    );


  return limpio.trim();

}

function normalizarUrlFuente(url) {

  let valor =
    String(url || '').trim();


  if (!valor) {
    return '';
  }


  // Caso Markdown:
  // [https://sitio.com/...](https://sitio.com/...)

  const markdown =
    valor.match(
      /^\[([^\]]+)\]\((https?:\/\/[^)]+)\)$/
    );


  if (markdown) {

    return markdown[2];

  }


  // Buscar una URL HTTP/HTTPS dentro del texto.

  const urlEncontrada =
    valor.match(
      /https?:\/\/[^\s\])]+/
    );


  if (urlEncontrada) {

    return urlEncontrada[0];

  }


  return valor;

}

function validarResumenEquipo(
  datos,
  nombreEsperado
) {

  const valoresPermitidos = [
    'buena',
    'limitada',
    'sin_informacion'
  ];


  if (!datos) {

    return {

      nombre:
        nombreEsperado,

      comoLlega:
        '',

      calidadInformacion:
        'sin_informacion'

    };

  }


  let calidad =
    String(
      datos.calidadInformacion ||
      'sin_informacion'
    );


  if (
    valoresPermitidos.indexOf(
      calidad
    ) === -1
  ) {

    calidad =
      'sin_informacion';

  }


  return {

    // No permitimos que Gemini cambie
    // el nombre del equipo.

    nombre:
      nombreEsperado,

    comoLlega:
      String(
        datos.comoLlega || ''
      ),

    calidadInformacion:
      calidad

  };

}

function guardarCacheResumenPartido(
  cache,
  cacheKey,
  respuesta
) {

  const texto =
    JSON.stringify(
      respuesta
    );

  // Una respuesta vacía puede guardarse sólo unos minutos para evitar ráfagas,
  // pero nunca debe reemplazar el último respaldo que sí tenía información.
  if (respuesta && respuesta.encontrado === false) {
    try {
      cache.put(cacheKey, texto, 3 * 60);
    }
    catch (error) {
      Logger.log('No se pudo guardar cache vacío: ' + error.message);
    }
    return;
  }


  // ============================================================
  // CACHE RÁPIDO
  // ============================================================

  try {

    const segundos =
      30 * 60;


    cache.put(
      cacheKey,
      texto,
      segundos
    );

  }
  catch (error) {

    Logger.log(
      'No se pudo guardar CacheService: ' +
      error.message
    );

  }


  // ============================================================
  // RESPALDO
  // ============================================================

  try {

    const props =
      PropertiesService
        .getScriptProperties();


    props.setProperty(
      'backup_' + cacheKey,
      texto
    );

  }
  catch (error) {

    Logger.log(
      'No se pudo guardar respaldo: ' +
      error.message
    );

  }

}

function fuenteVisibleParaPartido(
  fuente,
  partido
) {

  if (!fuente) {
    return false;
  }


  // Para decidir si una fuente se muestra al usuario
  // utilizamos principalmente el TÍTULO.
  //
  // El contenido puede contener tablas, clasificaciones,
  // resultados relacionados, etc. y mencionar a nuestros
  // equipos aunque la página trate de otro partido.

  const titulo =
    normalizarTextoComparacion_(
      fuente.title || ''
    );


  if (!titulo) {
    return false;
  }


  const variantesLocal =
    obtenerVariantesNombreEquipo_(
      normalizarTextoComparacion_(
        partido.local
      )
    );


  const variantesVisitante =
    obtenerVariantesNombreEquipo_(
      normalizarTextoComparacion_(
        partido.visitante
      )
    );


  const mencionaLocal =
    variantesLocal.some(
      function(nombre) {

        return (
          nombre.length >= 4 &&
          titulo.indexOf(nombre) !== -1
        );

      }
    );


  const mencionaVisitante =
    variantesVisitante.some(
      function(nombre) {

        return (
          nombre.length >= 4 &&
          titulo.indexOf(nombre) !== -1
        );

      }
    );


  // ============================================================
  // FUENTE ENCONTRADA BUSCANDO EL PARTIDO
  // ============================================================

  // Si Tavily la encontró en la búsqueda específica
  // del partido, exigimos que el título mencione
  // a los DOS equipos.

  if (
    fuente.tipoBusqueda === 'PARTIDO'
  ) {

    return (
      mencionaLocal &&
      mencionaVisitante
    );

  }


  // ============================================================
  // FUENTE ENCONTRADA BUSCANDO AL LOCAL
  // ============================================================

  if (
    fuente.tipoBusqueda === 'LOCAL'
  ) {

    return mencionaLocal;

  }


  // ============================================================
  // FUENTE ENCONTRADA BUSCANDO AL VISITANTE
  // ============================================================

  if (
    fuente.tipoBusqueda === 'VISITANTE'
  ) {

    return mencionaVisitante;

  }


  if (
    fuente.tipoBusqueda === 'TRANSMISION'
  ) {

    return (
      mencionaLocal &&
      mencionaVisitante
    );

  }


  // ============================================================
  // OTROS CASOS
  // ============================================================

  return (
    mencionaLocal ||
    mencionaVisitante
  );

}

function normalizarTextoComparacion_(texto) {

  return String(texto || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(
      /[\u0300-\u036f]/g,
      ''
    )
    .replace(
      /[^a-z0-9\s]/g,
      ' '
    )
    .replace(
      /\s+/g,
      ' '
    )
    .trim();

}

/**
 * Alias habituales de prensa para la búsqueda de transmisión.
 * No añade solicitudes a Tavily ni presupone que una plataforma transmita.
 */
function variantesBusquedaTransmision_(nombre) {
  const n = normalizarTextoComparacion_(nombre);
  const aliases = [String(nombre || '').replace(/"/g, '').trim()];
  const mapa = {
    'penarol':['Peñarol','Penarol','CA Peñarol','Club Atlético Peñarol'],
    'bayern munich':['Bayern Munich','Bayern München','Bayern Múnich','FC Bayern'],
    'bayern munchen':['Bayern Munich','Bayern München','Bayern Múnich','FC Bayern'],
    'arsenal':['Arsenal','Arsenal FC'],
    'atletico mineiro':['Atlético Mineiro','Atlético-MG'],
    'atletico mg':['Atlético Mineiro','Atlético-MG'],
    'montevideo city torque':['City Torque','Montevideo City Torque'],
    'city torque':['City Torque','Montevideo City Torque'],
    'inter':['Inter','Inter de Milán','Inter Milan','Internazionale'],
    'internazionale':['Inter de Milán','Inter Milan','Internazionale'],
    'manchester city':['Manchester City','Man City'],
    'manchester united':['Manchester United','Man United']
  };
  (mapa[n] || []).forEach(function(alias) { if (aliases.indexOf(alias) === -1) aliases.push(alias); });
  return '(' + aliases.filter(function(alias) { return !!alias; }).map(function(alias) {
    return '"' + alias + '"';
  }).join(' OR ') + ')';
}

function obtenerVariantesNombreEquipo_(
  nombre
) {

  const variantes = [];


  let limpio =
    String(nombre || '')
      .replace(
        /\buruguay\b/g,
        ''
      )
      .replace(
        /\bfc\b/g,
        ''
      )
      .replace(
        /\bfutbol club\b/g,
        ''
      )
      .replace(
        /\bclub de\b/g,
        ''
      )
      .replace(
        /\bclub\b/g,
        ''
      )
      .replace(
        /\s+/g,
        ' '
      )
      .trim();


  if (nombre) {

    variantes.push(
      String(nombre).trim()
    );

  }


  if (
    limpio &&
    variantes.indexOf(limpio) === -1
  ) {

    variantes.push(
      limpio
    );

  }


  const alias = {
    'brasil': 'brazil', 'brazil': 'brasil',
    'grecia': 'greece', 'greece': 'grecia',
    'alemania': 'germany', 'germany': 'alemania',
    'noruega': 'norway', 'norway': 'noruega',
    'belgica': 'belgium', 'belgium': 'belgica',
    'paises bajos': 'netherlands', 'netherlands': 'paises bajos',
    'japon': 'japan', 'japan': 'japon',
    'corea del sur': 'south korea', 'south korea': 'corea del sur',
    'estados unidos': 'usa', 'usa': 'estados unidos',
    'atletico mineiro': 'atletico mg', 'atletico mg': 'atletico mineiro',
    'clube atletico mineiro': 'atletico mineiro',
    'montevideo city torque': 'city torque', 'city torque': 'montevideo city torque',
    'borussia dortmund': 'dortmund', 'dortmund': 'borussia dortmund',
    'bayern munchen': 'bayern munich', 'bayern munich': 'bayern munchen',
    'bayern munchen fc': 'bayern munich',
    'bayern munchen': 'bayern', 'bayern munich': 'bayern',
    'paris saint germain': 'psg', 'psg': 'paris saint germain',
    'fc barcelona': 'barcelona', 'barcelona': 'fc barcelona',
    'internazionale': 'inter', 'inter milan': 'internazionale',
    'shakhtar donetsk': 'shakhtar', 'shaktar donetsk': 'shakhtar donetsk',
    'rb leipzig': 'leipzig', 'rasenballsport leipzig': 'leipzig'
  };

  [String(nombre || '').trim(), limpio].forEach(function(valor) {
    const equivalente = alias[valor];
    if (equivalente && variantes.indexOf(equivalente) === -1) {
      variantes.push(equivalente);
    }
  });


  // Los títulos deportivos suelen abreviar los nombres oficiales.
  if (/atletico mineiro|clube atletico mineiro|atletico mg/.test(String(nombre || ''))) {
    ['atletico mineiro', 'atletico mg'].forEach(function(v) {
      if (variantes.indexOf(v) === -1) variantes.push(v);
    });
  }
  if (/bayern munchen|bayern munich/.test(String(nombre || ''))) {
    ['bayern munchen', 'bayern munich', 'bayern'].forEach(function(v) {
      if (variantes.indexOf(v) === -1) variantes.push(v);
    });
  }
  if (/city torque|montevideo city/.test(String(nombre || ''))) {
    ['city torque', 'montevideo city torque'].forEach(function(v) {
      if (variantes.indexOf(v) === -1) variantes.push(v);
    });
  }

  return variantes;

}

function consultarGeminiConReintentos_(
  geminiKey,
  geminiPayload
) {

  const url =
    'https://generativelanguage.googleapis.com/v1beta/interactions';


  // Una llamada por actualización manual evita espera larga y reintentos
  // de pago; ante error se devuelve el resumen anterior si existe.
  const maxIntentos = 1;


  for (
    let intento = 1;
    intento <= maxIntentos;
    intento++
  ) {

    Logger.log(
      'Gemini - intento ' +
      intento +
      ' de ' +
      maxIntentos
    );


    let response;


    try {

      response =
        UrlFetchApp.fetch(
          url,
          {

            method: 'post',

            contentType:
              'application/json',

            headers: {
              'x-goog-api-key':
                geminiKey
            },

            payload:
              JSON.stringify(
                geminiPayload
              ),

            muteHttpExceptions:
              true

          }
        );

    }
    catch (error) {

      Logger.log(
        'Error de conexión con Gemini: ' +
        error.message
      );


      if (intento === maxIntentos) {

        throw new Error(
          'No fue posible conectar con Gemini después de ' +
          maxIntentos +
          ' intentos.'
        );

      }


      esperarAntesDeReintentarGemini_(
        intento
      );


      continue;

    }


    const status =
      response.getResponseCode();


    Logger.log(
      'Gemini HTTP: ' +
      status
    );

    const cuerpoErrorGemini = response.getContentText();


    // ==========================================================
    // ÉXITO
    // ==========================================================

    if (status === 200) {

      return JSON.parse(
        response.getContentText()
      );

    }


    // ==========================================================
    // ERRORES TEMPORALES
    // ==========================================================

    // Una cuota diaria agotada no se resuelve reintentando segundos después.
    // Se devuelve inmediatamente el respaldo y se evita demorar al usuario.
    if (
      status === 429 &&
      /per day|daily|requests per day/i.test(cuerpoErrorGemini)
    ) {
      throw new Error('Cuota diaria de Gemini agotada para el modelo configurado.');
    }

    const erroresReintentables = [
      429,
      500,
      502,
      503,
      504
    ];


    if (
      erroresReintentables.indexOf(
        status
      ) !== -1
    ) {

      Logger.log(
        'Gemini devolvió un error temporal.'
      );


      if (intento < maxIntentos) {

        esperarAntesDeReintentarGemini_(
          intento
        );

        continue;

      }

    }


    // ==========================================================
    // ERROR DEFINITIVO
    // ==========================================================

    throw new Error(
      'Error Gemini HTTP ' +
      status +
      ': ' +
      cuerpoErrorGemini
    );

  }


  throw new Error(
    'No fue posible obtener respuesta de Gemini.'
  );

}

function esperarAntesDeReintentarGemini_(
  intento
) {

  let milisegundos;


  if (intento === 1) {

    milisegundos = 2000;

  }
  else {

    milisegundos = 3000;

  }

  Logger.log(
    'Esperando ' +
    (milisegundos / 1000) +
    ' segundos antes de reintentar Gemini...'
  );


  Utilities.sleep(
    milisegundos
  );

}

function obtenerResumenRespaldo_(
  cacheKey
) {

  try {

    const props =
      PropertiesService
        .getScriptProperties();


    const texto =
      props.getProperty(
        'backup_' + cacheKey
      );


    if (!texto) {

      return null;

    }


    const respaldo = JSON.parse(texto);
    if (!respaldo || respaldo.encontrado !== true) return null;
    return respaldo;

  }
  catch (error) {

    Logger.log(
      'No se pudo recuperar respaldo: ' +
      error.message
    );


    return null;

  }

}

function probarOpenAIFree() {
  const apiKey = PropertiesService
    .getScriptProperties()
    .getProperty('OPENAI_API_KEY');

  if (!apiKey) {
    throw new Error('No existe OPENAI_API_KEY');
  }

  const response = UrlFetchApp.fetch(
    'https://api.openai.com/v1/responses',
    {
      method: 'post',
      contentType: 'application/json',
      headers: {
        Authorization: 'Bearer ' + apiKey
      },
      payload: JSON.stringify({
        model: 'gpt-5.4-mini',
        input: 'Respondé únicamente con la palabra OK.'
      }),
      muteHttpExceptions: true
    }
  );

  Logger.log('HTTP: ' + response.getResponseCode());
  Logger.log(response.getContentText());
}

/** Diagnóstico de fuentes, sin búsquedas nuevas ni consumo de Tavily. Requiere token. */
function guardarDiagnosticoTransmision_(partido, originales, filtrados) {
  try {
    const aceptadas = {};
    filtrados.filter(function(r) { return r.tipoBusqueda === 'TRANSMISION'; })
      .forEach(function(r) { aceptadas[String(r.url || '')] = true; });
    const fuentes = originales.filter(function(r) { return r.tipoBusqueda === 'TRANSMISION'; })
      .slice(0, 12).map(function(r) {
        const url = String(r.url || '');
        return {
          titulo: String(r.title || '').substring(0, 180),
          url: url.substring(0, 400),
          extracto: String(r.content || '').substring(0, 450),
          aceptada: !!aceptadas[url],
          motivo: aceptadas[url] ? 'Incluida entre las fuentes seleccionadas; Uruguay se valida por separado' : (function() { const evaluacion = evaluarFuenteTavilyPartido_(r, partido); return evaluacion.aceptada ? 'Válida, pero excluida por límite de fuentes' : evaluacion.motivo; })(),
          fechaPublicacion: String(r.published_date || '')
        };
      });
    const dato = {
      fecha: Utilities.formatDate(new Date(), 'America/Montevideo', 'yyyy-MM-dd HH:mm:ss'),
      fixtureId: String(partido.fixtureId),
      local: partido.local, visitante: partido.visitante,
      fechaPartido: partido.fecha, competicion: partido.competicion,
      totalFuentesTransmision: fuentes.length, fuentes: fuentes
    };
    const props = PropertiesService.getScriptProperties();
    props.setProperty('TRANSMISSION_DIAG_LAST', JSON.stringify(dato).substring(0, 8500));
    props.setProperty('TRANSMISSION_DIAG_' + String(partido.fixtureId).replace(/[^0-9A-Za-z_-]/g, '_').substring(0, 60), JSON.stringify(dato).substring(0, 8500));
  } catch (err) { Logger.log('Diagnóstico transmisión: ' + err.message); }
}
function obtenerDiagnosticoTransmision_(fixtureId) {
  const id = String(fixtureId || '').replace(/[^0-9A-Za-z_-]/g, '_').substring(0, 60);
  const key = id ? 'TRANSMISSION_DIAG_' + id : 'TRANSMISSION_DIAG_LAST';
  const raw = PropertiesService.getScriptProperties().getProperty(key);
  return raw ? {ok:true, action:'transmissionDiagnostic', data:JSON.parse(raw)} :
    {ok:true, action:'transmissionDiagnostic', disponible:false, mensaje:'No hay diagnóstico registrado. Abrí un partido sin caché o pulsá Actualizar información y volvé a consultar.'};
}


/** Control de presupuesto estimado de Tavily, horario de Montevideo.
 * Propiedades opcionales:
 * TAVILY_MONTHLY_CREDITS (por defecto 1000)
 * TAVILY_BASELINE_YYYY_MM (créditos ya consumidos fuera de este contador).
 * TAVILY_ACTIVATION_DATE_YYYY_MM (yyyy-MM-dd; se fija automáticamente al primer uso).
 * Configurar BASELINE antes de la primera consulta para prorratear el saldo real restante.
 * No es el saldo oficial de Tavily: las búsquedas advanced se estiman en 2,
 * las basic en 1. No se cuentan respuestas desde caché.
 */
function estadoPresupuestoTavily_() {
  const props = PropertiesService.getScriptProperties();
  const hoy = new Date();
  const fecha = Utilities.formatDate(hoy, 'America/Montevideo', 'yyyy-MM-dd');
  const partes = fecha.split('-').map(Number);
  const mes = fecha.substring(0, 7).replace('-', '_');
  const dias = new Date(Date.UTC(partes[0], partes[1], 0)).getUTCDate();
  const cuota = Math.max(1, Number(props.getProperty('TAVILY_MONTHLY_CREDITS')) || 1000);
  const base = Math.max(0, Number(props.getProperty('TAVILY_BASELINE_' + mes)) || 0);
  const gastados = Math.max(0, Number(props.getProperty('TAVILY_USED_' + mes)) || 0);
  // En el primer mes de activación, distribuir el saldo restante entre los días
  // restantes (incluido hoy). Después, volver al presupuesto mensual acumulativo.
  // La fecha de activación se fija al configurar el saldo inicial o al primer uso.
  const inicio = props.getProperty('TAVILY_ACTIVATION_DATE_' + mes) || '';
  const inicioValido = /^\d{4}-\d{2}-\d{2}$/.test(inicio) && inicio.substring(0, 7) === fecha.substring(0, 7) && inicio <= fecha;
  const diaInicio = inicioValido ? Number(inicio.substring(8, 10)) : 1;
  const saldoInicial = Math.max(0, cuota - base);
  const acumulado = inicioValido
    ? Math.floor(saldoInicial * (partes[2] - diaInicio + 1) / (dias - diaInicio + 1))
    : Math.floor(cuota * partes[2] / dias) - base;
  const disponibleHoy = Math.max(0, Math.min(cuota - base - gastados, acumulado - gastados));
  return {mes:mes, fecha:fecha, cuotaMensual:cuota, diasMes:dias,
    fechaActivacion:inicioValido ? inicio : null,
    baseExterna:base, creditosRegistrados:gastados, creditosEstimadosConsumidos:base+gastados,
    disponiblesHoy:disponibleHoy, disponiblesMes:Math.max(0,cuota-base-gastados),
    nota:'Estimación local; no consulta el saldo oficial de Tavily.'};
}
/**
 * Ejecutar MANUALMENTE desde el editor de Apps Script, una sola vez.
 * Antes de ejecutar, reemplazar 800 por el consumo mensual REAL que informa Tavily.
 * No se expone como acción HTTP y no modifica claves ni credenciales.
 */
function configurarPresupuestoTavilyInicial() {
  const CUOTA_MENSUAL = 1000;
  const CREDITOS_YA_CONSUMIDOS = 815; // Consumo confirmado por el usuario el 16/10/2026; verificar que no haya cambiado antes de ejecutar.
  return inicializarPresupuestoTavily_(CUOTA_MENSUAL, CREDITOS_YA_CONSUMIDOS);
}

function inicializarPresupuestoTavily_(cuota, consumoPrevio) {
  if (!Number.isSafeInteger(cuota) || cuota <= 0 ||
      !Number.isSafeInteger(consumoPrevio) || consumoPrevio < 0 || consumoPrevio > cuota) {
    throw new Error('La cuota y el consumo inicial deben ser enteros válidos; consumo <= cuota.');
  }
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) throw new Error('No se pudo bloquear la configuración Tavily.');
  try {
    const props = PropertiesService.getScriptProperties();
    const fecha = Utilities.formatDate(new Date(), 'America/Montevideo', 'yyyy-MM-dd');
    const mes = fecha.substring(0, 7).replace('-', '_');
    const claveBase = 'TAVILY_BASELINE_' + mes;
    const claveInicio = 'TAVILY_ACTIVATION_DATE_' + mes;
    if (props.getProperty(claveInicio) !== null || props.getProperty(claveBase) !== null ||
        props.getProperty('TAVILY_USED_' + mes) !== null) {
      throw new Error('El presupuesto de este mes ya está inicializado o registra consumo. No se sobrescribió ningún dato.');
    }
    props.setProperties({
      TAVILY_MONTHLY_CREDITS: String(cuota),
      [claveBase]: String(consumoPrevio),
      [claveInicio]: fecha
    });
    return estadoPresupuestoTavily_();
  } finally {
    lock.releaseLock();
  }
}

function reservarCreditosTavily_(creditos, permitirExceso) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(15000)) throw new Error('No se pudo reservar el presupuesto Tavily; intentá de nuevo.');
  try {
    const props = PropertiesService.getScriptProperties();
    const hoy = Utilities.formatDate(new Date(), 'America/Montevideo', 'yyyy-MM-dd');
    const mes = hoy.substring(0, 7).replace('-', '_');
    const activacionKey = 'TAVILY_ACTIVATION_DATE_' + mes;
    if (!props.getProperty(activacionKey)) props.setProperty(activacionKey, hoy);
    const estado = estadoPresupuestoTavily_();
    // La autorización excepcional solo permite superar el presupuesto diario,
    // nunca la cuota mensual total configurada.
    if (creditos > estado.disponiblesMes) {
      const error = new Error('Se agotó la cuota mensual estimada de Tavily. No se realizarán nuevas búsquedas.');
      error.code = 'TAVILY_MONTHLY_LIMIT';
      error.budget = estado;
      throw error;
    }
    if (!permitirExceso && creditos > estado.disponiblesHoy) {
      const error = new Error('Se alcanzó el presupuesto diario de Tavily. ¿Querés realizar esta consulta igualmente?');
      error.code = 'TAVILY_DAILY_LIMIT';
      error.budget = estado;
      throw error;
    }
    const key = 'TAVILY_USED_' + estado.mes;
    props.setProperty(key, String(estado.creditosRegistrados + creditos));
  } finally {
    lock.releaseLock();
  }
}
