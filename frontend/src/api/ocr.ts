import {
  codigoDoErro,
  converterResposta,
  mensagemDoErro,
  type LeituraDaFoto,
  type RespostaDoContrato,
} from './ocrContrato'

// Endereço do ocr-service. Sem ele, o reconhecimento é simulado (modo demonstração).
// Contrato: services/ocr-service/docs/contrato-api.md (ainda um esqueleto, com TODOs).
const URL_OCR: string | undefined = import.meta.env.VITE_OCR_API_URL

export const modoDemonstracao = !URL_OCR

const ROTA = '/v1/extraction'

// Nome do campo do arquivo no multipart: ainda TODO no contrato. Ajuste aqui quando for definido.
const CAMPO_DA_FOTO = 'file'

// A leitura leva de 6 a 10 s por capa; com a fila de até ~30 s do serviço, 60 s cobre o pior caso.
const LIMITE_MS = 60_000

const RESPOSTA_DE_EXEMPLO: RespostaDoContrato = {
  books: [{ nome: 'Dom Casmurro', volume: null, autores: ['Machado de Assis'], editora: 'Editora Garnier' }],
  raw_text: 'DOM CASMURRO\nMACHADO DE ASSIS',
  result: 'structured',
}

export async function reconhecerLivro(imagem: Blob): Promise<LeituraDaFoto> {
  if (!URL_OCR) {
    await new Promise((resolver) => setTimeout(resolver, 1500))
    return converterResposta(RESPOSTA_DE_EXEMPLO)
  }

  const corpo = new FormData()
  corpo.append(CAMPO_DA_FOTO, imagem, 'capa.jpg')

  let resposta: Response
  try {
    resposta = await fetch(`${URL_OCR}${ROTA}`, {
      method: 'POST',
      body: corpo,
      signal: AbortSignal.timeout(LIMITE_MS),
    })
  } catch (erro) {
    if (erro instanceof DOMException && erro.name === 'TimeoutError') {
      throw new Error('O reconhecimento demorou demais. Tente de novo com uma foto menor.')
    }
    throw new Error('Não foi possível falar com o serviço de OCR. Ele está no ar?')
  }

  if (!resposta.ok) {
    const corpoDoErro: unknown = await resposta.json().catch(() => null)
    throw new Error(mensagemDoErro(resposta.status, codigoDoErro(corpoDoErro)))
  }

  const dados: RespostaDoContrato = await resposta.json()
  return converterResposta(dados)
}
