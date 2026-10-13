import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useRoutes } from '../api/hooks'
import type { RouteFilters } from '../api/types'
import { StatusBadge } from '../components/StatusBadge'
import { formatDateTime, formatDistance, formatMoney } from '../lib/format'

const PAGE_SIZE = 20

export function HistoryPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const filters: RouteFilters = {
    name: params.get('name') ?? undefined,
    from: params.get('from') ?? undefined,
    to: params.get('to') ?? undefined,
    page: Number(params.get('page') ?? 0),
    size: PAGE_SIZE,
  }
  const [draft, setDraft] = useState({ name: filters.name ?? '', from: filters.from ?? '', to: filters.to ?? '' })
  const routes = useRoutes(filters)

  const apply = (event: FormEvent) => {
    event.preventDefault()
    const next = new URLSearchParams()
    if (draft.name.trim()) next.set('name', draft.name.trim())
    if (draft.from) next.set('from', draft.from)
    if (draft.to) next.set('to', draft.to)
    setParams(next)
  }

  const goToPage = (page: number) => {
    const next = new URLSearchParams(params)
    next.set('page', String(page))
    setParams(next)
  }

  const hasFilters = Boolean(filters.name || filters.from || filters.to)
  const data = routes.data

  return (
    <main className="page">
      <div className="section-head">
        <h1 className="title">Histórico de rotas</h1>
        <Link to="/" className="button button-primary">Planejar rota</Link>
      </div>

      <form className="filters" onSubmit={apply}>
        <label className="field">
          <span className="field-label">Nome</span>
          <input value={draft.name} placeholder="Trecho do nome" onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        </label>
        <label className="field">
          <span className="field-label">De</span>
          <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </label>
        <label className="field">
          <span className="field-label">Até</span>
          <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </label>
        <div className="filters-actions">
          <button type="submit" className="button button-secondary">Filtrar</button>
          {hasFilters && (
            <button
              type="button"
              className="button button-ghost"
              onClick={() => {
                setDraft({ name: '', from: '', to: '' })
                setParams(new URLSearchParams())
              }}
            >
              Limpar
            </button>
          )}
        </div>
      </form>

      {routes.isPending && <p className="muted">Carregando…</p>}
      {routes.isError && <div className="alert" role="alert">{routes.error.message}</div>}

      {data && data.content.length === 0 && (
        <div className="empty">
          <p>{hasFilters ? 'Nenhuma rota com esses filtros.' : 'Nenhuma rota planejada ainda.'}</p>
          {!hasFilters && <Link to="/">Planejar a primeira rota</Link>}
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <div className="table-wrap" aria-busy={routes.isFetching}>
            <table className="table">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Nome</th>
                  <th>Origem</th>
                  <th className="num">Entregas</th>
                  <th className="num">Distância</th>
                  <th className="num">Custo</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((r) => (
                  <tr key={r.id} className="row-link" onClick={() => navigate(`/rotas/${r.id}`)}>
                    <td className="nowrap">{formatDateTime(r.createdAt)}</td>
                    <td><Link to={`/rotas/${r.id}`} onClick={(e) => e.stopPropagation()}>{r.name || 'Rota sem nome'}</Link></td>
                    <td>{r.originCity}/{r.originUf}</td>
                    <td className="num">{r.deliveredCount} de {r.stopCount}</td>
                    <td className="num">{formatDistance(r.totalDistanceM)}</td>
                    <td className="num">{formatMoney(r.fuelCost)}</td>
                    <td><StatusBadge status={r.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <nav className="pager" aria-label="Paginação">
              <button type="button" className="button button-ghost" disabled={data.page === 0} onClick={() => goToPage(data.page - 1)}>Anterior</button>
              <span className="muted">Página {data.page + 1} de {data.totalPages}</span>
              <button type="button" className="button button-ghost" disabled={data.page + 1 >= data.totalPages} onClick={() => goToPage(data.page + 1)}>Próxima</button>
            </nav>
          )}
        </>
      )}
    </main>
  )
}
