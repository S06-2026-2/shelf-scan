import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import type { EstadoDaLeitura, LeituraDaFoto } from '../api/ocrContrato'
import { Passos } from '../components/Passos'
import { useLivros } from '../estado/LivrosContext'
import type { Livro, StatusLeitura } from '../tipos/livro'
import './ConfirmarLivro.css'

interface EstadoConfirmacao {
  leitura: LeituraDaFoto
  imagem: Blob
}

type StatusEscolhivel = Exclude<StatusLeitura, 'emprestado'>

const STATUS: { valor: StatusEscolhivel; rotulo: string }[] = [
  { valor: 'quero-ler', rotulo: 'Quero ler' },
  { valor: 'lendo', rotulo: 'Lendo' },
  { valor: 'lido', rotulo: 'Lido' },
]

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez']

const SELOS: Record<EstadoDaLeitura, string> = {
  estruturado: 'Reconhecido via OCR',
  'nao-estruturado': 'Texto lido, sem separar os campos',
  'nenhum-texto': 'Nenhum texto encontrado',
}

const AVISOS: Partial<Record<EstadoDaLeitura, string>> = {
  'nao-estruturado':
    'Li o texto da foto, mas não consegui separar título e autor. Confira o texto abaixo e preencha os campos.',
  'nenhum-texto': 'Não encontrei texto nessa foto. Preencha os dados à mão ou tire outra foto.',
}

// A tela só faz sentido vindo da captura; se alguém abrir direto o endereço, volta para lá.
function lerEstado(estado: unknown): EstadoConfirmacao | null {
  if (typeof estado !== 'object' || estado === null) return null
  const { leitura, imagem } = estado as Partial<EstadoConfirmacao>
  return leitura && imagem instanceof Blob ? { leitura, imagem } : null
}

function corDoTitulo(titulo: string): number {
  const soma = [...titulo].reduce((total, letra) => total + letra.charCodeAt(0), 0)
  return (soma % 8) + 1
}

function mesEAnoAtual(): string {
  const hoje = new Date()
  return `${MESES[hoje.getMonth()]}/${hoje.getFullYear()}`
}

function opcional(texto: string): string | undefined {
  const limpo = texto.trim()
  return limpo === '' ? undefined : limpo
}

export function ConfirmarLivro() {
  const estado = lerEstado(useLocation().state)
  if (!estado) {
    return <Navigate to="/estante/adicionar" replace />
  }
  return <Formulario leitura={estado.leitura} imagem={estado.imagem} />
}

function Formulario({ leitura, imagem }: EstadoConfirmacao) {
  const navegar = useNavigate()
  const { adicionarLivro } = useLivros()
  const { livro: reconhecido, textoBruto, estado } = leitura

  const [previa, setPrevia] = useState<string | null>(null)
  const [titulo, setTitulo] = useState(reconhecido.titulo)
  const [autor, setAutor] = useState(reconhecido.autor)
  const [editora, setEditora] = useState(reconhecido.editora ?? '')
  const [volume, setVolume] = useState(reconhecido.volume ?? '')
  // Ano, gênero e sinopse não vêm do OCR (o enriquecimento é do database-service): ficam à mão.
  const [ano, setAno] = useState('')
  const [genero, setGenero] = useState('')
  const [sinopse, setSinopse] = useState('')
  const [status, setStatus] = useState<StatusEscolhivel>('quero-ler')
  const [erro, setErro] = useState<string | null>(null)

  // Lê a imagem de forma assíncrona; a foto já foi reduzida na captura, então é pequena.
  useEffect(() => {
    const leitor = new FileReader()
    leitor.onload = () => setPrevia(String(leitor.result))
    leitor.readAsDataURL(imagem)
    return () => leitor.abort()
  }, [imagem])

  function salvar(evento: FormEvent) {
    evento.preventDefault()
    if (titulo.trim() === '') {
      setErro('Informe o título do livro.')
      return
    }

    const livro: Livro = {
      id: crypto.randomUUID(),
      titulo: titulo.trim(),
      autor: autor.trim() || 'Autor não informado',
      status,
      cor: corDoTitulo(titulo),
      progresso: status === 'lendo' ? 0 : undefined,
      concluidoEm: status === 'lido' ? mesEAnoAtual() : undefined,
      volume: opcional(volume),
      editora: opcional(editora),
      ano: opcional(ano),
      genero: opcional(genero),
      sinopse: opcional(sinopse),
    }
    adicionarLivro(livro)
    navegar('/estante')
  }

  const aviso = AVISOS[estado]

  return (
    <div className="confirmar">
      <Passos atual={2} />

      <header>
        <h1>Confirme os dados do livro</h1>
        <p className="confirmar-descricao">Corrija o que o reconhecimento errou antes de salvar.</p>
      </header>

      <form className="confirmar-grade" onSubmit={salvar}>
        <div className="confirmar-foto">
          {previa && <img src={previa} alt="Foto enviada do livro" />}
          <span className={estado === 'estruturado' ? 'confirmar-selo' : 'confirmar-selo atencao'}>
            {SELOS[estado]}
          </span>
          <Link to="/estante/adicionar" className="confirmar-refazer">
            Tirar outra foto
          </Link>
        </div>

        <div className="confirmar-campos">
          {aviso && (
            <p className="confirmar-aviso campo-largo" role="status">
              {aviso}
            </p>
          )}

          {textoBruto !== '' && (
            <details className="confirmar-texto-bruto campo-largo" open={estado === 'nao-estruturado'}>
              <summary>Texto lido na foto</summary>
              <pre>{textoBruto}</pre>
            </details>
          )}

          <div className="campo campo-largo">
            <label htmlFor="titulo">Título</label>
            <input id="titulo" value={titulo} onChange={(e) => setTitulo(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="autor">Autor(es)</label>
            <input id="autor" value={autor} onChange={(e) => setAutor(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="editora">Editora</label>
            <input id="editora" value={editora} onChange={(e) => setEditora(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="volume">Volume</label>
            <input id="volume" value={volume} onChange={(e) => setVolume(e.target.value)} />
          </div>
          <div className="campo">
            <label htmlFor="ano">Ano</label>
            <input id="ano" inputMode="numeric" value={ano} onChange={(e) => setAno(e.target.value)} />
          </div>
          <div className="campo campo-largo">
            <label htmlFor="genero">Gênero</label>
            <input id="genero" value={genero} onChange={(e) => setGenero(e.target.value)} />
          </div>
          <div className="campo campo-largo">
            <label htmlFor="sinopse">Sinopse</label>
            <textarea id="sinopse" rows={4} value={sinopse} onChange={(e) => setSinopse(e.target.value)} />
          </div>

          <fieldset className="campo campo-largo confirmar-status">
            <legend>Status de leitura</legend>
            {STATUS.map((opcao) => (
              <label key={opcao.valor}>
                <input
                  type="radio"
                  name="status"
                  value={opcao.valor}
                  checked={status === opcao.valor}
                  onChange={() => setStatus(opcao.valor)}
                />
                {opcao.rotulo}
              </label>
            ))}
          </fieldset>

          {erro && (
            <p className="confirmar-erro campo-largo" role="alert">
              {erro}
            </p>
          )}

          <div className="confirmar-acoes campo-largo">
            <Link to="/estante" className="botao">
              Cancelar
            </Link>
            <button type="submit" className="botao botao-primario">
              Salvar na estante
            </button>
          </div>
        </div>
      </form>
    </div>
  )
}
