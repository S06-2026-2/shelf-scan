import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { livros as livrosDeExemplo } from '../mocks/livros'
import type { Livro } from '../tipos/livro'
import { LivrosContext } from './LivrosContext'

// Guarda a estante em memória enquanto não existe API de livros: recarregar a página volta ao exemplo.
export function LivrosProvider({ children }: { children: ReactNode }) {
  const [livros, setLivros] = useState<Livro[]>(livrosDeExemplo)

  const adicionarLivro = useCallback((livro: Livro) => {
    setLivros((atuais) => [livro, ...atuais])
  }, [])

  const valor = useMemo(() => ({ livros, adicionarLivro }), [livros, adicionarLivro])

  return <LivrosContext.Provider value={valor}>{children}</LivrosContext.Provider>
}
