import './AvatarPessoa.css'

interface AvatarPessoaProps {
  iniciais: string
  cor: number
  tamanho?: number
}

// O nome da pessoa sempre aparece em texto ao lado, então o avatar é decorativo.
export function AvatarPessoa({ iniciais, cor, tamanho = 40 }: AvatarPessoaProps) {
  return (
    <span
      className={`avatar-pessoa avatar-cor-${cor}`}
      style={{ width: tamanho, height: tamanho, fontSize: tamanho * 0.34 }}
      aria-hidden="true"
    >
      {iniciais}
    </span>
  )
}
