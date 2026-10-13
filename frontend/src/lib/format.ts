const TIME_ZONE = 'America/Sao_Paulo'

const number = (value: number, digits: number) =>
  value.toLocaleString('pt-BR', { minimumFractionDigits: digits, maximumFractionDigits: digits })

export function formatDistance(meters: number): string {
  if (meters < 1000) return `${Math.round(meters)} m`
  return `${number(meters / 1000, 1)} km`
}

export function formatDuration(seconds: number): string {
  const totalMinutes = Math.round(seconds / 60)
  if (totalMinutes < 60) return `${totalMinutes} min`
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return minutes === 0 ? `${hours} h` : `${hours} h ${String(minutes).padStart(2, '0')} min`
}

export function formatMoney(value: number): string {
  return value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

/** Preço por litro mantém até 3 casas (R$ 6,199), como nas bombas. */
export function formatPricePerLiter(value: number): string {
  return `${value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL', minimumFractionDigits: 2, maximumFractionDigits: 3 })}/l`
}

export function formatLiters(value: number): string {
  return `${number(value, 2)} L`
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('pt-BR', { timeZone: TIME_ZONE, dateStyle: 'short', timeStyle: 'short' })
}

export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '')
}

/** Máscara 00000-000 enquanto o usuário digita. */
export function maskCep(value: string): string {
  const digits = onlyDigits(value).slice(0, 8)
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits
}

/** Aceita vírgula ou ponto como separador decimal. Vazio → undefined; inválido → NaN. */
export function parseDecimal(value: string): number | undefined {
  const trimmed = value.trim()
  if (trimmed === '') return undefined
  if (!/^\d+([.,]\d+)?$/.test(trimmed)) return Number.NaN
  return Number(trimmed.replace(',', '.'))
}

export function decimalPlaces(value: string): number {
  const match = value.trim().match(/[.,](\d+)$/)
  return match ? match[1].length : 0
}

export function addressLine(address: { street: string | null; number?: string; city: string; uf: string }): string {
  const street = address.street ? (address.number ? `${address.street}, ${address.number}` : address.street) : null
  return street ?? `${address.city}/${address.uf}`
}
