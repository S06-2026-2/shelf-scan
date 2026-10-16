import { AvatarPessoa } from '../../components/AvatarPessoa'
import type { Pessoa } from '../../tipos/social'

export function Amigos({ amigos }: { amigos: Pessoa[] }) {
  return (
    <section aria-labelledby="titulo-amigos" className="perfil-secao">
      <h2 id="titulo-amigos">Amigos</h2>
      <ul className="perfil-amigos">
        {amigos.map((amigo) => (
          <li key={amigo.id} className="perfil-amigo">
            <AvatarPessoa iniciais={amigo.iniciais} cor={amigo.cor} tamanho={52} />
            <span>{amigo.nome}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
