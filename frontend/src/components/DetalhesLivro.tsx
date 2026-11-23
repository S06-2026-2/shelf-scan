import { useEffect, useRef, useState } from 'react'
import { modoDemonstracao, obterDetalhes } from '../api/livros'
import type { DetalhesDoLivro, Livro } from '../tipos/livro'
import { detalhesLocais } from '../utilitarios/livro'
import './DetalhesLivro.css'

type Carga =
  | { tipo: 'carregando' }
  | { tipo: 'pronto'; detalhes: DetalhesDoLivro }
  | { tipo: 'erro'; mensagem: string }

// Painel com os dados completos do livro. Usa o <dialog> do navegador: o Esc fecha e o foco fica preso nele.
export function DetalhesLivro({ livro, onFechar }: { livro: Livro; onFechar: () => void }) {
  const caixa = useRef<HTMLDialogElement>(null)
  const [carga, setCarga] = useState<Carga>(
    modoDemonstracao ? { tipo: 'pronto', detalhes: detalhesLocais(livro) } : { tipo: 'carregando' },
  )

  useEffect(() => {
    // No modo de desenvolvimento o React roda o efeito duas vezes; abrir de novo um <dialog> aberto dá erro
    if (!caixa.current?.open) caixa.current?.showModal()
  }, [])

  useEffect(() => {
    if (modoDemonstracao) return
    let cancelado = false
    obterDetalhes(livro.id)
      .then((detalhes) => {
        if (!cancelado) setCarga({ tipo: 'pronto', detalhes })
      })
      .catch((falha) => {
        if (!cancelado) {
          setCarga({ tipo: 'erro', mensagem: falha instanceof Error ? falha.message : 'Algo deu errado.' })
        }
      })
    return () => {
      cancelado = true
    }
  }, [livro.id])

  return (
    <dialog ref={caixa} className="detalhes-livro" onClose={onFechar} aria-labelledby="titulo-detalhes">
      <h2 id="titulo-detalhes">{livro.titulo}</h2>

      {carga.tipo === 'carregando' && <p role="status">Carregando os detalhes…</p>}
      {carga.tipo === 'erro' && <p role="alert">{carga.mensagem}</p>}
      {carga.tipo === 'pronto' && <Dados detalhes={carga.detalhes} />}

      <button type="button" className="botao" onClick={() => caixa.current?.close()}>
        Fechar
      </button>
    </dialog>
  )
}

function Dados({ detalhes }: { detalhes: DetalhesDoLivro }) {
  const linhas: [string, string | undefined][] = [
    ['Autores', detalhes.autores.join(', ') || undefined],
    ['Editora', detalhes.editora],
    ['ISBN', detalhes.isbn],
    ['Volume', detalhes.volume],
    ['Páginas', detalhes.paginas === undefined ? undefined : String(detalhes.paginas)],
    ['Gêneros', detalhes.tags.join(', ') || undefined],
  ]

  return (
    <>
      <dl>
        {linhas.map(([rotulo, valor]) => (
          <div key={rotulo} className="detalhes-livro-linha">
            <dt>{rotulo}</dt>
            <dd>{valor ?? '—'}</dd>
          </div>
        ))}
      </dl>
      <p className="detalhes-livro-sinopse">{detalhes.sinopse ?? 'Sem sinopse cadastrada.'}</p>
    </>
  )
}
