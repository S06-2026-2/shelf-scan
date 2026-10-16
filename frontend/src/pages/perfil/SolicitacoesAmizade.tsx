import { AvatarPessoa } from '../../components/AvatarPessoa'
import type { Pessoa } from '../../tipos/social'

interface SolicitacoesAmizadeProps {
  pedidos: Pessoa[]
  aoAceitar: (pessoa: Pessoa) => void
  aoRecusar: (pessoa: Pessoa) => void
}

export function SolicitacoesAmizade({ pedidos, aoAceitar, aoRecusar }: SolicitacoesAmizadeProps) {
  if (pedidos.length === 0) return null

  return (
    <section aria-labelledby="titulo-solicitacoes" className="perfil-secao">
      <h2 id="titulo-solicitacoes">Solicitações de amizade</h2>
      <ul className="perfil-pedidos">
        {pedidos.map((pessoa) => (
          <li key={pessoa.id} className="perfil-pedido">
            <AvatarPessoa iniciais={pessoa.iniciais} cor={pessoa.cor} tamanho={36} />
            <div className="perfil-pedido-texto">
              <strong>{pessoa.nome}</strong>
              {pessoa.amigosEmComum !== undefined && (
                <span>
                  {pessoa.amigosEmComum} {pessoa.amigosEmComum === 1 ? 'amigo' : 'amigos'} em comum
                </span>
              )}
            </div>
            <button
              type="button"
              className="botao botao-primario botao-pequeno"
              aria-label={`Aceitar solicitação de ${pessoa.nome}`}
              onClick={() => aoAceitar(pessoa)}
            >
              Aceitar
            </button>
            <button
              type="button"
              className="botao botao-pequeno"
              aria-label={`Recusar solicitação de ${pessoa.nome}`}
              onClick={() => aoRecusar(pessoa)}
            >
              Recusar
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
