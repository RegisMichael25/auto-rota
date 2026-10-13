import { useId } from 'react'
import { ApiError } from '../api/client'
import { useCep } from '../api/hooks'
import { maskCep, onlyDigits } from '../lib/format'

export interface AddressValue {
  cep: string
  number: string
}

interface Props {
  label: string
  value: AddressValue
  onChange: (value: AddressValue) => void
  errors?: { cep?: string; number?: string }
  onRemove?: () => void
  marker?: string
}

/** CEP + número. Com 8 dígitos, consulta o CEP e mostra o endereço encontrado. */
export function AddressField({ label, value, onChange, errors, onRemove, marker }: Props) {
  const id = useId()
  const digits = onlyDigits(value.cep)
  const cep = useCep(digits)

  let hint: { text: string; tone: 'muted' | 'error' } | null = null
  if (errors?.cep) hint = { text: errors.cep, tone: 'error' }
  else if (digits.length === 8 && cep.isFetching) hint = { text: 'Buscando endereço…', tone: 'muted' }
  else if (cep.error)
    hint = {
      text: cep.error instanceof ApiError && cep.error.status === 404 ? 'CEP não encontrado.' : cep.error.message,
      tone: 'error',
    }
  else if (digits.length === 8 && cep.data) {
    const { street, neighborhood, city, uf } = cep.data
    hint = { text: [street, neighborhood, `${city}/${uf}`].filter(Boolean).join(' · '), tone: 'muted' }
  }

  return (
    <fieldset className="address">
      <legend className="address-legend">
        {marker && <span className="marker marker-inline" aria-hidden="true">{marker}</span>}
        {label}
      </legend>
      <div className="address-inputs">
        <label className="field" htmlFor={`${id}-cep`}>
          <span className="field-label">CEP</span>
          <input
            id={`${id}-cep`}
            inputMode="numeric"
            autoComplete="postal-code"
            placeholder="00000-000"
            value={maskCep(value.cep)}
            aria-invalid={Boolean(errors?.cep) || cep.isError}
            onChange={(e) => onChange({ ...value, cep: maskCep(e.target.value) })}
          />
        </label>
        <label className="field field-number" htmlFor={`${id}-number`}>
          <span className="field-label">Número</span>
          <input
            id={`${id}-number`}
            placeholder="123"
            maxLength={20}
            value={value.number}
            aria-invalid={Boolean(errors?.number)}
            onChange={(e) => onChange({ ...value, number: e.target.value })}
          />
        </label>
        {onRemove && (
          <button type="button" className="button button-ghost address-remove" onClick={onRemove}>
            Remover
          </button>
        )}
      </div>
      {hint && <p className={`hint hint-${hint.tone}`}>{hint.text}</p>}
      {errors?.number && <p className="hint hint-error">{errors.number}</p>}
    </fieldset>
  )
}
