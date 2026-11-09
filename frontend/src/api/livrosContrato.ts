import type { Livro, NovoLivro, StatusLeitura } from '../tipos/livro'
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
