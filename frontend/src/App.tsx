import { NavLink, Route, Routes } from 'react-router-dom'
import { HistoryPage } from './pages/HistoryPage'
import { PlanPage } from './pages/PlanPage'
import { RoutePage } from './pages/RoutePage'

export default function App() {
  return (
    <div className="app">
      <header className="topbar">
        <NavLink to="/" className="brand">Auto Rota</NavLink>
        <nav className="nav">
          <NavLink to="/" end>Planejar</NavLink>
          <NavLink to="/rotas" end>Histórico</NavLink>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<PlanPage />} />
        <Route path="/rotas" element={<HistoryPage />} />
        <Route path="/rotas/:id" element={<RoutePage />} />
        <Route path="*" element={<main className="page"><p>Página não encontrada.</p></main>} />
      </Routes>
    </div>
  )
}
