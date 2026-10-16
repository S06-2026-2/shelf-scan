import type { StatusLeitura } from '../tipos/livro'
import './SeloStatus.css'

const ROTULOS: Record<StatusLeitura, string> = {
  lendo: 'Lendo',
  lido: 'Lido',
  'quero-ler': 'Quero ler',
  emprestado: 'Emprestado',
}

export function SeloStatus({ status }: { status: StatusLeitura }) {
  return <span className={`selo selo-${status}`}>{ROTULOS[status]}</span>
}
