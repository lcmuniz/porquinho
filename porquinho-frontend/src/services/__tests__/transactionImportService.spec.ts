import { describe, it, expect, vi, beforeEach } from 'vitest'
import { listImports, uploadImport } from '../transactionImportService'

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
