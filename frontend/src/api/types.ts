export type RoutePlanStatus = 'PLANEJADA' | 'EM_ANDAMENTO' | 'CONCLUIDA'
export type StopStatus = 'PENDENTE' | 'ENTREGUE' | 'NAO_ENTREGUE'
export type CoordSource = 'BRASILAPI' | 'NOMINATIM' | 'GOOGLE'

export interface AddressRequest {
  cep: string
  number: string
}

export interface PlanRouteRequest {
  name?: string
  origin: AddressRequest
  destinations: AddressRequest[]
  roundTrip: boolean
  consumptionKmPerL?: number
  fuelPricePerL?: number
}

export interface CepResponse {
  cep: string
  street: string | null
  complement: string | null
  neighborhood: string | null
  city: string
  uf: string
}

export interface AddressResponse extends CepResponse {
  number: string
  lat: number
  lng: number
}

export interface StopResponse {
  visitOrder: number
  inputOrder: number
  address: AddressResponse
  coordSource: CoordSource
  legDistanceM: number
  legDurationS: number
  status: StopStatus
  deliveredAt: string | null
  deliveryNote: string | null
}

export interface LineString {
  type: 'LineString'
  coordinates: [number, number][]
}

export interface RoutePlanResponse {
  id: string
  name: string | null
  createdAt: string
  status: RoutePlanStatus
  roundTrip: boolean
  consumptionKmPerL: number
  fuelPricePerL: number
  totalDistanceM: number
  totalDurationS: number
  fuelLiters: number
  fuelCost: number
  returnDistanceM: number | null
  returnDurationS: number | null
  origin: AddressResponse
  stops: StopResponse[]
  routeGeometry: LineString
}

export interface RouteSummaryResponse {
  id: string
  name: string | null
  createdAt: string
  status: RoutePlanStatus
  roundTrip: boolean
  originCity: string
  originUf: string
  stopCount: number
  deliveredCount: number
  notDeliveredCount: number
  totalDistanceM: number
  totalDurationS: number
  fuelLiters: number
  fuelCost: number
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RouteFilters {
  name?: string
  from?: string
  to?: string
  page?: number
  size?: number
}
