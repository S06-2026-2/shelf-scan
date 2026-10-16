import { createContext, useContext } from 'react'
import type { Livro } from '../tipos/livro'

export interface LivrosContextoValor {
  livros: Livro[]
  adicionarLivro: (livro: Livro) => void
}

export const LivrosContext = createContext<LivrosContextoValor | null>(null)

export function useLivros(): LivrosContextoValor {
  const contexto = useContext(LivrosContext)
  if (!contexto) {
    throw new Error('useLivros precisa estar dentro de um LivrosProvider')
  }
  return contexto
}
