import { useEffect, useState, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

interface Solution {
  id: string;
  solutionId: string;
  description: string;
}

type SortDir = 'asc' | 'desc';

export default function SolutionsPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();
  const { domainId } = useParams<{ domainId: string }>();

  const [solutions, setSolutions] = useState<Solution[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [showNew, setShowNew] = useState(false);
  const [newSolutionId, setNewSolutionId] = useState('');
  const [newDescription, setNewDescription] = useState('');
  const [creating, setCreating] = useState(false);

  const fetchSolutions = async () => {
    if (!sessionToken || !sharedSecret || !domainId) return;
    setLoading(true);
    setError(null);
    try {
      const res = await authenticatedFetch(`/${domainId}/solutions`, sessionToken, sharedSecret);
      if (res.status === 401) { logout(); navigate('/login'); return; }
      if (!res.ok) throw new Error(`Failed to fetch solutions (${res.status})`);
      const data = await res.json();
      const list: Solution[] = data.solutions?.solution
        ? data.solutions.solution.map((s: any) => ({ id: s.id, solutionId: s.solutionId, description: s.description || '' }))
        : [];
      setSolutions(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchSolutions(); }, []);

  const handleDelete = async (sol: Solution) => {
    if (!sessionToken || !sharedSecret) return;
    if (!window.confirm(`Delete solution "${sol.solutionId}"?`)) return;
    try {
      const res = await authenticatedFetch(`/${domainId}/solutions/${sol.solutionId}`, sessionToken, sharedSecret, { method: 'DELETE' });
      if (res.status === 401) { logout(); navigate('/login'); return; }
      if (!res.ok) throw new Error(`Failed to delete solution (${res.status})`);
      setSolutions((prev) => prev.filter((s) => s.id !== sol.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  const handleCreate = async () => {
    if (!sessionToken || !sharedSecret || !newSolutionId.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const res = await authenticatedFetch(`/${domainId}/solutions`, sessionToken, sharedSecret, {
        method: 'POST',
        body: JSON.stringify({ solutionId: newSolutionId.trim(), description: newDescription.trim() || undefined }),
      });
      if (res.status === 401) { logout(); navigate('/login'); return; }
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to create solution (${res.status}): ${t}`); }
      setNewSolutionId(''); setNewDescription(''); setShowNew(false);
      fetchSolutions();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreating(false);
    }
  };

  const filtered = useMemo(() => {
    const lf = filter.toLowerCase();
    return solutions
      .filter((s) => s.solutionId.toLowerCase().includes(lf))
      .sort((a, b) => { const c = a.solutionId.localeCompare(b.solutionId); return sortDir === 'asc' ? c : -c; });
  }, [solutions, filter, sortDir]);

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-4xl mx-auto px-6 py-8">
        <button onClick={() => navigate('/domains')} className="text-sm text-blue-600 hover:text-blue-800 mb-4 inline-block">← Back to Domains</button>
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-gray-800">{domainId} Domain Overview</h1>
            <button onClick={fetchSolutions} className="text-gray-400 hover:text-gray-600" title="Refresh">↻</button>
          </div>
          <button onClick={() => setShowNew(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">New Solution</button>
        </div>

        {showNew && (
          <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 shadow-sm">
            <h2 className="text-sm font-medium text-gray-700 mb-3">Create New Solution</h2>
            <div className="flex gap-3 items-end">
              <div className="flex-1">
                <label className="block text-xs text-gray-500 mb-1">Solution ID</label>
                <input type="text" value={newSolutionId} onChange={(e) => setNewSolutionId(e.target.value)} placeholder="e.g. prod" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div className="flex-1">
                <label className="block text-xs text-gray-500 mb-1">Description</label>
                <input type="text" value={newDescription} onChange={(e) => setNewDescription(e.target.value)} placeholder="e.g. Production solution" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <button onClick={handleCreate} disabled={creating || !newSolutionId.trim()} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creating ? 'Creating...' : 'Create'}</button>
              <button onClick={() => { setShowNew(false); setNewSolutionId(''); setNewDescription(''); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2">Cancel</button>
            </div>
          </div>
        )}

        <div className="mb-4">
          <input type="text" placeholder="Filter solutions..." value={filter} onChange={(e) => setFilter(e.target.value)} className="w-full max-w-xs border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
        </div>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        {loading ? (
          <p className="text-gray-500 text-sm">Loading...</p>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="text-left px-4 py-3 font-medium text-gray-600 cursor-pointer select-none hover:text-gray-900" onClick={() => setSortDir(d => d === 'asc' ? 'desc' : 'asc')}>Name {sortDir === 'asc' ? '▲' : '▼'}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">ID</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Delete</th>
                </tr>
              </thead>
              <tbody>
                {filtered.length === 0 ? (
                  <tr><td colSpan={3} className="px-4 py-6 text-center text-gray-400">No solutions found</td></tr>
                ) : (
                  filtered.map((sol) => (
                    <tr key={sol.id} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="px-4 py-3"><button onClick={() => navigate(`/domains/${domainId}/solutions/${sol.solutionId}`)} className="text-blue-600 hover:text-blue-800 hover:underline">{sol.solutionId}</button></td>
                      <td className="px-4 py-3 text-gray-600">{sol.id}</td>
                      <td className="px-4 py-3 text-right"><button onClick={() => handleDelete(sol)} className="text-red-500 hover:text-red-700 font-medium" title="Delete solution">✕</button></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </main>
    </div>
  );
}
