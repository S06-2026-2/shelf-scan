import type { LivroReconhecido } from '../tipos/livro'

// Endereço do ocr-service. Sem ele, o reconhecimento é simulado (modo demonstração).
// Contrato PROVISÓRIO: POST {URL}/reconhecer, multipart com o campo "imagem",
// resposta JSON { titulo, autor, editora?, ano?, genero?, sinopse? }.
const URL_OCR: string | undefined = import.meta.env.VITE_OCR_API_URL

export const modoDemonstracao = !URL_OCR

const LIMITE_MS = 60_000

const EXEMPLO: LivroReconhecido = {
  titulo: 'Dom Casmurro',
  autor: 'Machado de Assis',
  editora: 'Editora Garnier',
  ano: '1899',
  genero: 'Romance realista',
  sinopse:
    'Narrado por Bentinho, o romance acompanha seu ciúme obsessivo por Capitu e a dúvida que ' +
    'atravessa toda a obra: ela o teria traído com Escobar, seu melhor amigo?',
}

export async function reconhecerLivro(imagem: Blob): Promise<LivroReconhecido> {
  if (!URL_OCR) {
    await new Promise((resolver) => setTimeout(resolver, 1500))
    return EXEMPLO
  }

  const corpo = new FormData()
  corpo.append('imagem', imagem, 'capa.jpg')

  let resposta: Response
  try {
    resposta = await fetch(`${URL_OCR}/reconhecer`, {
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
    throw new Error(`O serviço de OCR respondeu com erro (${resposta.status}).`)
  }

  const dados: Partial<LivroReconhecido> = await resposta.json()
  if (!dados.titulo) {
    throw new Error('Não consegui ler o título nessa imagem. Tente uma foto mais nítida e de frente.')
  }
  return { ...dados, titulo: dados.titulo, autor: dados.autor ?? '' }
}
