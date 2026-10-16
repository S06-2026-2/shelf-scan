import './Passos.css'

const ETAPAS = ['Capturar', 'Confirmar dados']

export function Passos({ atual }: { atual: 1 | 2 }) {
  return (
    <ol className="passos" aria-label="Etapas">
      {ETAPAS.map((rotulo, indice) => {
        const numero = indice + 1
        const classe = numero < atual ? 'passo feito' : numero === atual ? 'passo atual' : 'passo'
        return (
          <li key={rotulo} className={classe} aria-current={numero === atual ? 'step' : undefined}>
            <span className="passo-numero">{numero < atual ? '✓' : numero}</span>
            {rotulo}
          </li>
        )
      })}
    </ol>
  )
}
