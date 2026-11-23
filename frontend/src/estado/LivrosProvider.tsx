import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { enriquecerLivro, listarLivros, modoDemonstracao, salvarLivro } from '../api/livros'
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

function listaDeCampos(campos: string[]): string {
  if (campos.length <= 1) return campos.join('')
  return `${campos.slice(0, -1).join(', ')} e ${campos[campos.length - 1]}`
}

// Com o banco configurado (modo real), a estante vem do database-service. Sem ele, fica em memória:
// recarregar a página volta ao exemplo.
export function LivrosProvider({ children }: { children: ReactNode }) {
  const [livros, setLivros] = useState<Livro[]>(modoDemonstracao ? livrosDeExemplo : [])
  const [carregando, setCarregando] = useState(!modoDemonstracao)
  const [erro, setErro] = useState<string | null>(null)
  const [enriquecendo, setEnriquecendo] = useState<ReadonlySet<string>>(new Set())
  const [aviso, setAviso] = useState<string | null>(null)

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

  // Roda em segundo plano depois de salvar: o livro já está no banco, então uma falha da IA só vira aviso.
  const completarComIa = useCallback(
    async (livroId: string, textoBruto?: string) => {
      setEnriquecendo((atuais) => new Set(atuais).add(livroId))
      try {
        const resumo = await enriquecerLivro(usuarioAtual.id, livroId, textoBruto)
        aplicar(await buscar())
        setAviso(
          resumo.campos.length > 0
            ? `A IA completou: ${listaDeCampos(resumo.campos)}. ${resumo.aviso}`
            : 'A IA não encontrou dados novos para esse livro.',
        )
      } catch {
        setAviso('O livro foi salvo, mas a IA não conseguiu completá-lo agora.')
      } finally {
        setEnriquecendo((atuais) => {
          const restantes = new Set(atuais)
          restantes.delete(livroId)
          return restantes
        })
      }
    },
    [aplicar],
  )

  const adicionarLivro = useCallback(
    async (novo: NovoLivro) => {
      if (modoDemonstracao) {
        setLivros((atuais) => [livroLocal(novo), ...atuais])
        return
      }
      const livroId = await salvarLivro(usuarioAtual.id, novo)
      aplicar(await buscar())
      void completarComIa(livroId, novo.textoBruto)
    },
    [aplicar, completarComIa],
  )

  const fecharAviso = useCallback(() => setAviso(null), [])

  const valor = useMemo(
    () => ({ livros, carregando, erro, recarregar, adicionarLivro, enriquecendo, aviso, fecharAviso }),
    [livros, carregando, erro, recarregar, adicionarLivro, enriquecendo, aviso, fecharAviso],
  )

  return <LivrosContext.Provider value={valor}>{children}</LivrosContext.Provider>
}
