import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from './client'
import { api, type StopOutcome } from './routes'
import type { PlanRouteRequest, RouteFilters, RoutePlanResponse } from './types'

export const queryKeys = {
  cep: (cep: string) => ['cep', cep] as const,
  routes: (filters: RouteFilters) => ['routes', filters] as const,
  route: (id: string) => ['route', id] as const,
}

/** Erros 4xx não melhoram com nova tentativa; falhas de rede e 5xx tentam mais uma vez. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 1
}

export function useCep(cepDigits: string) {
  return useQuery({
    queryKey: queryKeys.cep(cepDigits),
    queryFn: () => api.lookupCep(cepDigits),
    enabled: cepDigits.length === 8,
    staleTime: Infinity,
  })
}

export function useRoutes(filters: RouteFilters) {
  return useQuery({
    queryKey: queryKeys.routes(filters),
    queryFn: () => api.listRoutes(filters),
    placeholderData: keepPreviousData,
  })
}

export function useRoute(id: string) {
  return useQuery({ queryKey: queryKeys.route(id), queryFn: () => api.getRoute(id) })
}

function useStoreRoute() {
  const client = useQueryClient()
  return (route: RoutePlanResponse) => {
    client.setQueryData(queryKeys.route(route.id), route)
    return client.invalidateQueries({ queryKey: ['routes'] })
  }
}

export function usePlanRoute() {
  const store = useStoreRoute()
  return useMutation({ mutationFn: (body: PlanRouteRequest) => api.planRoute(body), onSuccess: store })
}

export interface MarkStopInput {
  visitOrder: number
  outcome: StopOutcome
  note?: string
}

export function useMarkStop(routeId: string) {
  const store = useStoreRoute()
  return useMutation({
    mutationFn: ({ visitOrder, outcome, note }: MarkStopInput) => api.markStop(routeId, visitOrder, outcome, note),
    onSuccess: store,
  })
}
