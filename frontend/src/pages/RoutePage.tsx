import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useRoute } from '../api/hooks'
import type { RoutePlanResponse } from '../api/types'
import { RouteMap } from '../components/RouteMap'
import { StatusBadge } from '../components/StatusBadge'
import { StopItem } from '../components/StopItem'
import {
  addressLine,
  formatDateTime,
  formatDistance,
  formatDuration,
  formatLiters,
  formatMoney,
  formatPricePerLiter,
} from '../lib/format'

type Order = 'visit' | 'input'

export function RoutePage() {
  const { id = '' } = useParams()
  const route = useRoute(id)

  if (route.isPending) return <main className="page"><p className="muted">Carregando rota…</p></main>
  if (route.isError)
    return (
      <main className="page page-narrow">
        <div className="alert" role="alert">{route.error.message}</div>
        <p><Link to="/rotas">Voltar ao histórico</Link></p>
      </main>
    )
  return <RouteDetail route={route.data} />
}

function RouteDetail({ route }: { route: RoutePlanResponse }) {
  const [order, setOrder] = useState<Order>('visit')
  const stops = [...route.stops].sort((a, b) =>
    order === 'visit' ? a.visitOrder - b.visitOrder : a.inputOrder - b.inputOrder,
  )
  const done = route.stops.filter((s) => s.status !== 'PENDENTE').length

  return (
    <main className="split">
      <section className="panel" aria-label="Detalhes da rota">
        <div className="stack">
          <div>
            <Link to="/rotas" className="back">← Histórico</Link>
            <div className="section-head">
              <h1 className="title">{route.name || 'Rota sem nome'}</h1>
              <StatusBadge status={route.status} />
            </div>
            <p className="muted">
              Criada em {formatDateTime(route.createdAt)} · {done} de {route.stops.length} paradas marcadas
            </p>
          </div>

          <dl className="totals">
            <div><dt>Distância</dt><dd>{formatDistance(route.totalDistanceM)}</dd></div>
            <div><dt>Tempo</dt><dd>{formatDuration(route.totalDurationS)}</dd></div>
            <div><dt>Combustível</dt><dd>{formatLiters(route.fuelLiters)}</dd></div>
            <div><dt>Custo</dt><dd>{formatMoney(route.fuelCost)}</dd></div>
          </dl>
          <p className="muted">
            {route.roundTrip ? 'Circular (volta à origem)' : 'Aberta (termina na última parada)'} ·{' '}
            {route.consumptionKmPerL.toLocaleString('pt-BR')} km/l · {formatPricePerLiter(route.fuelPricePerL)}
          </p>

          <div className="section-head">
            <h2 className="subtitle">Paradas</h2>
            <div className="segmented" role="group" aria-label="Ordenar paradas">
              <button type="button" aria-pressed={order === 'visit'} onClick={() => setOrder('visit')}>Ordem de visita</button>
              <button type="button" aria-pressed={order === 'input'} onClick={() => setOrder('input')}>Ordem informada</button>
            </div>
          </div>

          <ol className="stops">
            {order === 'visit' && (
              <li className="stop stop-origin">
                <span className="marker marker-origin" aria-hidden="true">S</span>
                <div className="stop-body">
                  <h3 className="stop-title">Saída: {addressLine(route.origin)}</h3>
                  <p className="muted">{[route.origin.neighborhood, `${route.origin.city}/${route.origin.uf}`].filter(Boolean).join(' · ')}</p>
                </div>
              </li>
            )}
            {stops.map((stop) => (
              <StopItem key={stop.visitOrder} routeId={route.id} stop={stop} />
            ))}
            {order === 'visit' && route.roundTrip && route.returnDistanceM != null && (
              <li className="stop stop-origin">
                <span className="marker marker-origin" aria-hidden="true">S</span>
                <div className="stop-body">
                  <h3 className="stop-title">Volta à origem</h3>
                  <p className="muted">
                    {formatDistance(route.returnDistanceM)} · {formatDuration(route.returnDurationS ?? 0)} desde a última parada
                  </p>
                </div>
              </li>
            )}
          </ol>
        </div>
      </section>
      <section className="map-wrap" aria-label="Mapa da rota">
        <RouteMap route={route} />
      </section>
    </main>
  )
}
