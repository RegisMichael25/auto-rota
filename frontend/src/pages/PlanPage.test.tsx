import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { MAX_DESTINATIONS, PlanPage } from './PlanPage'

const ok = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<PlanPage />} />
          <Route path="/rotas/:id" element={<p>detalhe da rota</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function mockApi() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input)
    if (url.includes('/api/cep/01310100'))
      return ok({ cep: '01310-100', street: 'Avenida Paulista', neighborhood: 'Bela Vista', city: 'São Paulo', uf: 'SP' })
    if (url.includes('/api/cep/')) return ok({ code: 'CEP_NOT_FOUND', detail: 'CEP não encontrado' }, 404)
    if (url.endsWith('/api/routes/plan') && init?.method === 'POST') return ok({ id: 'abc' }, 201)
    return ok({}, 500)
  })
}

describe('PlanPage', () => {
  it('preenche o endereço quando o CEP é válido', async () => {
    mockApi()
    renderPage()

    const origin = screen.getByRole('group', { name: /origem/i })
    await userEvent.type(within(origin).getByLabelText('CEP'), '01310100')

    expect(await within(origin).findByText('Avenida Paulista · Bela Vista · São Paulo/SP')).toBeInTheDocument()
    expect(within(origin).getByLabelText('CEP')).toHaveValue('01310-100')
  })

  it('avisa quando o CEP não existe', async () => {
    mockApi()
    renderPage()

    await userEvent.type(within(screen.getByRole('group', { name: /origem/i })).getByLabelText('CEP'), '99999999')

    expect(await screen.findByText('CEP não encontrado.')).toBeInTheDocument()
  })

  it('começa com retorno à origem ligado e limita a 10 destinos', async () => {
    mockApi()
    renderPage()

    expect(screen.getByRole('switch', { name: /retornar à origem/i })).toBeChecked()
    const add = screen.getByRole('button', { name: 'Adicionar destino' })
    for (let i = 1; i < MAX_DESTINATIONS; i++) await userEvent.click(add)

    expect(screen.getAllByRole('group', { name: /destino \d+/i })).toHaveLength(MAX_DESTINATIONS)
    expect(add).toBeDisabled()
  })

  it('valida campos antes de enviar', async () => {
    const fetch = mockApi()
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: 'Calcular rota' }))

    expect(screen.getAllByText('Informe um CEP com 8 dígitos.')).toHaveLength(2)
    expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining('/api/routes/plan'), expect.anything())
  })

  it('envia o pedido e abre a rota calculada', async () => {
    const fetch = mockApi()
    renderPage()

    const origin = screen.getByRole('group', { name: /origem/i })
    await userEvent.type(within(origin).getByLabelText('CEP'), '01310100')
    await userEvent.type(within(origin).getByLabelText('Número'), '1')
    const destination = screen.getByRole('group', { name: 'Destino 1' })
    await userEvent.type(within(destination).getByLabelText('CEP'), '01310100')
    await userEvent.type(within(destination).getByLabelText('Número'), '1000')
    await userEvent.click(screen.getByRole('switch', { name: /retornar à origem/i }))
    await userEvent.type(screen.getByLabelText('Consumo (km/l)'), '12,5')
    await userEvent.click(screen.getByRole('button', { name: 'Calcular rota' }))

    expect(await screen.findByText('detalhe da rota')).toBeInTheDocument()
    const call = fetch.mock.calls.find(([url]) => String(url).endsWith('/api/routes/plan'))!
    expect(JSON.parse(String(call[1]!.body))).toEqual({
      origin: { cep: '01310100', number: '1' },
      destinations: [{ cep: '01310100', number: '1000' }],
      roundTrip: false,
      consumptionKmPerL: 12.5,
    })
  })

  it('mostra o erro do servidor', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).includes('/api/cep/')
        ? ok({ cep: '01310-100', street: 'Avenida Paulista', city: 'São Paulo', uf: 'SP' })
        : ok({ code: 'ROUTE_NOT_FOUND', detail: 'Não existe rota de carro entre os pontos.' }, 422),
    )
    renderPage()

    for (const [group, number] of [[/origem/i, '1'], ['Destino 1', '2']] as const) {
      const fieldset = screen.getByRole('group', { name: group })
      await userEvent.type(within(fieldset).getByLabelText('CEP'), '01310100')
      await userEvent.type(within(fieldset).getByLabelText('Número'), number)
    }
    await userEvent.click(screen.getByRole('button', { name: 'Calcular rota' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Não existe rota de carro entre os pontos.'))
  })
})
