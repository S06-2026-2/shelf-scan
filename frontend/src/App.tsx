import { Navigate, Route, Routes } from 'react-router-dom'
import { EmConstrucao } from './components/EmConstrucao'
import { Layout } from './components/Layout'
import { Estante } from './pages/Estante'
import { Perfil } from './pages/Perfil'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/perfil" replace />} />
        <Route path="perfil" element={<Perfil />} />
        <Route path="estante" element={<Estante />} />
        <Route
          path="estante/adicionar"
          element={
            <EmConstrucao
              titulo="Adicionar livro via OCR"
              descricao="Envio da foto da capa ou da lombada para reconhecer o livro."
            />
          }
        />
        <Route
          path="estante/adicionar/confirmar"
          element={
            <EmConstrucao
              titulo="Confirmar dados do livro"
              descricao="Revisão dos dados reconhecidos antes de salvar na estante."
            />
          }
        />
        <Route path="*" element={<Navigate to="/perfil" replace />} />
      </Route>
    </Routes>
  )
}

export default App
