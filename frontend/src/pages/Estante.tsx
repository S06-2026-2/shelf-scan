import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CartaoLivro } from '../components/CartaoLivro'
import { useLivros } from '../estado/LivrosContext'
import type { StatusLeitura } from '../tipos/livro'
import './Estante.css'

type Filtro = 'todos' | StatusLeitura

const FILTROS: { valor: Filtro; rotulo: string }[] = [
  { valor: 'todos', rotulo: 'Todos' },
  { valor: 'lendo', rotulo: 'Lendo' },
  { valor: 'lido', rotulo: 'Lido' },
  { valor: 'quero-ler', rotulo: 'Quero ler' },
  { valor: 'emprestado', rotulo: 'Emprestados' },
]

export function Estante() {
  const { livros, carregando, erro, recarregar } = useLivros()
  const [filtro, setFiltro] = useState<Filtro>('todos')

  const contar = (valor: Filtro) =>
    valor === 'todos' ? livros.length : livros.filter((livro) => livro.status === valor).length

  const visiveis = filtro === 'todos' ? livros : livros.filter((livro) => livro.status === filtro)

  return (
    <div className="estante">
      <header className="estante-cabecalho">
        <h1>Minha estante</h1>
        <Link to="/estante/adicionar" className="botao botao-primario">
          + Adicionar livro
        </Link>
      </header>

      {erro ? (
        <div role="alert">
          <p className="estante-vazia">{erro}</p>
          <button type="button" className="botao" onClick={recarregar}>
            Tentar de novo
          </button>
        </div>
      ) : carregando ? (
        <p className="estante-vazia" role="status">
          Carregando a estante…
        </p>
      ) : (
        <>
          <div className="estante-filtros" role="group" aria-label="Filtrar por status de leitura">
            {FILTROS.map((opcao) => (
              <button
                key={opcao.valor}
                type="button"
                className={opcao.valor === filtro ? 'filtro ativo' : 'filtro'}
                aria-pressed={opcao.valor === filtro}
                onClick={() => setFiltro(opcao.valor)}
              >
                {opcao.rotulo} · {contar(opcao.valor)}
              </button>
            ))}
          </div>

          {visiveis.length === 0 ? (
            <p className="estante-vazia">Nenhum livro com esse status ainda.</p>
          ) : (
            <ul className="estante-grade">
              {visiveis.map((livro) => (
                <li key={livro.id}>
                  <CartaoLivro livro={livro} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
