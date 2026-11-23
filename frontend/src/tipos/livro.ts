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
  genero?: string
  sinopse?: string
}

// O que a tela de confirmação coleta para salvar um livro. O banco e o modo demonstração partem daqui.
export interface NovoLivro {
  titulo: string
  autores: string[]
  volume?: string
  editora?: string
  // Texto que o OCR leu da capa. Não vai no cadastro: só ajuda o enriquecimento a achar a edição certa.
  textoBruto?: string
  genero?: string
  sinopse?: string
  paginas?: number
  status: Exclude<StatusLeitura, 'emprestado'>
  paginaAtual?: number
}

// Dados completos de um livro, como o banco guarda (painel de detalhes).
export interface DetalhesDoLivro {
  titulo: string
  autores: string[]
  editora?: string
  isbn?: string
  volume?: string
  paginas?: number
  sinopse?: string
  tags: string[]
}

// O que a IA completou no livro, para o aviso da estante.
export interface ResumoDoEnriquecimento {
  campos: string[]
  aviso: string
}

// O que o OCR identifica na foto (contrato do ocr-service). Ano, gênero e sinopse não vêm daqui:
// o enriquecimento é do database-service.
export interface LivroReconhecido {
  titulo: string
  autor: string
  volume?: string
  editora?: string
}
