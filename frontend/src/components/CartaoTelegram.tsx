import { useEffect, useState } from 'react'
import { consultarStatus, desconectar, gerarLink } from '../api/telegram'
import './CartaoTelegram.css'

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'conectado' }
  | { tipo: 'desconectado' }
  | { tipo: 'aguardando'; link: string }
  | { tipo: 'erro'; mensagem: string }

const INTERVALO_VERIFICACAO_MS = 3000

function mensagemDe(erro: unknown): string {
  return erro instanceof Error ? erro.message : 'Algo deu errado.'
}

export function CartaoTelegram({ usuarioId }: { usuarioId: string }) {
  const [estado, setEstado] = useState<Estado>({ tipo: 'carregando' })
  const [ocupado, setOcupado] = useState(false)

  useEffect(() => {
    let cancelado = false
    consultarStatus(usuarioId)
      .then((conectado) => {
        if (!cancelado) setEstado({ tipo: conectado ? 'conectado' : 'desconectado' })
      })
      .catch((erro) => {
        if (!cancelado) setEstado({ tipo: 'erro', mensagem: mensagemDe(erro) })
      })
    return () => {
      cancelado = true
    }
  }, [usuarioId])

  // Enquanto espera a pessoa tocar em "Iniciar" no Telegram, confere de tempos em tempos.
  const aguardando = estado.tipo === 'aguardando'
  useEffect(() => {
    if (!aguardando) return
    const relogio = setInterval(() => {
      consultarStatus(usuarioId)
        .then((conectado) => {
          if (conectado) setEstado({ tipo: 'conectado' })
        })
        .catch(() => {
          // uma falha isolada não deve tirar a pessoa da espera; a próxima tentativa resolve
        })
    }, INTERVALO_VERIFICACAO_MS)
    return () => clearInterval(relogio)
  }, [aguardando, usuarioId])

  async function conectar() {
    setOcupado(true)
    try {
      const link = await gerarLink(usuarioId)
      setEstado({ tipo: 'aguardando', link })
    } catch (erro) {
      setEstado({ tipo: 'erro', mensagem: mensagemDe(erro) })
    } finally {
      setOcupado(false)
    }
  }

  async function desconectarTelegram() {
    setOcupado(true)
    try {
      await desconectar(usuarioId)
      setEstado({ tipo: 'desconectado' })
    } catch (erro) {
      setEstado({ tipo: 'erro', mensagem: mensagemDe(erro) })
    } finally {
      setOcupado(false)
    }
  }

  return (
    <section className="cartao-telegram" aria-labelledby="titulo-telegram">
      <div className="cartao-telegram-texto">
        <h2 id="titulo-telegram">Avisos no Telegram</h2>
        <p>{descricao(estado)}</p>
      </div>

      <div className="cartao-telegram-acoes">
        {estado.tipo === 'desconectado' && (
          <button type="button" className="botao botao-primario" onClick={conectar} disabled={ocupado}>
            Conectar Telegram
          </button>
        )}

        {estado.tipo === 'aguardando' && (
          <>
            <a className="botao botao-primario" href={estado.link} target="_blank" rel="noreferrer">
              Abrir no Telegram
            </a>
            <button type="button" className="botao" onClick={conectar} disabled={ocupado}>
              Gerar novo link
            </button>
          </>
        )}

        {estado.tipo === 'conectado' && (
          <button type="button" className="botao" onClick={desconectarTelegram} disabled={ocupado}>
            Desconectar
          </button>
        )}

        {estado.tipo === 'erro' && (
          <button type="button" className="botao" onClick={() => window.location.reload()}>
            Tentar de novo
          </button>
        )}
      </div>
    </section>
  )
}

function descricao(estado: Estado): string {
  switch (estado.tipo) {
    case 'carregando':
      return 'Verificando a conexão…'
    case 'conectado':
      return 'Conectado. Você recebe aqui os avisos dos seus livros.'
    case 'desconectado':
      return 'Receba um aviso no Telegram toda vez que um livro seu for salvo.'
    case 'aguardando':
      return 'Abra o Telegram e toque em Iniciar. Esta página atualiza sozinha quando conectar. O link vale por 15 minutos.'
    case 'erro':
      return estado.mensagem
  }
}
