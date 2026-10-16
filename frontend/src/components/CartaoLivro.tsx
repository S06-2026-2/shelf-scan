import type { Livro } from '../tipos/livro'
import { CapaLivro } from './CapaLivro'
import { SeloStatus } from './SeloStatus'
import './CartaoLivro.css'

export function CartaoLivro({ livro }: { livro: Livro }) {
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
    </article>
  )
}
