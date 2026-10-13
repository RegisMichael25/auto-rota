import { request } from './client'
import type {
  CepResponse,
  PageResponse,
  PlanRouteRequest,
  RouteFilters,
  RoutePlanResponse,
  RouteSummaryResponse,
} from './types'

export type StopOutcome = 'deliver' | 'fail'

export const api = {
  lookupCep: (cep: string) => request<CepResponse>('/api/cep/' + encodeURIComponent(cep)),

  planRoute: (body: PlanRouteRequest) =>
    request<RoutePlanResponse>('/api/routes/plan', { method: 'POST', body: JSON.stringify(body) }),

  listRoutes: (filters: RouteFilters) =>
    request<PageResponse<RouteSummaryResponse>>('/api/routes', { query: { ...filters } }),

  getRoute: (id: string) => request<RoutePlanResponse>('/api/routes/' + encodeURIComponent(id)),

  markStop: (id: string, visitOrder: number, outcome: StopOutcome, deliveryNote?: string) =>
    request<RoutePlanResponse>(`/api/routes/${encodeURIComponent(id)}/stops/${visitOrder}/${outcome}`, {
      method: 'POST',
      body: JSON.stringify({ deliveryNote: deliveryNote?.trim() || null }),
    }),
}
