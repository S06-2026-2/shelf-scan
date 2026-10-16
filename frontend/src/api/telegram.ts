// Endereço do notification-service. Em outros ambientes, defina VITE_NOTIFICATION_API_URL.
const URL_BASE: string = import.meta.env.VITE_NOTIFICATION_API_URL ?? 'http://localhost:8341'

async function chamar(caminho: string, opcoes?: RequestInit): Promise<Response> {
  let resposta: Response
  try {
    resposta = await fetch(`${URL_BASE}${caminho}`, opcoes)
  } catch {
    throw new Error('Não foi possível falar com o serviço de notificações. Ele está no ar?')
  }

  if (resposta.status === 503) {
    throw new Error('O bot do Telegram não está configurado no servidor.')
  }
  if (!resposta.ok) {
    throw new Error(`O serviço de notificações respondeu com erro (${resposta.status}).`)
  }
  return resposta
}

export async function consultarStatus(usuarioId: string): Promise<boolean> {
  const resposta = await chamar(`/telegram/vinculos/${encodeURIComponent(usuarioId)}`)
  const corpo: { conectado: boolean } = await resposta.json()
  return corpo.conectado
}

export async function gerarLink(usuarioId: string): Promise<string> {
  const resposta = await chamar('/telegram/vinculos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usuarioId }),
  })
  const corpo: { link: string } = await resposta.json()
  return corpo.link
}

export async function desconectar(usuarioId: string): Promise<void> {
  await chamar(`/telegram/vinculos/${encodeURIComponent(usuarioId)}`, { method: 'DELETE' })
}
