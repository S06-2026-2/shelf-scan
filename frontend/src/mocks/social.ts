import type { Biblioteca, Pessoa, Recomendacao } from '../tipos/social'

// Dados de exemplo: ainda não existem APIs de amizades, bibliotecas nem recomendações.
export const amigosIniciais: Pessoa[] = [
  { id: 'a1', nome: 'Ana Clara', iniciais: 'AC', cor: 2 },
  { id: 'a2', nome: 'Rafa Melo', iniciais: 'RM', cor: 4 },
  { id: 'a3', nome: 'João Tavares', iniciais: 'JT', cor: 6 },
  { id: 'a4', nome: 'Carla Barbosa', iniciais: 'CB', cor: 3 },
  { id: 'a5', nome: 'Lucas Santos', iniciais: 'LS', cor: 5 },
]

export const pedidosIniciais: Pessoa[] = [
  { id: 'p1', nome: 'Felipe Loschi', iniciais: 'FL', cor: 3, amigosEmComum: 3 },
  { id: 'p2', nome: 'Pedro Dias', iniciais: 'PD', cor: 5, amigosEmComum: 1 },
]

export const bibliotecasIniciais: Biblioteca[] = [
  {
    id: 'b1',
    nome: 'Biblioteca Inatel',
    descricao: '1.240 títulos · Santa Rita do Sapucaí, MG',
    membro: true,
  },
  {
    id: 'b2',
    nome: 'Biblioteca Municipal Cora Coralina',
    descricao: '3.980 títulos · acervo geral',
    membro: true,
  },
  {
    id: 'b3',
    nome: 'Sebo Vira-Página',
    descricao: '620 títulos · usados e trocas',
    membro: false,
  },
]

export const recomendacoes: Recomendacao[] = [
  { id: 'r1', titulo: 'O Cortiço', autor: 'Aluísio Azevedo', cor: 7 },
  { id: 'r2', titulo: 'Iracema', autor: 'José de Alencar', cor: 4 },
  { id: 'r3', titulo: 'Macunaíma', autor: 'Mário de Andrade', cor: 2 },
  { id: 'r4', titulo: 'Sagarana', autor: 'Guimarães Rosa', cor: 3 },
]
