import type { Livro } from '../tipos/livro'
import { CapaLivro } from './CapaLivro'
import { SeloStatus } from './SeloStatus'
import './CartaoLivro.css'

interface Propriedades {
  livro: Livro
  // a IA está completando este livro
  enriquecendo?: boolean
  onDetalhes?: () => void
}

export function CartaoLivro({ livro, enriquecendo = false, onDetalhes }: Propriedades) {
  return (
    <article className="cartao-livro">
      <div className="cartao-livro-capa">
        <CapaLivro titulo={livro.titulo} autor={livro.autor} cor={livro.cor} />
        <span className="cartao-livro-selo">
          <SeloStatus status={livro.status} />
        </span>
      </div>

      <h3 className="cartao-livro-titulo">{livro.titulo}</h3>
      <p className="cartao-livro-autor">{livro.autor}</p>

      {livro.status === 'lendo' && livro.progresso !== undefined && (
        <>
          <div
            className="progresso"
            role="progressbar"
            aria-label={`Progresso de leitura de ${livro.titulo}`}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={livro.progresso}
          >
            <i style={{ width: `${livro.progresso}%` }} />
          </div>
          <p className="cartao-livro-detalhe">{livro.progresso}% concluído</p>
        </>
      )}

      {livro.status === 'lido' && livro.concluidoEm && (
        <p className="cartao-livro-detalhe">concluído em {livro.concluidoEm}</p>
      )}

      {livro.status === 'emprestado' && livro.emprestadoPara && (
        <p className="cartao-livro-detalhe">com {livro.emprestadoPara}</p>
      )}

      {livro.aviso && <p className="cartao-livro-aviso">{livro.aviso}</p>}

      {enriquecendo && (
        <p className="cartao-livro-ia" role="status">
          Completando com IA…
        </p>
      )}

      {onDetalhes && (
        <button type="button" className="cartao-livro-detalhes" onClick={onDetalhes}>
          Detalhes
        </button>
      )}
    </article>
  )
}
