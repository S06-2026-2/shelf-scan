// Enquanto não há login, todas as telas usam este usuário. Com VITE_USUARIO_ID, o id é o do banco
// (e do cartão do Telegram); sem ele, o usuário de exemplo `teste-1`.
export const usuarioAtual = {
  id: import.meta.env.VITE_USUARIO_ID || 'teste-1',
  nome: 'Marina Duarte',
  arroba: '@marinaduarte',
  iniciais: 'MD',
}
