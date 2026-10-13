const DEFAULT_API_URL = 'http://localhost:8080'

export const API_URL = (import.meta.env.VITE_API_URL || DEFAULT_API_URL).replace(/\/+$/, '')

export interface FieldError {
  field: string
  message: string
}

/** Erro da API no formato ProblemDetail (RFC 7807), com o `code` estável do backend. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

type Query = Record<string, string | number | undefined>

export async function request<T>(path: string, init: RequestInit & { query?: Query } = {}): Promise<T> {
  const { query, ...options } = init
  const url = new URL(API_URL + path)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== '') url.searchParams.set(key, String(value))
  }

  let response: Response
  try {
    response = await fetch(url, {
      ...options,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Não foi possível falar com o servidor. Verifique a conexão e tente de novo.')
  }

  if (!response.ok) throw await toApiError(response)
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: { detail?: string; title?: string; code?: string; errors?: FieldError[] } = {}
  try {
    body = await response.json()
  } catch {
    // corpo vazio ou não JSON
  }
  return new ApiError(
    response.status,
    body.code ?? 'HTTP_' + response.status,
    body.detail ?? body.title ?? 'Erro inesperado no servidor (' + response.status + ').',
    Array.isArray(body.errors) ? body.errors : [],
  )
}
