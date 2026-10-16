import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { AdicionarLivro } from './pages/AdicionarLivro'
import { ConfirmarLivro } from './pages/ConfirmarLivro'
import { Estante } from './pages/Estante'
import { Perfil } from './pages/Perfil'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/perfil" replace />} />
        <Route path="perfil" element={<Perfil />} />
        <Route path="estante" element={<Estante />} />
        <Route path="estante/adicionar" element={<AdicionarLivro />} />
        <Route path="estante/adicionar/confirmar" element={<ConfirmarLivro />} />
        <Route path="*" element={<Navigate to="/perfil" replace />} />
      </Route>
    </Routes>
  )
}

export default App
