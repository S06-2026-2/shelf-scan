import { Logo } from './components/Logo'
import './App.css'

const CORES = [
  { nome: 'Acento', variavel: '--accent' },
  { nome: 'Acento forte', variavel: '--accent-strong' },
  { nome: 'Acento suave', variavel: '--accent-soft' },
  { nome: 'Dourado', variavel: '--gold' },
  { nome: 'Texto', variavel: '--ink' },
  { nome: 'Superfície', variavel: '--surface' },
]

const ESTADOS = [
  { nome: 'Lendo', cor: '--status-reading', fundo: '--status-reading-bg' },
  { nome: 'Quero ler', cor: '--status-wishlist', fundo: '--status-wishlist-bg' },
  { nome: 'Lido', cor: '--status-done', fundo: '--status-done-bg' },
  { nome: 'Emprestado', cor: '--status-loan', fundo: '--status-loan-bg' },
]

function App() {
  return (
    <main className="marca">
      <h1>Identidade visual</h1>

      <section>
        <h2>Logo</h2>
        <div className="marca-linha">
          <Logo tamanho={56} />
          <Logo tamanho={32} />
          <Logo tamanho={32} somenteIcone />
          <Logo tamanho={16} somenteIcone />
        </div>
      </section>

      <section>
        <h2>Cores</h2>
        <div className="marca-linha">
          {CORES.map((cor) => (
            <div key={cor.variavel} className="amostra">
              <span
                className="amostra-cor"
                style={{ background: `var(${cor.variavel})` }}
              />
              <span>{cor.nome}</span>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2>Estados de leitura</h2>
        <div className="marca-linha">
          {ESTADOS.map((estado) => (
            <span
              key={estado.nome}
              className="selo"
              style={{
                color: `var(${estado.cor})`,
                background: `var(${estado.fundo})`,
              }}
            >
              {estado.nome}
            </span>
          ))}
        </div>
      </section>
    </main>
  )
}

export default App
