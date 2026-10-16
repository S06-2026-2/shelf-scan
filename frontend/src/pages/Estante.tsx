import { useState } from 'react'
import { Link } from 'react-router-dom'
import { CartaoLivro } from '../components/CartaoLivro'
import { livros } from '../mocks/livros'
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

function contar(filtro: Filtro): number {
  return filtro === 'todos' ? livros.length : livros.filter((livro) => livro.status === filtro).length
}

export function Estante() {
  const [filtro, setFiltro] = useState<Filtro>('todos')

  const visiveis = filtro === 'todos' ? livros : livros.filter((livro) => livro.status === filtro)

  return (
    <div className="estante">
      <header className="estante-cabecalho">
        <h1>Minha estante</h1>
        <Link to="/estante/adicionar" className="botao botao-primario">
          + Adicionar livro
        </Link>
      </header>

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
    </div>
  )
}
