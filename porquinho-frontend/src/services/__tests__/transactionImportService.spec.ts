import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  approveHighConfidence,
  discardImport,
  getImport,
  listImports,
  listImportTransactions,
  uploadImport,
} from '../transactionImportService'

const mockGet = vi.fn()
const mockPost = vi.fn()

vi.mock('../api', () => ({
  api: {
    get: (...a: unknown[]) => mockGet(...a),
    post: (...a: unknown[]) => mockPost(...a),
  },
}))

describe('transactionImportService', () => {
  beforeEach(() => {
    mockGet.mockReset()
    mockPost.mockReset()
  })

  it('lista as importações em /api/v1/imports', async () => {
    mockGet.mockResolvedValue({ data: [] })
    expect(await listImports()).toEqual([])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/imports')
  })

  it('consulta uma importação em /api/v1/imports/{id} (Story 5.3)', async () => {
    mockGet.mockResolvedValue({ data: { id: 'i1', bankName: 'NUBANK' } })
    expect(await getImport('i1')).toEqual({ id: 'i1', bankName: 'NUBANK' })
    expect(mockGet).toHaveBeenCalledWith('/api/v1/imports/i1')
  })

  it('lista as linhas da revisão em /api/v1/imports/{id}/transactions (Story 5.9)', async () => {
    mockGet.mockResolvedValue({ data: [{ id: 't1' }] })
    expect(await listImportTransactions('i1')).toEqual([{ id: 't1' }])
    expect(mockGet).toHaveBeenCalledWith('/api/v1/imports/i1/transactions')
  })

  it('aprova em POST /api/v1/imports/{id}/approve-high-confidence (Story 5.9)', async () => {
    mockPost.mockResolvedValue({ data: { affectedTransactions: 3 } })
    expect(await approveHighConfidence('i1')).toEqual({ affectedTransactions: 3 })
    expect(mockPost).toHaveBeenCalledWith('/api/v1/imports/i1/approve-high-confidence')
  })

  it('descarta em POST /api/v1/imports/{id}/discard (Story 5.9)', async () => {
    mockPost.mockResolvedValue({ data: { status: 'DISCARDED' } })
    expect(await discardImport('i1')).toEqual({ status: 'DISCARDED' })
    expect(mockPost).toHaveBeenCalledWith('/api/v1/imports/i1/discard')
  })

  it('envia multipart com file e accountId e repassa o progresso', async () => {
    mockPost.mockImplementation(async (_url: string, _form: FormData, config: { onUploadProgress: (e: object) => void }) => {
      config.onUploadProgress({ loaded: 50, total: 200 })
      return { data: { id: 'i1' } }
    })
    const file = new File(['OFXHEADER:100'], 'a.ofx')
    const progress = vi.fn()

    await uploadImport(file, { kind: 'account', id: 'a1' }, progress)

    const [url, form, config] = mockPost.mock.calls[0]!
    expect(url).toBe('/api/v1/imports')
    expect((form as FormData).get('file')).toBeInstanceOf(File)
    expect((form as FormData).get('accountId')).toBe('a1')
    expect((form as FormData).has('creditCardId')).toBe(false)
    expect(config.headers['Content-Type']).toBe('multipart/form-data')
    expect(progress).toHaveBeenCalledWith(25)
  })

  it('usa creditCardId para cartão', async () => {
    mockPost.mockResolvedValue({ data: { id: 'i1' } })
    await uploadImport(new File(['x'], 'b.ofx'), { kind: 'card', id: 'c1' })
    const form = mockPost.mock.calls[0]![1] as FormData
    expect(form.get('creditCardId')).toBe('c1')
    expect(form.has('accountId')).toBe(false)
  })
})
