export type StatusLeitura = 'lendo' | 'lido' | 'quero-ler' | 'emprestado'

export interface Livro {
  id: string
  titulo: string
  autor: string
  status: StatusLeitura
  // Cor da capa (1 a 8); a capa real ainda não existe, então ela é desenhada com o título.
  cor: number
  progresso?: number
  concluidoEm?: string
  emprestadoPara?: string
  aviso?: string
  volume?: string
  editora?: string
  ano?: string
  genero?: string
  sinopse?: string
}

// O que o OCR identifica na foto (contrato do ocr-service). Ano, gênero e sinopse não vêm daqui:
// o enriquecimento é do database-service.
export interface LivroReconhecido {
  titulo: string
  autor: string
  volume?: string
  editora?: string
}
