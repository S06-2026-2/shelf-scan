export function EmConstrucao({ titulo, descricao }: { titulo: string; descricao: string }) {
  return (
    <div>
      <h1>{titulo}</h1>
      <p style={{ color: 'var(--ink-muted)', maxWidth: '60ch' }}>{descricao}</p>
    </div>
  )
}
