import type { DetalhesDoLivro, Livro, NovoLivro, ResumoDoEnriquecimento, StatusLeitura } from '../tipos/livro'
import { corDoTitulo, mesEAno, textoDosAutores } from '../utilitarios/livro'

// Formato das respostas e do corpo do database-service, conforme docs/api/database-service.md
// (seção DTOs). Se os nomes mudarem, só este arquivo precisa mudar.
export interface EstanteDoContrato {
  id: number | null
  nome: string
  padrao: boolean
  virtual: boolean
}

interface LeituraDoContrato {
  status: 'quero-ler' | 'lendo' | 'lido'
  paginaAtual: number | null
  porcentagem: number | null
  dataConclusao: string | null
}

export interface LivroResumoDoContrato {
  id: number
  titulo: string
  autores: string[]
  leitura: LeituraDoContrato
  emprestado: boolean
  emprestadoPara?: string
  diasParaVencimento?: number
}

interface NovoLivroDoContrato {
  titulo: string
  autores: string[]
  tags?: string[]
  volume?: string
  editora?: string
  paginas?: number
  sinopse?: string
  leitura: { status: 'quero-ler' | 'lendo' | 'lido'; paginaAtual?: number }
}

// Corpo de POST /usuario/{id}/livro. Campos undefined somem no JSON.stringify.
export function montarCorpo(novo: NovoLivro): NovoLivroDoContrato {
  return {
    titulo: novo.titulo,
    autores: novo.autores,
    tags: novo.genero ? [novo.genero] : undefined,
    volume: novo.volume,
    editora: novo.editora,
    paginas: novo.paginas,
    sinopse: novo.sinopse,
    leitura: {
      status: novo.status,
      paginaAtual: novo.status === 'lendo' ? novo.paginaAtual : undefined,
    },
  }
}

// "2026-03-10" vira "mar/2026". Lê o texto direto para o fuso horário não mudar o dia.
function mesDaConclusao(data: string | null): string | undefined {
  const partes = data?.split('-')
  if (!partes || partes.length < 2) return undefined
  return mesEAno(Number(partes[0]), Number(partes[1]))
}

function dias(quantidade: number): string {
  return `${quantidade} ${quantidade === 1 ? 'dia' : 'dias'}`
}

// Só avisa quando a devolução está perto ou atrasada (até 3 dias).
function avisoDeDevolucao(diasParaVencimento: number | undefined): string | undefined {
  if (diasParaVencimento === undefined || diasParaVencimento > 3) return undefined
  if (diasParaVencimento < 0) return `Devolução atrasada há ${dias(-diasParaVencimento)}`
  if (diasParaVencimento === 0) return 'Devolução prevista para hoje'
  return `Devolução em ${dias(diasParaVencimento)}`
}

export function converterResumo(resumo: LivroResumoDoContrato): Livro {
  const { leitura } = resumo
  const status: StatusLeitura = resumo.emprestado ? 'emprestado' : leitura.status
  return {
    id: String(resumo.id),
    titulo: resumo.titulo,
    autor: textoDosAutores(resumo.autores),
    status,
    cor: corDoTitulo(resumo.titulo),
    progresso: status === 'lendo' ? (leitura.porcentagem ?? undefined) : undefined,
    concluidoEm: status === 'lido' ? mesDaConclusao(leitura.dataConclusao) : undefined,
    emprestadoPara: resumo.emprestado ? resumo.emprestadoPara : undefined,
    aviso: resumo.emprestado ? avisoDeDevolucao(resumo.diasParaVencimento) : undefined,
  }
}

// GET /livro/{id}: só os campos que o painel de detalhes mostra
export interface LivroDetalheDoContrato {
  titulo: string
  autores: string[]
  tags: string[]
  isbn: string | null
  volume: string | null
  editora?: string | null
  paginas: number | null
  sinopse: string | null
}

export function converterDetalhes(livro: LivroDetalheDoContrato): DetalhesDoLivro {
  return {
    titulo: livro.titulo,
    autores: livro.autores,
    editora: livro.editora ?? undefined,
    isbn: livro.isbn ?? undefined,
    volume: livro.volume ?? undefined,
    paginas: livro.paginas ?? undefined,
    sinopse: livro.sinopse ?? undefined,
    tags: livro.tags,
  }
}

// POST .../enriquecimento: o que a tela usa da resposta
export interface EnriquecimentoDoContrato {
  camposPreenchidos: string[]
  aviso: string
}

const ROTULOS_DOS_CAMPOS: Record<string, string> = {
  sinopse: 'sinopse',
  isbn: 'ISBN',
  paginas: 'páginas',
  editora: 'editora',
  autores: 'autores',
  tags: 'gêneros',
}

export function converterEnriquecimento(resposta: EnriquecimentoDoContrato): ResumoDoEnriquecimento {
  return {
    campos: resposta.camposPreenchidos.map((campo) => ROTULOS_DOS_CAMPOS[campo] ?? campo),
    aviso: resposta.aviso,
  }
}

export const MENSAGEM_BANCO_FORA = 'Não foi possível falar com o banco de dados. Ele está no ar?'

interface ProblemaDoContrato {
  campos?: { campo: string; mensagem: string }[]
}

// O front age pelo status HTTP; em 400 acrescenta os campos que o banco recusou.
export function mensagemDoErro(status: number, corpo: unknown): string {
  const problema = (typeof corpo === 'object' && corpo !== null ? corpo : {}) as ProblemaDoContrato
  if (status === 400) {
    const campos = problema.campos?.map((item) => `${item.campo}: ${item.mensagem}`).join('; ')
    return `O banco recusou os dados${campos ? ` (${campos})` : ''}.`
  }
  if (status === 404) return 'Usuário não encontrado no banco. Confira o VITE_USUARIO_ID.'
  if (status === 501) return 'O banco ainda não implementa essa operação.'
  // Com o proxy do Vite, banco parado ou lento chega como 502, 503 ou 504 em vez de falha de rede.
  if (status === 502 || status === 503 || status === 504) return MENSAGEM_BANCO_FORA
  return `O banco respondeu com erro (${status}).`
}
