import './CapaLivro.css'

interface CapaLivroProps {
  titulo: string
  autor: string
  cor: number
}

// A informação já aparece em texto no cartão, então a capa é decorativa para leitores de tela.
export function CapaLivro({ titulo, autor, cor }: CapaLivroProps) {
  return (
    <div className={`capa capa-${cor}`} aria-hidden="true">
      <span className="capa-filete" />
      <span className="capa-titulo">{titulo}</span>
      <span className="capa-autor">{autor}</span>
    </div>
  )
}
