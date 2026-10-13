import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { HistoryPage } from './HistoryPage'

const route = {
  id: 'r1', name: 'Entregas da manhã', createdAt: '2026-10-13T12:00:00Z', status: 'EM_ANDAMENTO', roundTrip: true,
  originCity: 'São Paulo', originUf: 'SP', stopCount: 3, deliveredCount: 1, notDeliveredCount: 1,
  totalDistanceM: 17564, totalDurationS: 2040, fuelLiters: 1.756, fuelCost: 10.89,
}

const page = (content: unknown[]) =>
  new Response(JSON.stringify({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }), { status: 200 })

function renderPage(path = '/rotas') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/rotas" element={<HistoryPage />} />
          <Route path="/rotas/:id" element={<p>detalhe</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('HistoryPage', () => {
  it('lista rotas e abre o detalhe', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(page([route]))
    renderPage()

    expect(await screen.findByText('Entregas da manhã')).toBeInTheDocument()
    expect(screen.getByText('1 de 3')).toBeInTheDocument()
    expect(screen.getByText('17,6 km')).toBeInTheDocument()

    await userEvent.click(screen.getByText('Entregas da manhã'))
    expect(await screen.findByText('detalhe')).toBeInTheDocument()
  })

  it('envia filtros de nome e período', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => page([]))
    renderPage()
    await screen.findByText('Nenhuma rota planejada ainda.')

    await userEvent.type(screen.getByLabelText('Nome'), 'manhã')
    await userEvent.type(screen.getByLabelText('De'), '2026-10-01')
    await userEvent.type(screen.getByLabelText('Até'), '2026-10-31')
    await userEvent.click(screen.getByRole('button', { name: 'Filtrar' }))

    expect(await screen.findByText('Nenhuma rota com esses filtros.')).toBeInTheDocument()
    const last = new URL(String(fetch.mock.calls.at(-1)![0]))
    expect(Object.fromEntries(last.searchParams)).toEqual({ name: 'manhã', from: '2026-10-01', to: '2026-10-31', page: '0', size: '20' })
  })

  it('mostra erro do servidor', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ code: 'VALIDATION_ERROR', detail: 'Data final anterior à inicial' }), { status: 400 }),
    )
    renderPage('/rotas?from=2026-10-31&to=2026-10-01')

    expect(await screen.findByRole('alert')).toHaveTextContent('Data final anterior à inicial')
  })
})
