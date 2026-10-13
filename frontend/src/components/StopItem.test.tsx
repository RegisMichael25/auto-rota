import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { StopResponse } from '../api/types'
import { StopItem } from './StopItem'

const stop: StopResponse = {
  visitOrder: 2,
  inputOrder: 1,
  address: { cep: '01310-100', street: 'Avenida Paulista', number: '1000', complement: null, neighborhood: 'Bela Vista', city: 'São Paulo', uf: 'SP', lat: -23.56, lng: -46.65 },
  coordSource: 'NOMINATIM',
  legDistanceM: 3066,
  legDurationS: 420,
  status: 'PENDENTE',
  deliveredAt: null,
  deliveryNote: null,
}

function renderStop() {
  const client = new QueryClient()
  render(
    <QueryClientProvider client={client}>
      <ul><StopItem routeId="r1" stop={stop} /></ul>
    </QueryClientProvider>,
  )
}

describe('StopItem', () => {
  it('mostra ordem informada e trecho anterior', () => {
    renderStop()
    expect(screen.getByText(/3,1 km · 7 min desde o ponto anterior · informado como 1º/)).toBeInTheDocument()
  })

  it('marca como não entregue com observação', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 'r1', stops: [] }), { status: 200 }),
    )
    renderStop()

    await userEvent.click(screen.getByRole('button', { name: 'Adicionar observação' }))
    await userEvent.type(screen.getByLabelText(/observação/i), 'Cliente ausente')
    await userEvent.click(screen.getByRole('button', { name: 'Não entregue' }))

    const [url, init] = fetch.mock.calls[0]
    expect(String(url)).toBe('http://localhost:8080/api/routes/r1/stops/2/fail')
    expect(JSON.parse(String(init!.body))).toEqual({ deliveryNote: 'Cliente ausente' })
  })
})
