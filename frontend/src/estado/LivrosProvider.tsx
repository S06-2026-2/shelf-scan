import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { listarLivros, modoDemonstracao, salvarLivro } from '../api/livros'
import { livros as livrosDeExemplo } from '../mocks/livros'
import { usuarioAtual } from '../mocks/usuario'
import type { Livro, NovoLivro } from '../tipos/livro'
import { livroLocal } from '../utilitarios/livro'
import { LivrosContext } from './LivrosContext'

type Resultado = { livros: Livro[] } | { erro: string }

// Busca a estante no banco. Nunca lança: a falha volta como mensagem para a tela mostrar.
async function buscar(): Promise<Resultado> {
  try {
    return { livros: await listarLivros(usuarioAtual.id) }
  } catch (falha) {
    return { erro: falha instanceof Error ? falha.message : 'Não foi possível carregar a estante.' }
  }
}

// Com o banco configurado (modo real), a estante vem do database-service. Sem ele, fica em memória:
// recarregar a página volta ao exemplo.
export function LivrosProvider({ children }: { children: ReactNode }) {
  const [livros, setLivros] = useState<Livro[]>(modoDemonstracao ? livrosDeExemplo : [])
  const [carregando, setCarregando] = useState(!modoDemonstracao)
  const [erro, setErro] = useState<string | null>(null)

  const aplicar = useCallback((resultado: Resultado) => {
    if ('livros' in resultado) {
      setLivros(resultado.livros)
      setErro(null)
    } else {
      setErro(resultado.erro)
    }
    setCarregando(false)
  }, [])

  useEffect(() => {
    if (!modoDemonstracao) void buscar().then(aplicar)
  }, [aplicar])

  const recarregar = useCallback(() => {
    setCarregando(true)
    void buscar().then(aplicar)
  }, [aplicar])

  const adicionarLivro = useCallback(
    async (novo: NovoLivro) => {
      if (modoDemonstracao) {
        setLivros((atuais) => [livroLocal(novo), ...atuais])
        return
      }
      await salvarLivro(usuarioAtual.id, novo)
      aplicar(await buscar())
    },
    [aplicar],
  )

  const valor = useMemo(
    () => ({ livros, carregando, erro, recarregar, adicionarLivro }),
    [livros, carregando, erro, recarregar, adicionarLivro],
  )

  return <LivrosContext.Provider value={valor}>{children}</LivrosContext.Provider>
}
