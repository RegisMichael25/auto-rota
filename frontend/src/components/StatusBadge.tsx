import type { RoutePlanStatus, StopStatus } from '../api/types'

const LABELS: Record<RoutePlanStatus | StopStatus, string> = {
  PLANEJADA: 'Planejada',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  PENDENTE: 'Pendente',
  ENTREGUE: 'Entregue',
  NAO_ENTREGUE: 'Não entregue',
}

export function StatusBadge({ status }: { status: RoutePlanStatus | StopStatus }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status]}</span>
}
