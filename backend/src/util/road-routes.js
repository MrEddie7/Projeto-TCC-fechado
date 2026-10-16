// ─────────────────────────────────────────────────────────────────────────
// Rotas viárias reais de São Paulo usadas pelo simulador.
//
// Cada rota é um array de waypoints [lat, lng] extraídos de vias reais.
// O simulador percorre os segmentos em loop, interpolando a posição entre
// waypoints consecutivos de acordo com a velocidade de cada trecho.
//
// Vias incluídas (cobertura ~50 km de malha urbana):
//   1. Av. Paulista – Consolação – Rebouças – Pinheiros (leste→oeste)
//   2. Av. Brasil – Ipiranga – Dom Pedro II – Mercado – Brás (anel norte)
//   3. Marginal Pinheiros (norte→sul)
//   4. Marginal Tietê (leste→oeste)
//   5. Av. Berrini – Nações Unidas – Faria Lima (corredor financeiro)
//   6. Av. Radial Leste – Celso Garcia (zona leste)
//   7. Av. Santo Amaro – Morumbi – Cidade Jardim (zona sul)
//   8. Av. Vital Brasil – Butantã – USP (zona oeste)
// ─────────────────────────────────────────────────────────────────────────

// Velocidade típica de cada rota em km/h (usada para interpolar os pontos).
// O simulador pode variar ±20% para simular tráfego.
const ROUTES = [
  {
    // 1. Av. Paulista → Consolação → Rebouças → Pinheiros
    name: 'Av. Paulista – Pinheiros',
    speedKmh: 40,
    points: [
      [-23.5613, -46.6558], // Paulista × Pamplona
      [-23.5617, -46.6590], // Paulista × Brigadeiro
      [-23.5621, -46.6627], // Paulista × Paraíso
      [-23.5625, -46.6655], // Paulista × Trianon
      [-23.5629, -46.6683], // Paulista × Brasil
      [-23.5634, -46.6714], // Paulista × Consolação
      [-23.5577, -46.6735], // Consolação (subindo)
      [-23.5527, -46.6745], // Consolação × Marquês de Itu
      [-23.5489, -46.6749], // Consolação × Bela Cintra
      [-23.5460, -46.6754], // Consolação × Haddock Lobo
      [-23.5430, -46.6760], // Consolação × Peixoto Gomide
      [-23.5407, -46.6766], // Rebouças (início)
      [-23.5397, -46.6800], // Rebouças × Theodoro Sampaio
      [-23.5390, -46.6838], // Rebouças × Henrique Schaumann
      [-23.5383, -46.6870], // Rebouças × Pedroso de Moraes
      [-23.5378, -46.6902], // Rebouças × Groenlândia
      [-23.5374, -46.6930], // Rebouças × Europa
      [-23.5371, -46.6960], // Rebouças × Bandeira
      [-23.5370, -46.6988], // Rebouças × Eusébio Matoso
      [-23.5369, -46.7010], // Pinheiros (chegada)
    ],
  },
  {
    // 2. Av. Ipiranga → Dom Pedro II → Mercado Municipal (anel norte)
    name: 'Ipiranga – Mercado Municipal',
    speedKmh: 35,
    points: [
      [-23.5449, -46.6388], // Ipiranga × Rep. do Líbano
      [-23.5437, -46.6408], // Ipiranga × Aurora
      [-23.5425, -46.6425], // Ipiranga × Duque de Caxias
      [-23.5413, -46.6440], // Ipiranga × Amaral Gurgel
      [-23.5400, -46.6453], // Ipiranga × Maranhão
      [-23.5387, -46.6467], // Ipiranga × Riachuelo
      [-23.5371, -46.6485], // Dom Pedro II (início)
      [-23.5355, -46.6473], // Dom Pedro II × Mauá
      [-23.5338, -46.6462], // Dom Pedro II × 25 de Março
      [-23.5325, -46.6455], // Dom Pedro II × Florêncio de Abreu
      [-23.5315, -46.6450], // Mercado Municipal (chegada)
      [-23.5325, -46.6455], // volta
      [-23.5338, -46.6462],
      [-23.5355, -46.6473],
      [-23.5371, -46.6485],
      [-23.5387, -46.6467],
      [-23.5400, -46.6453],
      [-23.5413, -46.6440],
      [-23.5425, -46.6425],
      [-23.5437, -46.6408],
    ],
  },
  {
    // 3. Marginal Pinheiros – trecho sul→norte
    name: 'Marginal Pinheiros',
    speedKmh: 70,
    points: [
      [-23.6195, -46.6988], // Brooklin Novo (sul)
      [-23.6107, -46.6986], // Chucri Zaidan
      [-23.6019, -46.6970], // Bandeirantes (confluência)
      [-23.5941, -46.6956], // Santo Amaro (acesso)
      [-23.5862, -46.6935], // Berrini
      [-23.5784, -46.6915], // Faria Lima
      [-23.5706, -46.6898], // Eusébio Matoso
      [-23.5630, -46.6882], // Pinheiros (Teodoro Sampaio)
      [-23.5554, -46.6865], // Cardeal Arcoverde
      [-23.5480, -46.6850], // Sumaré
      [-23.5406, -46.6835], // Pompeia
      [-23.5333, -46.6821], // Lapa
      [-23.5260, -46.6808], // Ceasa
      [-23.5190, -46.6795], // Presidente Altino
    ],
  },
  {
    // 4. Marginal Tietê – trecho leste→oeste (sentido Osasco)
    name: 'Marginal Tietê',
    speedKmh: 75,
    points: [
      [-23.5159, -46.6143], // Guarulhos (acesso leste)
      [-23.5157, -46.6243], // Penha
      [-23.5155, -46.6343], // Carandiru
      [-23.5153, -46.6443], // Santana
      [-23.5152, -46.6543], // Limão
      [-23.5151, -46.6643], // Mandaqui / Freguesia do Ó
      [-23.5150, -46.6743], // Jaguaré
      [-23.5150, -46.6843], // Pres. Altino
      [-23.5149, -46.6943], // Osasco (chegada)
      [-23.5150, -46.6843],
      [-23.5150, -46.6743],
      [-23.5151, -46.6643],
      [-23.5152, -46.6543],
      [-23.5153, -46.6443],
      [-23.5155, -46.6343],
      [-23.5157, -46.6243],
    ],
  },
  {
    // 5. Berrini → Nações Unidas → Faria Lima (corredor financeiro)
    name: 'Corredor Faria Lima – Berrini',
    speedKmh: 45,
    points: [
      [-23.5975, -46.6858], // Berrini (sul)
      [-23.5934, -46.6866], // Nações Unidas × JK
      [-23.5893, -46.6874], // Nações Unidas × Hélio Pellegrino
      [-23.5852, -46.6882], // Nações Unidas × Bandeirantes
      [-23.5810, -46.6843], // Faria Lima × Pedroso de Moraes
      [-23.5770, -46.6804], // Faria Lima × Leôncio de Carvalho
      [-23.5729, -46.6765], // Faria Lima × Cardeal Arcoverde
      [-23.5689, -46.6727], // Faria Lima × João Moura
      [-23.5648, -46.6688], // Faria Lima × Rebouças
      [-23.5608, -46.6650], // Faria Lima × Horácio Lafer
      [-23.5648, -46.6688],
      [-23.5689, -46.6727],
      [-23.5729, -46.6765],
      [-23.5770, -46.6804],
      [-23.5810, -46.6843],
      [-23.5852, -46.6882],
      [-23.5893, -46.6874],
      [-23.5934, -46.6866],
    ],
  },
  {
    // 6. Av. Celso Garcia → Radial Leste (zona leste)
    name: 'Radial Leste – Celso Garcia',
    speedKmh: 38,
    points: [
      [-23.5494, -46.6108], // Brás (início)
      [-23.5510, -46.6058], // Celso Garcia × Bresser
      [-23.5526, -46.6008], // Celso Garcia × Javari
      [-23.5542, -46.5958], // Celso Garcia × Tatuapé
      [-23.5558, -46.5908], // Celso Garcia × Água Rasa
      [-23.5574, -46.5858], // Celso Garcia × São Miguel
      [-23.5590, -46.5808], // Celso Garcia × Moóca
      [-23.5574, -46.5858],
      [-23.5558, -46.5908],
      [-23.5542, -46.5958],
      [-23.5526, -46.6008],
      [-23.5510, -46.6058],
    ],
  },
  {
    // 7. Av. Santo Amaro → Morumbi → Cidade Jardim (zona sul)
    name: 'Santo Amaro – Morumbi',
    speedKmh: 42,
    points: [
      [-23.6302, -46.6782], // Santo Amaro (início)
      [-23.6252, -46.6793], // Santo Amaro × Comendador Souza
      [-23.6202, -46.6804], // Santo Amaro × Washington Luís
      [-23.6152, -46.6815], // Santo Amaro × Bandeirantes
      [-23.6102, -46.6826], // Morumbi × Escola de Engenharia
      [-23.6052, -46.6838], // Morumbi × Rouxinol
      [-23.6002, -46.6849], // Cidade Jardim (chegada)
      [-23.6052, -46.6838],
      [-23.6102, -46.6826],
      [-23.6152, -46.6815],
      [-23.6202, -46.6804],
      [-23.6252, -46.6793],
    ],
  },
  {
    // 8. Av. Vital Brasil → Butantã → USP (zona oeste)
    name: 'Vital Brasil – USP',
    speedKmh: 35,
    points: [
      [-23.5598, -46.7182], // Pinheiros (início)
      [-23.5620, -46.7228], // Vital Brasil × Faria Lima
      [-23.5642, -46.7274], // Vital Brasil × Butantã
      [-23.5664, -46.7320], // Vital Brasil × Corifeu de Azevedo
      [-23.5686, -46.7366], // Vital Brasil × USP (Portaria 1)
      [-23.5708, -46.7412], // Av. Prof. Luciano Gualberto
      [-23.5730, -46.7458], // Cidade Universitária (centro)
      [-23.5708, -46.7412],
      [-23.5686, -46.7366],
      [-23.5664, -46.7320],
      [-23.5642, -46.7274],
      [-23.5620, -46.7228],
    ],
  },
];

