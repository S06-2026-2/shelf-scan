import { useEffect, useRef, useState, type DragEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { modoDemonstracao, reconhecerLivro } from '../api/ocr'
import { Passos } from '../components/Passos'
import { reduzirImagem } from '../utilitarios/imagem'
import './AdicionarLivro.css'

const TIPOS_ACEITOS = ['image/jpeg', 'image/png', 'image/webp']
const LIMITE_BYTES = 10 * 1024 * 1024

function formatarTamanho(bytes: number): string {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

export function AdicionarLivro() {
  const navegar = useNavigate()
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [previa, setPrevia] = useState<string | null>(null)
  const [lendo, setLendo] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const [arrastando, setArrastando] = useState(false)

  // A URL da pré-visualização é criada ao escolher o arquivo e liberada ao trocar ou sair da tela.
  const urlAtual = useRef<string | null>(null)

  useEffect(() => {
    return () => {
      if (urlAtual.current) URL.revokeObjectURL(urlAtual.current)
    }
  }, [])

  function trocarPrevia(novo: File) {
    if (urlAtual.current) URL.revokeObjectURL(urlAtual.current)
    urlAtual.current = URL.createObjectURL(novo)
    setPrevia(urlAtual.current)
  }

  function escolher(novo: File | undefined) {
    if (!novo) return
    if (!TIPOS_ACEITOS.includes(novo.type)) {
      setErro('Use um arquivo JPG, PNG ou WebP.')
      return
    }
    if (novo.size > LIMITE_BYTES) {
      setErro('A imagem passa de 10 MB. Escolha uma menor.')
      return
    }
    setErro(null)
    setArquivo(novo)
    trocarPrevia(novo)
  }

  function aoSoltar(evento: DragEvent<HTMLLabelElement>) {
    evento.preventDefault()
    setArrastando(false)
    if (!lendo) escolher(evento.dataTransfer.files[0])
  }

  async function reconhecer() {
    if (!arquivo) return
    setLendo(true)
    setErro(null)
    try {
      const imagem = await reduzirImagem(arquivo)
      const reconhecido = await reconhecerLivro(imagem)
      navegar('/estante/adicionar/confirmar', { state: { reconhecido, imagem } })
    } catch (falha) {
      setErro(falha instanceof Error ? falha.message : 'Algo deu errado.')
    } finally {
      setLendo(false)
    }
  }

  return (
    <div className="adicionar">
      <Passos atual={1} />

      <header>
        <h1>Adicionar livro via OCR</h1>
        <p className="adicionar-descricao">Envie uma foto nítida da capa ou da lombada do livro.</p>
      </header>

      <label
        className={arrastando ? 'adicionar-area arrastando' : 'adicionar-area'}
        onDragOver={(evento) => {
          evento.preventDefault()
          setArrastando(true)
        }}
        onDragLeave={() => setArrastando(false)}
        onDrop={aoSoltar}
      >
        <input
          type="file"
          className="adicionar-input"
          accept={TIPOS_ACEITOS.join(',')}
          disabled={lendo}
          onChange={(evento) => escolher(evento.target.files?.[0])}
        />
        {previa ? (
          <img src={previa} alt="Pré-visualização da foto escolhida" className="adicionar-previa" />
        ) : (
          <>
            <strong>Arraste a foto aqui ou clique para escolher</strong>
            <span>JPG, PNG ou WebP, até 10 MB</span>
          </>
        )}
      </label>

      {arquivo && (
        <p className="adicionar-arquivo">
          {arquivo.name} · {formatarTamanho(arquivo.size)}
        </p>
      )}
      {erro && (
        <p className="adicionar-erro" role="alert">
          {erro}
        </p>
      )}
      {modoDemonstracao && (
        <p className="adicionar-aviso">Modo demonstração: o reconhecimento ainda é simulado.</p>
      )}

      <div className="adicionar-acoes">
        <Link to="/estante" className="botao">
          Cancelar
        </Link>
        <button
          type="button"
          className="botao botao-primario"
          onClick={reconhecer}
          disabled={!arquivo || lendo}
        >
          {lendo ? 'Lendo a capa…' : 'Reconhecer livro'}
        </button>
      </div>
    </div>
  )
}
