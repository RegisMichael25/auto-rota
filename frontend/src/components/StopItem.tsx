import { useState } from 'react'
import { useMarkStop } from '../api/hooks'
import type { StopOutcome } from '../api/routes'
import type { StopResponse } from '../api/types'
import { addressLine, formatDateTime, formatDistance, formatDuration } from '../lib/format'
import { StatusBadge } from './StatusBadge'

interface Props {
  routeId: string
  stop: StopResponse
}

export function StopItem({ routeId, stop }: Props) {
  const mark = useMarkStop(routeId)
  const [note, setNote] = useState(stop.deliveryNote ?? '')
  const [editingNote, setEditingNote] = useState(false)
  const { address } = stop

  const submit = (outcome: StopOutcome) =>
    mark.mutate({ visitOrder: stop.visitOrder, outcome, note }, { onSuccess: () => setEditingNote(false) })

  const pending = mark.isPending ? mark.variables?.outcome : undefined

  return (
    <li className="stop">
      <span className={`marker marker-${stop.status.toLowerCase()}`} aria-hidden="true">
        {stop.visitOrder}
      </span>
      <div className="stop-body">
        <div className="stop-head">
          <h3 className="stop-title">{addressLine(address)}</h3>
          <StatusBadge status={stop.status} />
        </div>
        <p className="muted">
          {[address.neighborhood, `${address.city}/${address.uf}`].filter(Boolean).join(' · ')} · CEP {address.cep}
        </p>
        <p className="muted">
          {formatDistance(stop.legDistanceM)} · {formatDuration(stop.legDurationS)} desde o ponto anterior ·
          informado como {stop.inputOrder}º
        </p>

        {stop.status !== 'PENDENTE' && (stop.deliveredAt || stop.deliveryNote) && !editingNote && (
          <p className="stop-note">
            {stop.deliveredAt && <>Entregue em {formatDateTime(stop.deliveredAt)}. </>}
            {stop.deliveryNote && <>“{stop.deliveryNote}”</>}
          </p>
        )}

        {editingNote && (
          <label className="field">
            <span className="field-label">Observação</span>
            <textarea
              rows={2}
              maxLength={500}
              value={note}
              placeholder="Ex.: deixado com o porteiro"
              onChange={(e) => setNote(e.target.value)}
            />
            <span className="hint hint-muted">Salva ao marcar Entregue ou Não entregue.</span>
          </label>
        )}

        <div className="stop-actions">
          <button
            type="button"
            className="button button-primary"
            aria-pressed={stop.status === 'ENTREGUE'}
            disabled={mark.isPending}
            onClick={() => submit('deliver')}
          >
            {pending === 'deliver' ? 'Salvando…' : 'Entregue'}
          </button>
          <button
            type="button"
            className="button button-secondary"
            aria-pressed={stop.status === 'NAO_ENTREGUE'}
            disabled={mark.isPending}
            onClick={() => submit('fail')}
          >
            {pending === 'fail' ? 'Salvando…' : 'Não entregue'}
          </button>
          {!editingNote && (
            <button type="button" className="button button-ghost" onClick={() => setEditingNote(true)}>
              {stop.deliveryNote ? 'Editar observação' : 'Adicionar observação'}
            </button>
          )}
        </div>
        {mark.error && <p className="hint hint-error" role="alert">{mark.error.message}</p>}
      </div>
    </li>
  )
}
