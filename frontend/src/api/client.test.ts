import { describe, expect, it, vi } from 'vitest'
import { ApiError, request } from './client'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } })

describe('request', () => {
  it('converte ProblemDetail em ApiError com code e erros de campo', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(400, {
        title: 'Dados inválidos',
        detail: 'Corrija os campos indicados.',
        code: 'VALIDATION_ERROR',
        errors: [{ field: 'origin.cep', message: 'CEP deve ter 8 dígitos' }],
      }),
    )

    const error = await request<never>('/api/routes/plan', { method: 'POST', body: '{}' }).catch((e: ApiError) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 400, code: 'VALIDATION_ERROR', message: 'Corrija os campos indicados.' })
    expect(error.fieldErrors).toEqual([{ field: 'origin.cep', message: 'CEP deve ter 8 dígitos' }])
  })

  it('informa falha de rede de forma legível', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(request('/api/routes')).rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' })
  })

  it('omite parâmetros vazios da query string', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { content: [] }))

    await request('/api/routes', { query: { name: '', page: 0, from: undefined } })

    expect(String(fetch.mock.calls[0][0])).toBe('http://localhost:8080/api/routes?page=0')
  })
})