export const ROUTE_COUNT = ROUTES.length;

/**
 * Retorna a rota pelo índice (ciclicamente).
 */
export function getRoute(index) {
  return ROUTES[index % ROUTES.length];
}

/**
 * Calcula a distância em metros entre dois pontos (Haversine simplificado).
 * Duplicado aqui para não criar dependência circular com geo.js.
 */
function distMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/**
 * Calcula o ângulo de curso (heading) entre dois pontos em graus (0=norte).
 */
function bearingDeg(lat1, lng1, lat2, lng2) {
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const φ1 = (lat1 * Math.PI) / 180;
  const φ2 = (lat2 * Math.PI) / 180;
  const y = Math.sin(dLng) * Math.cos(φ2);
  const x =
    Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(dLng);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

/**
 * Estado de posicionamento ao longo de uma rota.
 * @typedef {{ routeIndex: number, waypointIndex: number, progress: number }} RouteState
 *
 * progress: fração [0,1] do segmento atual já percorrida.
 */

/**
 * Cria um estado inicial para um veículo, ancorando-o ao waypoint mais próximo
 * da posição atual (lat, lng), na rota indicada por routeIndex.
 */
export function createRouteState(lat, lng, routeIndex = 0) {
  const route = getRoute(routeIndex);
  let bestIdx = 0;
  let bestDist = Infinity;
  for (let i = 0; i < route.points.length; i++) {
    const d = distMeters(lat, lng, route.points[i][0], route.points[i][1]);
    if (d < bestDist) {
      bestDist = d;
      bestIdx = i;
    }
  }
  return { routeIndex, waypointIndex: bestIdx, progress: 0 };
}

/**
 * Avança o veículo ao longo da rota por `distanceMeters` metros.
 * Modifica `state` in-place e retorna { lat, lng, heading, speed }.
 *
 * @param {RouteState} state
 * @param {number} distanceMeters  metros a percorrer neste tick
 * @param {number} speedKmhOverride  velocidade efetiva (pode diferir da nominal)
 */
export function advanceOnRoute(state, distanceMeters, speedKmhOverride) {
  const route = getRoute(state.routeIndex);
  const pts = route.points;
  let remaining = distanceMeters;

  while (remaining > 0) {
    const nextIdx = (state.waypointIndex + 1) % pts.length;
    const segLat1 = pts[state.waypointIndex][0];
    const segLng1 = pts[state.waypointIndex][1];
    const segLat2 = pts[nextIdx][0];
    const segLng2 = pts[nextIdx][1];
    const segLen = distMeters(segLat1, segLng1, segLat2, segLng2);

    // Metros disponíveis no segmento atual a partir de progress.
    const available = segLen * (1 - state.progress);

    if (remaining <= available) {
      state.progress += remaining / segLen;
      remaining = 0;
    } else {
      remaining -= available;
      state.progress = 0;
      state.waypointIndex = nextIdx;
    }
  }

  // Posição interpolada dentro do segmento atual.
  const cur = pts[state.waypointIndex];
  const nxt = pts[(state.waypointIndex + 1) % pts.length];
  const t = state.progress;
  const lat = cur[0] + (nxt[0] - cur[0]) * t;
  const lng = cur[1] + (nxt[1] - cur[1]) * t;
  const heading = bearingDeg(cur[0], cur[1], nxt[0], nxt[1]);

  return { lat, lng, heading, speed: speedKmhOverride ?? route.speedKmh };
}

/**
 * Escolhe uma rota aleatória, distribuindo os veículos pela malha.
 */
export function randomRouteIndex() {
  return Math.floor(Math.random() * ROUTE_COUNT);
}
