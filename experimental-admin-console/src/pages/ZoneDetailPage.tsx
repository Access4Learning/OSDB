import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

interface ContextEntry {
  // `id` is the SIF context business identifier (serialized as JSON "id").
  // Used as the unique key for expand/collapse and context-services lookups.
  id: string;
  description: string;
  createdAt: string;
}

interface ContextServiceEntry {
  id: string;
  contextId: string;
  serviceName: string;
  serviceType: string;
  createdAt: string;
}

interface SolutionServiceEntry {
  id: string;
  serviceName: string;
  serviceType: string;
  createdAt: string;
}

export default function ZoneDetailPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();
  const { domainId, solutionId, zoneId } = useParams<{ domainId: string; solutionId: string; zoneId: string }>();

  const [contexts, setContexts] = useState<ContextEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Add context form
  const [showNewContext, setShowNewContext] = useState(false);
  const [newContextId, setNewContextId] = useState('');
  const [newContextDescription, setNewContextDescription] = useState('');
  const [creatingContext, setCreatingContext] = useState(false);

  // Expanded contexts (show services)
  const [expandedContexts, setExpandedContexts] = useState<Set<string>>(new Set());
  const [contextServices, setContextServices] = useState<Record<string, ContextServiceEntry[]>>({});
  const [solutionServices, setSolutionServices] = useState<SolutionServiceEntry[]>([]);
  const [loadingServices, setLoadingServices] = useState<Set<string>>(new Set());

  const authFetch = async (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts, extraHeaders);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  const fetchContexts = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/zones/${zoneId}/contexts`);
    if (!res) return;
    if (!res.ok) { setError(`Failed to fetch contexts (${res.status})`); return; }
    const data = await res.json();
    const list = data.contexts?.context || [];
    setContexts(Array.isArray(list) ? list : []);
  };

  const fetchSolutionServices = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/solution-services`);
    if (!res) return;
    if (!res.ok) return;
    const data = await res.json();
    setSolutionServices(Array.isArray(data) ? data : []);
  };

  const fetchContextServices = async (contextId: string) => {
    setLoadingServices((prev) => new Set(prev).add(contextId));
    const res = await authFetch(`/${domainId}/${solutionId}/context-services?zoneId=${zoneId}&contextId=${contextId}`);
    if (!res) { setLoadingServices((prev) => { const n = new Set(prev); n.delete(contextId); return n; }); return; }
    if (!res.ok) { setLoadingServices((prev) => { const n = new Set(prev); n.delete(contextId); return n; }); return; }
    const data = await res.json();
    setContextServices((prev) => ({ ...prev, [contextId]: Array.isArray(data) ? data : [] }));
    setLoadingServices((prev) => { const n = new Set(prev); n.delete(contextId); return n; });
  };

  const handleCreateContext = async () => {
    if (!newContextId.trim()) return;
    setCreatingContext(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/zones/${zoneId}/contexts/context`, {
        method: 'POST',
        body: JSON.stringify({ context: { id: newContextId.trim(), description: newContextDescription.trim() || null } }),
      });
      if (!res) return;
      if (res.status === 409) { throw new Error('Context with this ID already exists in this zone'); }
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to create context (${res.status}): ${t}`); }
      setNewContextId(''); setNewContextDescription(''); setShowNewContext(false);
      fetchContexts();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreatingContext(false);
    }
  };

  const handleDeleteContext = async (ctx: ContextEntry) => {
    if (!window.confirm(`Delete context "${ctx.id}"? This will also delete all associated services and ACL entries.`)) return;
    setError(null);
    const res = await authFetch(`/${domainId}/${solutionId}/zones/${zoneId}/contexts/${ctx.id}`, { method: 'DELETE' }, { 'X-Run-As-Environment': 'broker-services' });
    if (!res) return;
    if (!res.ok) { const t = await res.text(); setError(`Failed to delete context (${res.status}): ${t}`); return; }
    fetchContexts();
  };

  const handleToggleContext = (ctx: ContextEntry) => {
    const next = new Set(expandedContexts);
    if (next.has(ctx.id)) {
      next.delete(ctx.id);
    } else {
      next.add(ctx.id);
      if (!contextServices[ctx.id]) {
        fetchContextServices(ctx.id);
      }
    }
    setExpandedContexts(next);
  };

  const handleAddContextService = async (contextId: string, svc: { serviceName: string; serviceType: string }) => {
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/context-services`, {
        method: 'POST',
        body: JSON.stringify({ zoneId, contextId, serviceName: svc.serviceName, serviceType: svc.serviceType }),
      });
      if (!res) return;
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to add service (${res.status}): ${t}`); }
      fetchContextServices(contextId);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Add service failed');
    }
  };

  const handleDeleteContextService = async (contextId: string, csId: string) => {
    setError(null);
    const res = await authFetch(`/${domainId}/${solutionId}/context-services/${csId}`, { method: 'DELETE' });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete context service (${res.status})`); return; }
    setContextServices((prev) => ({
      ...prev,
      [contextId]: (prev[contextId] || []).filter((cs) => cs.id !== csId),
    }));
  };

  const getAvailableServices = (contextId: string): SolutionServiceEntry[] => {
    const assigned = contextServices[contextId] || [];
    const assignedKeys = new Set(assigned.map((cs) => `${cs.serviceName}|${cs.serviceType}`));
    return solutionServices.filter((s) => !assignedKeys.has(`${s.serviceName}|${s.serviceType}`));
  };

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError(null);
      await Promise.all([fetchContexts(), fetchSolutionServices()]);
      setLoading(false);
    };
    load();
  }, []);

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-5xl mx-auto px-6 py-8">
        <button onClick={() => navigate(`/domains/${domainId}/solutions/${solutionId}`)} className="text-sm text-blue-600 hover:text-blue-800 mb-4 inline-block">← Back to Solution Overview</button>

        <h1 className="text-2xl font-semibold text-gray-800 mb-2">Zone: {zoneId}</h1>
        <p className="text-sm text-gray-500 mb-6">Manage contexts and their services within this zone.</p>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        {/* Contexts Section */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-lg font-medium text-gray-800">Contexts</h2>
            <button onClick={() => setShowNewContext(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">Add Context</button>
          </div>

          {showNewContext && (
            <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 shadow-sm">
              <h3 className="text-sm font-medium text-gray-700 mb-3">Add Context</h3>
              <div className="flex gap-3 items-end">
                <div className="flex-1">
                  <label className="block text-xs text-gray-500 mb-1">Context ID</label>
                  <input type="text" value={newContextId} onChange={(e) => setNewContextId(e.target.value)} placeholder="e.g. DEFAULT" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <div className="flex-1">
                  <label className="block text-xs text-gray-500 mb-1">Description (optional)</label>
                  <input type="text" value={newContextDescription} onChange={(e) => setNewContextDescription(e.target.value)} placeholder="e.g. Default context" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
                <button onClick={handleCreateContext} disabled={creatingContext || !newContextId.trim()} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creatingContext ? 'Creating...' : 'Create'}</button>
                <button onClick={() => { setShowNewContext(false); setNewContextId(''); setNewContextDescription(''); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2">Cancel</button>
              </div>
            </div>
          )}

          {loading ? <p className="text-gray-500 text-sm">Loading...</p> : (
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200">
                    <th className="text-left px-4 py-3 font-medium text-gray-600 w-8"></th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Context ID</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">Created</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600 w-16"></th>
                  </tr>
                </thead>
                <tbody>
                  {contexts.length === 0 ? (
                    <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No contexts found in this zone</td></tr>
                  ) : contexts.map((ctx) => (
                    <>
                      <tr key={ctx.id} className="border-b border-gray-100 hover:bg-gray-50 cursor-pointer" onClick={() => handleToggleContext(ctx)}>
                        <td className="px-4 py-3 text-gray-400">{expandedContexts.has(ctx.id) ? '▼' : '▶'}</td>
                        <td className="px-4 py-3 text-gray-800 font-medium">{ctx.id}</td>
                        <td className="px-4 py-3 text-gray-600">{ctx.description || '—'}</td>
                        <td className="px-4 py-3 text-gray-600 text-xs">{ctx.createdAt ? new Date(ctx.createdAt).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : '—'}</td>
                        <td className="px-4 py-3 text-right">
                          {ctx.id !== 'DEFAULT' && (
                            <button
                              onClick={(e) => { e.stopPropagation(); handleDeleteContext(ctx); }}
                              className="text-red-500 hover:text-red-700 text-xs"
                            >Delete</button>
                          )}
                        </td>
                      </tr>
                      {expandedContexts.has(ctx.id) && (
                        <tr key={`${ctx.id}-services`}>
                          <td colSpan={5} className="px-4 py-3 bg-gray-50 border-b border-gray-100">
                            <ContextServicesPanel
                              contextId={ctx.id}
                              services={contextServices[ctx.id] || []}
                              availableServices={getAvailableServices(ctx.id)}
                              loading={loadingServices.has(ctx.id)}
                              onAdd={(svc) => handleAddContextService(ctx.id, svc)}
                              onDelete={(csId) => handleDeleteContextService(ctx.id, csId)}
                            />
                          </td>
                        </tr>
                      )}
                    </>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function ContextServicesPanel({
  contextId,
  services,
  availableServices,
  loading,
  onAdd,
  onDelete,
}: {
  contextId: string;
  services: ContextServiceEntry[];
  availableServices: SolutionServiceEntry[];
  loading: boolean;
  onAdd: (svc: { serviceName: string; serviceType: string }) => void;
  onDelete: (csId: string) => void;
}) {
  const [serviceName, setServiceName] = useState('');
  const [serviceType, setServiceType] = useState('OBJECT');
  const datalistId = `svc-suggestions-${contextId}`;

  const handleAdd = () => {
    if (!serviceName.trim()) return;
    onAdd({ serviceName: serviceName.trim(), serviceType });
    setServiceName('');
    setServiceType('OBJECT');
  };

  // Auto-fill type when user selects a suggestion that matches a solution-service
  const handleNameChange = (val: string) => {
    setServiceName(val);
    const match = availableServices.find((s) => s.serviceName === val);
    if (match) setServiceType(match.serviceType);
  };

  if (loading) return <p className="text-xs text-gray-400">Loading services...</p>;

  return (
    <div className="pl-4">
      <p className="text-xs font-medium text-gray-600 mb-2">Context Services</p>
      {services.length === 0 ? (
        <p className="text-xs text-gray-400 mb-2">No services assigned</p>
      ) : (
        <table className="w-full text-xs mb-2">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="pr-4 py-1">Service Name</th>
              <th className="pr-4 py-1">Service Type</th>
              <th className="py-1 w-8"></th>
            </tr>
          </thead>
          <tbody>
            {services.map((cs) => (
              <tr key={cs.id} className="border-t border-gray-100">
                <td className="pr-4 py-1 text-gray-800">{cs.serviceName}</td>
                <td className="pr-4 py-1 text-gray-600">{cs.serviceType}</td>
                <td className="py-1 text-right"><button onClick={() => onDelete(cs.id)} className="text-red-500 hover:text-red-700" title="Remove service">✕</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="flex items-center gap-2 mt-2">
        <input
          type="text"
          list={datalistId}
          value={serviceName}
          onChange={(e) => handleNameChange(e.target.value)}
          placeholder="Service name"
          className="border border-gray-300 rounded px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 w-40"
        />
        <datalist id={datalistId}>
          {availableServices.map((svc) => (
            <option key={svc.id} value={svc.serviceName}>{svc.serviceName} ({svc.serviceType})</option>
          ))}
        </datalist>
        <select value={serviceType} onChange={(e) => setServiceType(e.target.value)} className="border border-gray-300 rounded px-2 py-1 text-xs focus:outline-none focus:ring-2 focus:ring-blue-500">
          <option value="OBJECT">OBJECT</option>
          <option value="UTILITY">UTILITY</option>
          <option value="FUNCTIONAL">FUNCTIONAL</option>
          <option value="SERVICEPATH">SERVICEPATH</option>
          <option value="XQUERYTEMPLATE">XQUERYTEMPLATE</option>
          <option value="SERVICE">SERVICE</option>
        </select>
        <button onClick={handleAdd} disabled={!serviceName.trim()} className="text-xs bg-blue-600 text-white px-2 py-1 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">Add</button>
      </div>
    </div>
  );
}
