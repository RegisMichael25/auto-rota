import { describe, expect, it } from 'vitest'
import { decimalPlaces, formatDistance, formatDuration, formatLiters, formatMoney, formatPricePerLiter, maskCep, parseDecimal } from './format'

describe('format', () => {
  it('formata distância em metros abaixo de 1 km e em km acima', () => {
    expect(formatDistance(850)).toBe('850 m')
    expect(formatDistance(17564)).toBe('17,6 km')
  })

  it('formata duração em minutos e horas', () => {
    expect(formatDuration(59)).toBe('1 min')
    expect(formatDuration(45 * 60)).toBe('45 min')
    expect(formatDuration(3900)).toBe('1 h 05 min')
    expect(formatDuration(7200)).toBe('2 h')
  })

  it('formata dinheiro e litros em pt-BR', () => {
    expect(formatMoney(10.5).replace(/\s/g, ' ')).toBe('R$ 10,50')
    expect(formatLiters(1.756)).toBe('1,76 L')
    expect(formatPricePerLiter(6.199).replace(/\s/g, ' ')).toBe('R$ 6,199/l')
    expect(formatPricePerLiter(6).replace(/\s/g, ' ')).toBe('R$ 6,00/l')
  })

  it('aplica a máscara de CEP', () => {
    expect(maskCep('01310')).toBe('01310')
    expect(maskCep('01310100')).toBe('01310-100')
    expect(maskCep('01.310-1009')).toBe('01310-100')
  })

  it('lê decimais com vírgula ou ponto', () => {
    expect(parseDecimal('')).toBeUndefined()
    expect(parseDecimal('10,5')).toBe(10.5)
    expect(parseDecimal('6.199')).toBe(6.199)
    expect(parseDecimal('abc')).toBeNaN()
    expect(decimalPlaces('6,1999')).toBe(4)
  })
})
