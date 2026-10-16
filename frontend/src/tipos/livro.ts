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
}
