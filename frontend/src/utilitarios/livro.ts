import type { Livro, NovoLivro } from '../tipos/livro'

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

// A mesma cor para o mesmo título, entre 1 e 8.
export function corDoTitulo(titulo: string): number {
  const soma = [...titulo].reduce((total, letra) => total + letra.charCodeAt(0), 0)
  return (soma % 8) + 1
}

// mes de 1 a 12.
export function mesEAno(ano: number, mes: number): string {
  return `${MESES[mes - 1]}/${ano}`
}

export function textoDosAutores(autores: string[]): string {
  return autores.length > 0 ? autores.join(', ') : 'Autor não informado'
}

// Livro de exemplo do modo demonstração: monta o que o banco devolveria, sem rede.
export function livroLocal(novo: NovoLivro): Livro {
  const hoje = new Date()
  return {
    id: crypto.randomUUID(),
    titulo: novo.titulo,
    autor: textoDosAutores(novo.autores),
    status: novo.status,
    cor: corDoTitulo(novo.titulo),
    progresso: novo.status === 'lendo' ? 0 : undefined,
    concluidoEm: novo.status === 'lido' ? mesEAno(hoje.getFullYear(), hoje.getMonth() + 1) : undefined,
    volume: novo.volume,
    genero: novo.genero,
    sinopse: novo.sinopse,
  }
}
