import { CapaLivro } from '../../components/CapaLivro'
import { recomendacoes } from '../../mocks/social'

export function Recomendacoes() {
  return (
    <section aria-labelledby="titulo-recomendacoes" className="perfil-secao">
      <h2 id="titulo-recomendacoes">Recomendado pra você</h2>
      <ul className="perfil-recomendacoes">
        {recomendacoes.map((livro) => (
          <li key={livro.id} className="perfil-recomendacao">
            <CapaLivro titulo={livro.titulo} autor={livro.autor} cor={livro.cor} />
            <span className="perfil-selo-ia">Recomendado</span>
            <strong>{livro.titulo}</strong>
            <span className="perfil-recomendacao-autor">{livro.autor}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
