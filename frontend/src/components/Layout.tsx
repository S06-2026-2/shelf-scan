import { NavLink, Outlet } from 'react-router-dom'
import { usuarioAtual } from '../mocks/usuario'
import { Logo } from './Logo'
import './Layout.css'

const LINKS = [
  { para: '/estante', rotulo: 'Estante' },
  { para: '/perfil', rotulo: 'Perfil' },
]

export function Layout() {
  return (
    <div className="layout">
      <aside className="layout-menu">
        <div className="layout-marca">
          <Logo tamanho={30} />
        </div>
        <nav aria-label="Principal" className="layout-navegacao">
          {LINKS.map((link) => (
            <NavLink
              key={link.para}
              to={link.para}
              className={({ isActive }) => (isActive ? 'layout-link ativo' : 'layout-link')}
            >
              {link.rotulo}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="layout-corpo">
        <header className="layout-topo">
          <span className="layout-usuario">{usuarioAtual.nome}</span>
          <span className="avatar" aria-hidden="true">
            {usuarioAtual.iniciais}
          </span>
        </header>
        <main className="layout-conteudo">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
