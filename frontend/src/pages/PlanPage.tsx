import { useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, type FieldError } from '../api/client'
import { usePlanRoute } from '../api/hooks'
import type { PlanRouteRequest } from '../api/types'
import { AddressField, type AddressValue } from '../components/AddressField'
import { decimalPlaces, onlyDigits, parseDecimal } from '../lib/format'

export const MAX_DESTINATIONS = 10

interface Entry extends AddressValue {
  key: number
}

type Errors = Record<string, string>

const empty = (key: number): Entry => ({ key, cep: '', number: '' })

function validateAddress(prefix: string, value: AddressValue, errors: Errors) {
  if (onlyDigits(value.cep).length !== 8) errors[`${prefix}.cep`] = 'Informe um CEP com 8 dígitos.'
  if (value.number.trim() === '') errors[`${prefix}.number`] = 'Informe o número.'
}

function validateDecimal(field: string, raw: string, intDigits: number, fraction: number, errors: Errors) {
  const value = parseDecimal(raw)
  if (value === undefined) return
  if (Number.isNaN(value) || value <= 0) errors[field] = 'Informe um número maior que zero.'
  else if (decimalPlaces(raw) > fraction || Math.trunc(value).toString().length > intDigits)
    errors[field] = `Use até ${fraction} casas decimais.`
}

function fieldErrorsToMap(fieldErrors: FieldError[]): Errors {
  return Object.fromEntries(fieldErrors.map((e) => [e.field, e.message]))
}

export function PlanPage() {
  const navigate = useNavigate()
  const plan = usePlanRoute()
  const nextKey = useRef(2)

  const [name, setName] = useState('')
  const [origin, setOrigin] = useState<AddressValue>({ cep: '', number: '' })
  const [destinations, setDestinations] = useState<Entry[]>([empty(1)])
  const [roundTrip, setRoundTrip] = useState(true)
  const [consumption, setConsumption] = useState('')
  const [price, setPrice] = useState('')
  const [errors, setErrors] = useState<Errors>({})

  const serverErrors = plan.error instanceof ApiError ? fieldErrorsToMap(plan.error.fieldErrors) : {}
  const allErrors = { ...serverErrors, ...errors }
  const errorFor = (prefix: string) => ({ cep: allErrors[`${prefix}.cep`], number: allErrors[`${prefix}.number`] })

  const addDestination = () => {
    if (destinations.length >= MAX_DESTINATIONS) return
    setDestinations((current) => [...current, empty(nextKey.current++)])
  }

  const updateDestination = (key: number, value: AddressValue) =>
    setDestinations((current) => current.map((d) => (d.key === key ? { ...d, ...value } : d)))

  const removeDestination = (key: number) => setDestinations((current) => current.filter((d) => d.key !== key))

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const found: Errors = {}
    validateAddress('origin', origin, found)
    destinations.forEach((d, i) => validateAddress(`destinations[${i}]`, d, found))
    validateDecimal('consumptionKmPerL', consumption, 4, 2, found)
    validateDecimal('fuelPricePerL', price, 5, 3, found)
    setErrors(found)
    if (Object.keys(found).length > 0) return

    const body: PlanRouteRequest = {
      name: name.trim() || undefined,
      origin: { cep: onlyDigits(origin.cep), number: origin.number.trim() },
      destinations: destinations.map((d) => ({ cep: onlyDigits(d.cep), number: d.number.trim() })),
      roundTrip,
      consumptionKmPerL: parseDecimal(consumption),
      fuelPricePerL: parseDecimal(price),
    }
    plan.mutate(body, { onSuccess: (route) => navigate(`/rotas/${route.id}`) })
  }

  return (
    <main className="page page-narrow">
      <h1 className="title">Planejar rota</h1>
      <p className="muted lead">
        Informe de onde sai a entrega e até {MAX_DESTINATIONS} destinos. A ordem de visita é calculada pela menor
        distância de estrada.
      </p>

      <form className="stack" onSubmit={onSubmit} noValidate>
        <label className="field">
          <span className="field-label">Nome da rota (opcional)</span>
          <input value={name} maxLength={120} placeholder="Ex.: Entregas da manhã" onChange={(e) => setName(e.target.value)} />
        </label>

        <AddressField label="Origem (sua loja)" marker="S" value={origin} onChange={setOrigin} errors={errorFor('origin')} />

        <section className="stack-tight" aria-labelledby="destinations-title">
          <div className="section-head">
            <h2 id="destinations-title" className="subtitle">Destinos</h2>
            <span className="muted">
              {destinations.length} de {MAX_DESTINATIONS}
            </span>
          </div>
          {destinations.map((d, i) => (
            <AddressField
              key={d.key}
              label={`Destino ${i + 1}`}
              value={d}
              onChange={(value) => updateDestination(d.key, value)}
              onRemove={destinations.length > 1 ? () => removeDestination(d.key) : undefined}
              errors={errorFor(`destinations[${i}]`)}
            />
          ))}
          <div>
            <button
              type="button"
              className="button button-secondary"
              onClick={addDestination}
              disabled={destinations.length >= MAX_DESTINATIONS}
            >
              Adicionar destino
            </button>
          </div>
        </section>

        <label className="switch">
          <input type="checkbox" role="switch" checked={roundTrip} onChange={(e) => setRoundTrip(e.target.checked)} />
          <span>
            Retornar à origem
            <span className="muted block">
              {roundTrip ? 'A rota termina na loja.' : 'A rota termina na última entrega.'}
            </span>
          </span>
        </label>

        <div className="grid-2">
          <label className="field">
            <span className="field-label">Consumo (km/l)</span>
            <input inputMode="decimal" placeholder="Padrão do sistema" value={consumption} aria-invalid={Boolean(allErrors.consumptionKmPerL)} onChange={(e) => setConsumption(e.target.value)} />
            {allErrors.consumptionKmPerL && <span className="hint hint-error">{allErrors.consumptionKmPerL}</span>}
          </label>
          <label className="field">
            <span className="field-label">Preço do litro (R$)</span>
            <input inputMode="decimal" placeholder="Padrão do sistema" value={price} aria-invalid={Boolean(allErrors.fuelPricePerL)} onChange={(e) => setPrice(e.target.value)} />
            {allErrors.fuelPricePerL && <span className="hint hint-error">{allErrors.fuelPricePerL}</span>}
          </label>
        </div>

        {plan.error && Object.keys(serverErrors).length === 0 && (
          <div className="alert" role="alert">
            {plan.error.message}
          </div>
        )}

        <div className="form-actions">
          <button type="submit" className="button button-primary button-large" disabled={plan.isPending}>
            {plan.isPending ? 'Calculando rota…' : 'Calcular rota'}
          </button>
          {plan.isPending && (
            <span className="muted">Cada endereço novo é localizado no mapa; pode levar alguns segundos.</span>
          )}
        </div>
      </form>
    </main>
  )
}
