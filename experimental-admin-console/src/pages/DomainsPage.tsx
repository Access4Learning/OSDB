import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

interface Domain {
  id: string;
  name: string;
}

type SortDir = 'asc' | 'desc';

export default function DomainsPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();

  const [domains, setDomains] = useState<Domain[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const [sortDir, setSortDir] = useState<SortDir>('asc');
  const [showNewDomain, setShowNewDomain] = useState(false);
  const [newDomainId, setNewDomainId] = useState('');
  const [newDomainDesc, setNewDomainDesc] = useState('');
  const [creating, setCreating] = useState(false);

  const fetchDomains = async () => {
    if (!sessionToken || !sharedSecret) return;
    setLoading(true);
    setError(null);
    try {
      const res = await authenticatedFetch('/domains', sessionToken, sharedSecret);
      if (res.status === 401) {
        logout();
        navigate('/login');
        return;
      }
      if (!res.ok) {
        throw new Error(`Failed to fetch domains (${res.status})`);
      }
      const data = await res.json();
      // SIF collection format: { "domains": { "domain": [...] } }
      let list: Domain[];
      if (data.domains?.domain) {
        list = data.domains.domain.map((d: any) => ({ id: d.domainId || d.id, name: d.domainId || d.name }));
      } else if (Array.isArray(data)) {
        list = data;
      } else {
        list = [];
      }
      setDomains(list);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unknown error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDomains();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDelete = async (domain: Domain) => {
    if (!sessionToken || !sharedSecret) return;
    const confirmed = window.confirm(`Delete domain "${domain.name}"?`);
    if (!confirmed) return;

    try {
      const res = await authenticatedFetch(`/domains/${domain.id}`, sessionToken, sharedSecret, {
        method: 'DELETE',
      });
      if (res.status === 401) {
        logout();
        navigate('/login');
        return;
      }
      if (!res.ok) {
        throw new Error(`Failed to delete domain (${res.status})`);
      }
      setDomains((prev) => prev.filter((d) => d.id !== domain.id && d.name !== domain.name));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  const toggleSort = () => {
    setSortDir((prev) => (prev === 'asc' ? 'desc' : 'asc'));
  };

  const handleCreate = async () => {
    if (!sessionToken || !sharedSecret || !newDomainId.trim()) return;
    setCreating(true);
    setError(null);
    try {
      const res = await authenticatedFetch('/domains', sessionToken, sharedSecret, {
        method: 'POST',
        body: JSON.stringify({
          domainId: newDomainId.trim(),
          description: newDomainDesc.trim() || undefined,
        }),
      });
      if (res.status === 401) {
        logout();
        navigate('/login');
        return;
      }
      if (!res.ok) {
        const errBody = await res.text();
        throw new Error(`Failed to create domain (${res.status}): ${errBody}`);
      }
      setNewDomainId('');
      setNewDomainDesc('');
      setShowNewDomain(false);
      fetchDomains();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreating(false);
    }
  };

  const filteredDomains = useMemo(() => {
    const lowerFilter = filter.toLowerCase();
    const filtered = domains.filter((d) => d.name.toLowerCase().includes(lowerFilter));
    return filtered.sort((a, b) => {
      const cmp = a.name.localeCompare(b.name);
      return sortDir === 'asc' ? cmp : -cmp;
    });
  }, [domains, filter, sortDir]);

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-4xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-gray-800">Domains</h1>
            <button
              onClick={fetchDomains}
              className="text-gray-400 hover:text-gray-600 transition-colors"
              title="Refresh"
            >
              ↻
            </button>
          </div>
          <button
            onClick={() => setShowNewDomain(true)}
            className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 transition-colors"
          >
            New Domain
          </button>
        </div>

        {showNewDomain && (
          <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 shadow-sm">
            <h2 className="text-sm font-medium text-gray-700 mb-3">Create New Domain</h2>
            <div className="flex gap-3 items-end">
              <div className="flex-1">
                <label className="block text-xs text-gray-500 mb-1">Domain ID</label>
                <input
                  type="text"
                  value={newDomainId}
                  onChange={(e) => setNewDomainId(e.target.value)}
                  placeholder="e.g. my-district"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div className="flex-1">
                <label className="block text-xs text-gray-500 mb-1">Description (optional)</label>
                <input
                  type="text"
                  value={newDomainDesc}
                  onChange={(e) => setNewDomainDesc(e.target.value)}
                  placeholder="e.g. My School District"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <button
                onClick={handleCreate}
                disabled={creating || !newDomainId.trim()}
                className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {creating ? 'Creating...' : 'Create'}
              </button>
              <button
                onClick={() => { setShowNewDomain(false); setNewDomainId(''); setNewDomainDesc(''); }}
                className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2"
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        <div className="mb-4">
          <input
            type="text"
            placeholder="Filter domains..."
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            className="w-full max-w-xs border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
            {error}
          </div>
        )}

        {loading ? (
          <p className="text-gray-500 text-sm">Loading...</p>
        ) : (
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th
                    className="text-left px-4 py-3 font-medium text-gray-600 cursor-pointer select-none hover:text-gray-900"
                    onClick={toggleSort}
                  >
                    Name {sortDir === 'asc' ? '▲' : '▼'}
                  </th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Delete</th>
                </tr>
              </thead>
              <tbody>
                {filteredDomains.length === 0 ? (
                  <tr>
                    <td colSpan={2} className="px-4 py-6 text-center text-gray-400">
                      No domains found
                    </td>
                  </tr>
                ) : (
                  filteredDomains.map((domain) => (
                    <tr key={domain.id} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <button
                          onClick={() => navigate(`/domains/${domain.name}`)}
                          className="text-blue-600 hover:text-blue-800 hover:underline"
                        >
                          {domain.name}
                        </button>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleDelete(domain)}
                          className="text-red-500 hover:text-red-700 font-medium"
                          title="Delete domain"
                        >
                          ✕
                        </button>
                      </td>
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
