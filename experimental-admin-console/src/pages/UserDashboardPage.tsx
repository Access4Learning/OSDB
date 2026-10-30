import { useEffect, useState, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';
import { useAdminScope } from '../hooks/useAdminScope';
import Navbar from '../components/Navbar';
import UsersTab from '../components/UsersTab';
import EnvironmentDetailView from '../components/EnvironmentDetailView';
import LiveQueueChart from '../components/LiveQueueChart';

// ── Types ──

interface ProductIdentity {
  vendorName?: string;
  productName?: string;
  productVersion?: string;
  iconURI?: string;
}

interface ApplicationInfo {
  applicationKey?: string;
  supportedInfrastructureVersion?: string;
  dataModelNamespace?: string;
  transport?: string;
  applicationProduct?: ProductIdentity;
  adapterProduct?: ProductIdentity;
}

interface InfrastructureService {
  name: string;
  value: string;
}

interface EnvironmentDetail {
  id: string;
  consumerName: string;
  type: string;
  sessionToken?: string;
  authenticationMethod?: string;
  defaultZoneId?: string;
  fingerprint?: string;
  solutionId?: string;
  applicationInfo?: ApplicationInfo;
  infrastructureServices?: InfrastructureService[];
}

interface Queue {
  id: string;
  name: string;
  ownerId: string;
  maxConcurrentConnections?: number;
  messageCount?: number;
  lastAccessed?: string;
}

type AccessType = 'QUERY' | 'SUBSCRIBE' | 'CREATE' | 'UPDATE' | 'DELETE' | 'PROVIDE';
const ACCESS_TYPES: AccessType[] = ['QUERY', 'SUBSCRIBE', 'CREATE', 'UPDATE', 'DELETE', 'PROVIDE'];

interface AclEntry {
  id: string;
  environmentId: string;
  serviceName: string;
  zoneId: string | null;
  contextId: string | null;
  accessType: AccessType;
  accessLevel: string;
}

type Tab = 'details' | 'queues' | 'acl' | 'users';

// ── Main component ──

export default function UserDashboardPage() {
  const { selectedEnvironment, apiFetch } = useAuth();
  const { domainId, solutionId } = useAdminScope();

  const envId = selectedEnvironment?.id;

  const [tab, setTab] = useState<Tab>('details');
  const [env, setEnv] = useState<EnvironmentDetail | null>(null);
  const [queues, setQueues] = useState<Queue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // ACL state
  const [aclEntries, setAclEntries] = useState<AclEntry[]>([]);
  const [aclLoading, setAclLoading] = useState(false);
  const [aclFilter, setAclFilter] = useState({ zone: '', context: '', service: '' });

  // Queue management state
  const [showNewQueue, setShowNewQueue] = useState(false);
  const [newQueueName, setNewQueueName] = useState('');
  const [creatingQueue, setCreatingQueue] = useState(false);
  const [liveQueue, setLiveQueue] = useState<Queue | null>(null);

  // Wrapper for UsersTab — adapts apiFetch to the expected signature
  const authFetch = useCallback(async (path: string, opts?: RequestInit): Promise<Response | null> => {
    try {
      return await apiFetch(path, opts);
    } catch {
      return null;
    }
  }, [apiFetch]);

  const fetchEnv = async () => {
    if (!domainId || !solutionId || !envId) return;
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/environments/${envId}`);
      if (!res.ok) { setError(`Failed to fetch environment (${res.status})`); return; }
      const data = await res.json();
      const e = data.environment || data;
      setEnv({
        id: e.id,
        consumerName: e.consumerName,
        type: e.type || '',
        sessionToken: e.sessionToken,
        authenticationMethod: e.authenticationMethod || 'SIF_HMACSHA256',
        defaultZoneId: e.defaultZone?.id,
        fingerprint: e.fingerprint,
        solutionId: e.solutionId,
        applicationInfo: e.applicationInfo || undefined,
        infrastructureServices: e.infrastructureServices?.infrastructureService || [],
      });
    } catch {
      setError('Failed to fetch environment details');
    }
  };

  const fetchQueues = async () => {
    if (!domainId || !solutionId || !envId) return;
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/queues`);
      if (!res.ok) { setError(`Failed to fetch queues (${res.status})`); return; }
      const data = await res.json();
      const all: Queue[] = data.queues?.queue
        ? data.queues.queue.map((q: any) => ({
            id: q.id, name: q.name, ownerId: q.ownerId,
            maxConcurrentConnections: q.maxConcurrentConnections,
            messageCount: q.messageCount,
            lastAccessed: q.lastAccessed,
          }))
        : [];
      setQueues(all.filter((q) => q.ownerId === envId));
    } catch {
      setError('Failed to fetch queues');
    }
  };

  const fetchAclEntries = async () => {
    if (!domainId || !solutionId || !envId) return;
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/acl-entries?environmentId=${envId}`);
      if (!res.ok) { setError(`Failed to fetch ACL entries (${res.status})`); return; }
      const data = await res.json();
      setAclEntries(Array.isArray(data) ? data : []);
    } catch {
      setError('Failed to fetch ACL entries');
    }
  };

  const loadAclTab = async () => {
    setAclLoading(true);
    setError(null);
    await fetchAclEntries();
    setAclLoading(false);
  };

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    await Promise.all([fetchEnv(), fetchQueues()]);
    setLoading(false);
  };

  useEffect(() => {
    if (domainId && solutionId && envId) {
      loadAll();
    }
  }, [domainId, solutionId, envId]);

  useEffect(() => {
    if (tab === 'acl' && aclEntries.length === 0 && !aclLoading) {
      loadAclTab();
    }
  }, [tab]);

  // ── Queue management handlers ──

  const handleCreateQueue = async () => {
    if (!domainId || !solutionId || !newQueueName.trim()) return;
    setCreatingQueue(true);
    setError(null);
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/queues/queue`, {
        method: 'POST',
        body: JSON.stringify({ queue: { name: newQueueName.trim(), ownerId: envId } }),
      });
      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        setError(`Failed to create queue (${res.status})${errText ? ': ' + errText : ''}`);
      } else {
        setNewQueueName('');
        setShowNewQueue(false);
        await fetchQueues();
      }
    } catch {
      setError('Failed to create queue');
    }
    setCreatingQueue(false);
  };

  const handleDeleteQueue = async (q: Queue) => {
    if (!domainId || !solutionId) return;
    if (!window.confirm(`Delete queue "${q.name}"?`)) return;
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/queues/${q.id}`, { method: 'DELETE' });
      if (!res.ok) { setError(`Failed to delete queue (${res.status})`); return; }
      setQueues((prev) => prev.filter((x) => x.id !== q.id));
    } catch {
      setError('Failed to delete queue');
    }
  };

  // ── Utility ──

  const formatLastAccessed = (ts?: string) => {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  };

  const tabClass = (t: Tab) => t === tab
    ? 'px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white'
    : 'px-4 py-2 text-sm font-medium rounded text-gray-600 hover:text-gray-900 hover:bg-gray-100';

  if (!selectedEnvironment) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="max-w-7xl mx-auto px-4 py-8">
          <div className="bg-white rounded-lg border border-gray-200 p-6">
            <p className="text-sm text-gray-600">No environment selected</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-5xl mx-auto px-6 py-8">
        <h1 className="text-2xl font-semibold text-gray-800 mb-6">
          {env?.consumerName || selectedEnvironment.applicationName} — Environment Overview
        </h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        <div className="flex gap-2 mb-6">
          <button className={tabClass('details')} onClick={() => setTab('details')}>Details</button>
          <button className={tabClass('queues')} onClick={() => setTab('queues')}>Queues</button>
          <button className={tabClass('acl')} onClick={() => setTab('acl')}>ACL</button>
          <button className={tabClass('users')} onClick={() => setTab('users')}>Users</button>
        </div>

        {loading ? <p className="text-gray-500 text-sm">Loading...</p> : (
          <>
            {/* ── Details Tab ── */}
            {tab === 'details' && (
              <EnvironmentDetailView domainId={domainId!} solutionId={solutionId!} envId={envId!} authFetch={authFetch} />
            )}

            {/* ── Queues Tab ── */}
            {tab === 'queues' && (
              <div>
                <div className="flex justify-between items-center mb-4">
                  <button
                    onClick={() => setShowNewQueue(!showNewQueue)}
                    className="px-3 py-1.5 text-sm font-medium rounded bg-blue-600 text-white hover:bg-blue-700"
                  >
                    Create Queue
                  </button>
                  <button onClick={() => fetchQueues()} className="text-gray-400 hover:text-gray-600 text-sm" title="Refresh">↻ Refresh</button>
                </div>

                {/* Inline create queue form */}
                {showNewQueue && (
                  <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4 flex items-end gap-3">
                    <div className="flex-1">
                      <label className="block text-gray-500 text-sm mb-1">Queue Name</label>
                      <input
                        type="text"
                        value={newQueueName}
                        onChange={(e) => setNewQueueName(e.target.value)}
                        placeholder="Enter queue name"
                        className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                        onKeyDown={(e) => { if (e.key === 'Enter') handleCreateQueue(); }}
                      />
                    </div>
                    <button
                      onClick={handleCreateQueue}
                      disabled={creatingQueue || !newQueueName.trim()}
                      className="px-3 py-1.5 text-sm font-medium rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      {creatingQueue ? 'Creating...' : 'Create'}
                    </button>
                    <button
                      onClick={() => { setShowNewQueue(false); setNewQueueName(''); }}
                      className="px-3 py-1.5 text-sm font-medium rounded border border-gray-300 text-gray-700 hover:bg-gray-100"
                    >
                      Cancel
                    </button>
                  </div>
                )}

                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Max Connections</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Messages</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Last Accessed</th>
                        <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {queues.length === 0 ? (
                        <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No queues found for this environment</td></tr>
                      ) : queues.map((q) => (
                        <tr key={q.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-800">{q.name}</td>
                          <td className="px-4 py-3 text-gray-600">{q.maxConcurrentConnections ?? '—'}</td>
                          <td className="px-4 py-3 text-gray-600">{q.messageCount ?? '—'}</td>
                          <td className="px-4 py-3 text-gray-600 text-xs">{formatLastAccessed(q.lastAccessed)}</td>
                          <td className="px-4 py-3 text-right flex gap-2 justify-end">
                            <button onClick={() => setLiveQueue(q)} className="text-blue-600 hover:text-blue-800 text-xs font-medium">Live Messages</button>
                            <button onClick={() => handleDeleteQueue(q)} className="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {/* Live Queue Chart */}
                {liveQueue && domainId && solutionId && (
                  <LiveQueueChart
                    domainId={domainId}
                    solutionId={solutionId}
                    queueId={liveQueue.id}
                    queueName={liveQueue.name}
                    onClose={() => setLiveQueue(null)}
                  />
                )}
              </div>
            )}

            {/* ── ACL Tab (Read-Only) ── */}
            {tab === 'acl' && (
              <div>
                {aclLoading ? <p className="text-gray-500 text-sm">Loading ACL entries...</p> : (
                  <>
                    {/* Filters */}
                    <div className="flex gap-4 mb-4">
                      <input type="text" placeholder="Filter by zone..." value={aclFilter.zone} onChange={(e) => setAclFilter((f) => ({ ...f, zone: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      <input type="text" placeholder="Filter by context..." value={aclFilter.context} onChange={(e) => setAclFilter((f) => ({ ...f, context: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      <input type="text" placeholder="Filter by service..." value={aclFilter.service} onChange={(e) => setAclFilter((f) => ({ ...f, service: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      <button onClick={() => loadAclTab()} className="text-gray-400 hover:text-gray-600 text-sm px-2" title="Refresh">↻</button>
                    </div>

                    {/* ACL Table (read-only) */}
                    {(() => {
                      type AclRow = { serviceName: string; zoneId: string | null; contextId: string | null; accessMap: Map<AccessType, AclEntry> };
                      type ContextGroup = { contextName: string; contextId: string | null; rows: AclRow[] };
                      type ZoneGroup = { zoneName: string; contexts: ContextGroup[] };

                      const zoneMap = new Map<string, Map<string, Map<string, Map<AccessType, AclEntry>>>>();

                      for (const entry of aclEntries) {
                        const zoneName = entry.zoneId || '(zone-level wildcard)';
                        const ctxKey = entry.contextId || '__wildcard__';

                        if (!zoneMap.has(zoneName)) zoneMap.set(zoneName, new Map());
                        const ctxMap = zoneMap.get(zoneName)!;
                        if (!ctxMap.has(ctxKey)) ctxMap.set(ctxKey, new Map());
                        const svcMap = ctxMap.get(ctxKey)!;
                        if (!svcMap.has(entry.serviceName)) svcMap.set(entry.serviceName, new Map());
                        svcMap.get(entry.serviceName)!.set(entry.accessType, entry);
                      }

                      const zoneGroups: ZoneGroup[] = [];
                      for (const [zoneName, ctxMap] of zoneMap) {
                        const contexts: ContextGroup[] = [];
                        for (const [ctxKey, svcMap] of ctxMap) {
                          const contextId = ctxKey === '__wildcard__' ? null : ctxKey;
                          const contextName = contextId || '(all contexts)';
                          const rows: AclRow[] = [];
                          for (const [serviceName, accessMap] of svcMap) {
                            rows.push({ serviceName, zoneId: zoneName === '(zone-level wildcard)' ? null : zoneName, contextId, accessMap });
                          }
                          rows.sort((a, b) => a.serviceName.localeCompare(b.serviceName));
                          contexts.push({ contextName, contextId, rows });
                        }
                        contexts.sort((a, b) => a.contextName.localeCompare(b.contextName));
                        zoneGroups.push({ zoneName, contexts });
                      }
                      zoneGroups.sort((a, b) => a.zoneName.localeCompare(b.zoneName));

                      // Apply filters
                      const filtered = zoneGroups
                        .filter((zg) => !aclFilter.zone || zg.zoneName.toLowerCase().includes(aclFilter.zone.toLowerCase()))
                        .map((zg) => ({
                          ...zg,
                          contexts: zg.contexts
                            .filter((cg) => !aclFilter.context || cg.contextName.toLowerCase().includes(aclFilter.context.toLowerCase()))
                            .map((cg) => ({
                              ...cg,
                              rows: cg.rows.filter((r) => !aclFilter.service || r.serviceName.toLowerCase().includes(aclFilter.service.toLowerCase())),
                            }))
                            .filter((cg) => cg.rows.length > 0),
                        }))
                        .filter((zg) => zg.contexts.length > 0);

                      if (filtered.length === 0) {
                        return <div className="bg-white rounded-lg border border-gray-200 px-4 py-6 text-center text-gray-400 text-sm">No ACL entries found for this environment</div>;
                      }

                      const zoneRowCounts = filtered.map((zg) => zg.contexts.reduce((sum, cg) => sum + cg.rows.length, 0));
                      const ctxRowCounts = filtered.map((zg) => zg.contexts.map((cg) => cg.rows.length));

                      return (
                        <div className="bg-white rounded-lg border border-gray-200 overflow-visible">
                          <table className="w-full text-sm">
                            <thead>
                              <tr className="border-b border-gray-200 bg-gray-50">
                                <th className="text-left px-4 py-2 font-medium text-gray-600 w-40">Zone</th>
                                <th className="text-left px-4 py-2 font-medium text-gray-600 w-40">Context</th>
                                <th className="text-left px-4 py-2 font-medium text-gray-600 w-48">Service</th>
                                {ACCESS_TYPES.map((at) => (
                                  <th key={at} className="text-center px-2 py-2 font-medium text-gray-600 w-20">{at}</th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {filtered.map((zg, zIdx) => {
                                const totalZoneRows = zoneRowCounts[zIdx];
                                let zoneRowRendered = false;

                                return zg.contexts.map((cg, cIdx) => {
                                  const totalCtxRows = ctxRowCounts[zIdx][cIdx];
                                  let ctxRowRendered = false;

                                  return cg.rows.map((row) => {
                                    const showZone = !zoneRowRendered;
                                    const showCtx = !ctxRowRendered;
                                    if (showZone) zoneRowRendered = true;
                                    if (showCtx) ctxRowRendered = true;

                                    return (
                                      <tr key={`${zg.zoneName}-${cg.contextId}-${row.serviceName}`} className={`border-b border-gray-100 hover:bg-gray-50 ${showZone ? 'border-t border-gray-200' : ''}`}>
                                        {showZone && (
                                          <td rowSpan={totalZoneRows} className="px-4 py-2 align-top font-bold text-gray-700 bg-blue-50/40 border-r border-gray-100">
                                            {zg.zoneName}
                                          </td>
                                        )}
                                        {showCtx && (
                                          <td rowSpan={totalCtxRows} className="px-4 py-2 align-top text-gray-600 border-r border-gray-100">
                                            <span className="pl-2">{cg.contextName}</span>
                                          </td>
                                        )}
                                        <td className="px-4 py-2 text-gray-800">{row.serviceName}</td>
                                        {ACCESS_TYPES.map((at) => {
                                          const entry = row.accessMap.get(at);
                                          const isApproved = entry && entry.accessLevel === 'APPROVED';
                                          return (
                                            <td key={at} className="text-center px-2 py-2">
                                              <span
                                                className={`inline-flex items-center justify-center w-7 h-7 rounded border text-xs font-bold ${
                                                  isApproved
                                                    ? 'bg-green-50 border-green-300 text-green-600'
                                                    : 'bg-gray-50 border-gray-200 text-gray-300'
                                                }`}
                                              >
                                                {isApproved ? '✓' : ''}
                                              </span>
                                            </td>
                                          );
                                        })}
                                      </tr>
                                    );
                                  });
                                });
                              })}
                            </tbody>
                          </table>
                        </div>
                      );
                    })()}
                  </>
                )}
              </div>
            )}

            {/* ── Users Tab ── */}
            {tab === 'users' && domainId && solutionId && envId && (
              <UsersTab domainId={domainId} solutionId={solutionId} envId={envId} authFetch={authFetch} />
            )}
          </>
        )}
      </main>
    </div>
  );
}
