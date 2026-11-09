import { createContext, useContext } from 'react'
import type { Livro, NovoLivro } from '../tipos/livro'

export interface LivrosContextoValor {
  livros: Livro[]
  carregando: boolean
  erro: string | null
  recarregar: () => void
  adicionarLivro: (livro: NovoLivro) => Promise<void>
}

export const LivrosContext = createContext<LivrosContextoValor | null>(null)

export function useLivros(): LivrosContextoValor {
  const contexto = useContext(LivrosContext)
  if (!contexto) {
    throw new Error('useLivros precisa estar dentro de um LivrosProvider')
  }
  return contexto
}
