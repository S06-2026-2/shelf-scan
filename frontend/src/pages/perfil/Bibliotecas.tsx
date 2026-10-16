import type { Biblioteca } from '../../tipos/social'

interface BibliotecasProps {
  bibliotecas: Biblioteca[]
  aoEntrar: (id: string) => void
}

export function Bibliotecas({ bibliotecas, aoEntrar }: BibliotecasProps) {
  return (
    <section aria-labelledby="titulo-bibliotecas" className="perfil-secao">
      <h2 id="titulo-bibliotecas">Bibliotecas</h2>
      <ul className="perfil-bibliotecas">
        {bibliotecas.map((biblioteca) => (
          <li key={biblioteca.id} className="perfil-biblioteca">
            <h3>{biblioteca.nome}</h3>
            <p>{biblioteca.descricao}</p>
            {biblioteca.membro ? (
              <span className="perfil-membro">Membro</span>
            ) : (
              <button
                type="button"
                className="botao botao-pequeno"
                aria-label={`Tornar-se membro de ${biblioteca.nome}`}
                onClick={() => aoEntrar(biblioteca.id)}
              >
                Tornar-se membro
              </button>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
