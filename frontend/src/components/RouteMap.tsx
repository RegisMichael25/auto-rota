import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { useEffect, useMemo } from 'react'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet'
import type { RoutePlanResponse, StopStatus } from '../api/types'
import { addressLine } from '../lib/format'

type LatLng = [number, number]

function markerIcon(label: string, variant: 'origin' | StopStatus) {
  return L.divIcon({
    className: '',
    html: `<span class="marker marker-${variant.toLowerCase()}">${label}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
    popupAnchor: [0, -14],
  })
}

function FitBounds({ points }: { points: LatLng[] }) {
  const map = useMap()
  useEffect(() => {
    if (points.length > 0) map.fitBounds(L.latLngBounds(points), { padding: [40, 40] })
  }, [map, points])
  return null
}

export function RouteMap({ route }: { route: RoutePlanResponse }) {
  const line = useMemo<LatLng[]>(
    () => route.routeGeometry?.coordinates?.map(([lng, lat]) => [lat, lng]) ?? [],
    [route.routeGeometry],
  )
  const points = useMemo<LatLng[]>(
    () => [[route.origin.lat, route.origin.lng], ...route.stops.map((s): LatLng => [s.address.lat, s.address.lng]), ...line],
    [route.origin, route.stops, line],
  )

  return (
    <MapContainer className="map" center={[route.origin.lat, route.origin.lng]} zoom={13} scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {line.length > 1 && <Polyline positions={line} pathOptions={{ className: 'route-line' }} />}
      <Marker position={[route.origin.lat, route.origin.lng]} icon={markerIcon('S', 'origin')}>
        <Popup>
          Saída: {addressLine(route.origin)}
          <br />
          {route.origin.city}/{route.origin.uf}
        </Popup>
      </Marker>
      {route.stops.map((stop) => (
        <Marker
          key={stop.visitOrder}
          position={[stop.address.lat, stop.address.lng]}
          icon={markerIcon(String(stop.visitOrder), stop.status)}
          zIndexOffset={100 + stop.visitOrder}
        >
          <Popup>
            {stop.visitOrder}ª parada: {addressLine(stop.address)}
            <br />
            {stop.address.city}/{stop.address.uf}
          </Popup>
        </Marker>
      ))}
      <FitBounds points={points} />
    </MapContainer>
  )
}
