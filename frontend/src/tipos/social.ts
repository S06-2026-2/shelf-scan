export interface Pessoa {
  id: string
  nome: string
  iniciais: string
  // Cor do avatar (1 a 6).
  cor: number
  amigosEmComum?: number
}

export interface Biblioteca {
  id: string
  nome: string
  descricao: string
  membro: boolean
}

export interface Recomendacao {
  id: string
  titulo: string
  autor: string
  // Cor da capa (1 a 8).
  cor: number
}
