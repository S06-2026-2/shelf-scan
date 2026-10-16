import { CartaoTelegram } from '../components/CartaoTelegram'
import { usuarioAtual } from '../mocks/usuario'
import './Perfil.css'

export function Perfil() {
  return (
    <div className="perfil">
      <header className="perfil-cabecalho">
        <span className="avatar avatar-grande" aria-hidden="true">
          {usuarioAtual.iniciais}
        </span>
        <div>
          <h1>{usuarioAtual.nome}</h1>
          <p className="perfil-arroba">{usuarioAtual.arroba}</p>
        </div>
      </header>

      <CartaoTelegram usuarioId={usuarioAtual.id} />
    </div>
  )
}
