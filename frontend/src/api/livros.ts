import type { DetalhesDoLivro, Livro, NovoLivro, ResumoDoEnriquecimento } from '../tipos/livro'
import {
  MENSAGEM_BANCO_FORA,
  converterDetalhes,
  converterEnriquecimento,
  converterResumo,
  mensagemDoErro,
  montarCorpo,
  type EnriquecimentoDoContrato,
  type EstanteDoContrato,
  type LivroDetalheDoContrato,
  type LivroResumoDoContrato,
} from './livrosContrato'

// Endereço do database-service já com o prefixo da API (em desenvolvimento: /api/v1, que o Vite repassa
// para http://localhost:8321). Sem ele, ou sem o id do usuário, a estante é de exemplo (modo demonstração).
// Contrato: docs/api/database-service.md
const URL_BANCO: string | undefined = import.meta.env.VITE_DATABASE_API_URL
const ID_DO_USUARIO: string | undefined = import.meta.env.VITE_USUARIO_ID

export const modoDemonstracao = !URL_BANCO || !ID_DO_USUARIO

const LIMITE_MS = 15_000

// O enriquecimento consulta catálogos e o modelo (até 6 rodadas): leva bem mais que uma consulta comum.
const LIMITE_ENRIQUECIMENTO_MS = 90_000

async function chamar(caminho: string, opcoes: RequestInit = {}, limiteMs = LIMITE_MS): Promise<Response> {
  let resposta: Response
  try {
    resposta = await fetch(`${URL_BANCO}${caminho}`, {
      ...opcoes,
      signal: AbortSignal.timeout(limiteMs),
    })
  } catch {
    throw new Error(MENSAGEM_BANCO_FORA)
  }

  if (!resposta.ok) {
    const corpoDoErro: unknown = await resposta.json().catch(() => null)
    throw new Error(mensagemDoErro(resposta.status, corpoDoErro))
  }
  return resposta
}

// Livros da estante padrão do usuário (a que nasce com ele, "Minha estante").
export async function listarLivros(usuarioId: string): Promise<Livro[]> {
  const base = `/usuario/${encodeURIComponent(usuarioId)}`

  const respostaEstantes = await chamar(`${base}/estante`)
  const estantes: EstanteDoContrato[] = await respostaEstantes.json()
  const padrao = estantes.find((estante) => estante.padrao && estante.id !== null)
  if (!padrao) return []

  const respostaLivros = await chamar(`${base}/estante/${padrao.id}/livro`)
  const resumos: LivroResumoDoContrato[] = await respostaLivros.json()
  return resumos.map(converterResumo)
}

// Sem estanteId, o banco guarda o livro na estante padrão. Devolve o id do livro criado.
export async function salvarLivro(usuarioId: string, novo: NovoLivro): Promise<string> {
  const resposta = await chamar(`/usuario/${encodeURIComponent(usuarioId)}/livro`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(montarCorpo(novo)),
  })
  const criado: { id: number } = await resposta.json()
  return String(criado.id)
}

export async function obterDetalhes(livroId: string): Promise<DetalhesDoLivro> {
  const resposta = await chamar(`/livro/${encodeURIComponent(livroId)}`)
  const livro: LivroDetalheDoContrato = await resposta.json()
  return converterDetalhes(livro)
}

// O texto do OCR ajuda o agente a achar a edição; o banco recusa mais de 5000 caracteres.
export async function enriquecerLivro(
  usuarioId: string,
  livroId: string,
  textoBruto?: string,
): Promise<ResumoDoEnriquecimento> {
  const corpo = textoBruto ? { textoBruto: textoBruto.slice(0, 5000) } : {}
  const resposta = await chamar(
    `/usuario/${encodeURIComponent(usuarioId)}/livro/${encodeURIComponent(livroId)}/enriquecimento`,
    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(corpo) },
    LIMITE_ENRIQUECIMENTO_MS,
  )
  const dados: EnriquecimentoDoContrato = await resposta.json()
  return converterEnriquecimento(dados)
}
