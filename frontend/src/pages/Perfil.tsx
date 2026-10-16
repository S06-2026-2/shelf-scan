import { useState } from 'react'
import { CartaoTelegram } from '../components/CartaoTelegram'
import { useLivros } from '../estado/LivrosContext'
import { amigosIniciais, bibliotecasIniciais, pedidosIniciais } from '../mocks/social'
import { usuarioAtual } from '../mocks/usuario'
import type { Pessoa } from '../tipos/social'
import { Amigos } from './perfil/Amigos'
import { Bibliotecas } from './perfil/Bibliotecas'
import { Recomendacoes } from './perfil/Recomendacoes'
import { SolicitacoesAmizade } from './perfil/SolicitacoesAmizade'
import './Perfil.css'

export function Perfil() {
  const { livros } = useLivros()
  const [pedidos, setPedidos] = useState(pedidosIniciais)
  const [amigos, setAmigos] = useState(amigosIniciais)
  const [bibliotecas, setBibliotecas] = useState(bibliotecasIniciais)

  function aceitar(pessoa: Pessoa) {
    setPedidos((atuais) => atuais.filter((pedido) => pedido.id !== pessoa.id))
    setAmigos((atuais) => [pessoa, ...atuais])
  }

  function recusar(pessoa: Pessoa) {
    setPedidos((atuais) => atuais.filter((pedido) => pedido.id !== pessoa.id))
  }

  function entrarNaBiblioteca(id: string) {
    setBibliotecas((atuais) =>
      atuais.map((biblioteca) => (biblioteca.id === id ? { ...biblioteca, membro: true } : biblioteca)),
    )
  }

  const totalBibliotecas = bibliotecas.filter((biblioteca) => biblioteca.membro).length

  return (
    <div className="perfil">
      <header className="perfil-cabecalho">
        <span className="avatar avatar-grande" aria-hidden="true">
          {usuarioAtual.iniciais}
        </span>
        <div>
          <h1>{usuarioAtual.nome}</h1>
          <p className="perfil-arroba">{usuarioAtual.arroba}</p>
          <dl className="perfil-numeros">
            <div>
              <dt>livros na estante</dt>
              <dd>{livros.length}</dd>
            </div>
            <div>
              <dt>amigos</dt>
              <dd>{amigos.length}</dd>
            </div>
            <div>
              <dt>bibliotecas</dt>
              <dd>{totalBibliotecas}</dd>
            </div>
          </dl>
        </div>
      </header>

      <CartaoTelegram usuarioId={usuarioAtual.id} />

      <SolicitacoesAmizade pedidos={pedidos} aoAceitar={aceitar} aoRecusar={recusar} />
      <Amigos amigos={amigos} />
      <Bibliotecas bibliotecas={bibliotecas} aoEntrar={entrarNaBiblioteca} />
      <Recomendacoes />
    </div>
  )
}
