import type { Livro } from '../tipos/livro'

// Dados de exemplo: ainda não existe API de livros no backend.
export const livros: Livro[] = [
  {
    id: '1',
    titulo: 'Grande Sertão: Veredas',
    autor: 'Guimarães Rosa',
    status: 'lendo',
    cor: 3,
    progresso: 64,
  },
  {
    id: '2',
    titulo: 'Dom Casmurro',
    autor: 'Machado de Assis',
    status: 'lido',
    cor: 1,
    concluidoEm: 'jun/2025',
  },
  {
    id: '3',
    titulo: 'Capitães da Areia',
    autor: 'Jorge Amado',
    status: 'emprestado',
    cor: 7,
    emprestadoPara: 'Felipe Loschi',
  },
  {
    id: '4',
    titulo: 'Torto Arado',
    autor: 'Itamar Vieira Junior',
    status: 'quero-ler',
    cor: 6,
    aviso: 'Disponível na Biblioteca Inatel!',
  },
  {
    id: '5',
    titulo: 'Vidas Secas',
    autor: 'Graciliano Ramos',
    status: 'lido',
    cor: 8,
    concluidoEm: 'jan/2025',
  },
  {
    id: '6',
    titulo: 'Quarto de Despejo',
    autor: 'Carolina Maria de Jesus',
    status: 'lendo',
    cor: 5,
    progresso: 18,
  },
  {
    id: '7',
    titulo: 'Memórias Póstumas de Brás Cubas',
    autor: 'Machado de Assis',
    status: 'lido',
    cor: 2,
    concluidoEm: 'mar/2025',
  },
  {
    id: '8',
    titulo: 'A Hora da Estrela',
    autor: 'Clarice Lispector',
    status: 'quero-ler',
    cor: 4,
  },
]
