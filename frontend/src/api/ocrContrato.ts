import type { LivroReconhecido } from '../tipos/livro'

// Formato da resposta do ocr-service, conforme services/ocr-srvice/docs/coentrato-api.md (seção 4.2)
// e ocr/pipeline.py. PROVISÓRIO: os nomes definitivos ainda são TODO no contrato. Quando forem
// fechados, só este arquivo precisa mudar.
export interface LivroDoContrato {
  nome: string | null
  volume: string | null
  autores: string[] | null
  editora: string | null
}

export interface RespostaDoContrato {
  books: LivroDoContrato[]
  raw_text: string
  result?: string
}

export type EstadoDaLeitura = 'estruturado' | 'nao-estruturado' | 'nenhum-texto'

// O que o resto do front usa: o livro reconhecido, o texto bruto lido e em que pé ficou a leitura.
export interface LeituraDaFoto {
  livro: LivroReconhecido
  textoBruto: string
  estado: EstadoDaLeitura
}

function vazioParaUndefined(valor: string | null | undefined): string | undefined {
  const limpo = valor?.trim() ?? ''
  return limpo === '' ? undefined : limpo
}

function estadoDa(resposta: RespostaDoContrato, titulo: string, textoBruto: string): EstadoDaLeitura {
  if (resposta.result === 'no_text') return 'nenhum-texto'
  if (resposta.result === 'unstructured') return 'nao-estruturado'
  // Sem indicador (ou com um valor ainda desconhecido), decide pelo conteúdo.
  if (titulo !== '') return 'estruturado'
  return textoBruto !== '' ? 'nao-estruturado' : 'nenhum-texto'
}

export function converterResposta(resposta: RespostaDoContrato): LeituraDaFoto {
  // MVP: uma capa, um livro (a resposta já é uma lista).
  const primeiro = resposta.books?.[0]
  const textoBruto = resposta.raw_text?.trim() ?? ''

  const titulo = vazioParaUndefined(primeiro?.nome) ?? ''
  const autor = (primeiro?.autores ?? [])
    .map((nome) => nome.trim())
    .filter((nome) => nome !== '')
    .join(', ')

  return {
    livro: {
      titulo,
      autor,
      volume: vazioParaUndefined(primeiro?.volume),
      editora: vazioParaUndefined(primeiro?.editora),
    },
    textoBruto,
    estado: estadoDa(resposta, titulo, textoBruto),
  }
}

// O contrato diz que o cliente age pelo identificador estável do erro, não pelo texto da mensagem.
// O formato do corpo de erro ainda não está definido: aceita `code` na raiz ou dentro de `detail`.
export function codigoDoErro(corpo: unknown): string | undefined {
  if (typeof corpo !== 'object' || corpo === null) return undefined
  const { code, detail } = corpo as { code?: unknown; detail?: unknown }
  if (typeof code === 'string') return code
  if (typeof detail === 'object' && detail !== null) {
    const interno = (detail as { code?: unknown }).code
    if (typeof interno === 'string') return interno
  }
  return undefined
}

const MENSAGENS_POR_CODIGO: Record<string, string> = {
  unsupported_format: 'O arquivo não é uma foto JPEG ou PNG. Escolha outro.',
  corrupt_image: 'Não consegui abrir essa foto. Tente enviar outra.',
  image_too_large: 'A foto é grande demais. Envie uma menor.',
  image_too_small: 'A foto é pequena demais. Envie uma com mais resolução.',
}

export function mensagemDoErro(status: number, codigo?: string): string {
  if (codigo && MENSAGENS_POR_CODIGO[codigo]) return MENSAGENS_POR_CODIGO[codigo]
  if (status === 413) return 'A foto passa do limite de tamanho do serviço. Envie uma menor.'
  if (status === 429) return 'Muitas leituras seguidas. Espere um instante e tente de novo.'
  if (status >= 500) return 'O serviço de OCR está ocupado ou iniciando. Tente de novo em instantes.'
  return `O serviço de OCR recusou a foto (${status}).`
}
