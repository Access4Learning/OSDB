import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';
import LiveQueueChart from '../components/LiveQueueChart';
import UsersTab from '../components/UsersTab';

interface EnvironmentInfo {
  id: string;
  consumerName: string;
  type: string;
  sessionToken: string;
  fingerprint: string;
  solutionId: string;
  domainId?: string;
  authenticationMethod?: string;
  applicationInfo?: {
    applicationKey?: string;
    supportedInfrastructureVersion?: string;
    applicationProduct?: { vendorName?: string; productName?: string; productVersion?: string };
    adapterProduct?: { vendorName?: string; productName?: string; productVersion?: string };
  };
}

interface Queue {
  id: string;
  name: string;
  ownerId: string;
  maxConcurrentConnections?: number;
  messageCount?: number;
  polling?: string;
  lastAccessed?: string;
  lastModified?: string;
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

export default function MyEnvironmentPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();

  const [env, setEnv] = useState<EnvironmentInfo | null>(null);
  const [queues, setQueues] = useState<Queue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>('details');
  const [liveQueue, setLiveQueue] = useState<Queue | null>(null);
  const [domainId, setDomainId] = useState<string>('');
  const [solutionId, setSolutionId] = useState<string>('');

  // ACL state
  const [aclEntries, setAclEntries] = useState<AclEntry[]>([]);
  const [aclLoading, setAclLoading] = useState(false);
  const [aclFilter, setAclFilter] = useState({ zone: '', context: '', service: '' });
  const authFetch = async (path: string, opts?: RequestInit) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  // Discover the environment by trying to get the environment list
  // The gateway will scope the response to the authenticated environment's solution
  const discoverEnvironment = async () => {
    if (!sessionToken || !sharedSecret) return;
    setLoading(true);
    setError(null);

    try {
      // We need to find our domainId and solutionId.
      // Try a known pattern: the gateway's auth response includes these in exchange attributes,
      // but they're not exposed to us. We'll try fetching solutions for common domains,
      // or use a different approach.
      
      // Actually, the simplest approach: the environment's own sessionToken is unique.
      // We can try to find ourselves by listing environments in various solutions.
      // But that requires knowing the domain/solution.
      
      // For the experimental console, let's just show a form asking for domain/solution
      // if we can't auto-discover.
      setError('Please provide your domain and solution IDs to continue.');
      setLoading(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Discovery failed');
      setLoading(false);
    }
  };

  const loadEnvironment = async (domain: string, solution: string, envId: string) => {
    setLoading(true);
    setError(null);
    setDomainId(domain);
    setSolutionId(solution);

    try {
      // Fetch our own environment directly by ID
      const envRes = await authFetch(`/${domain}/${solution}/environments/${envId}`);
      if (!envRes || !envRes.ok) {
        setError(`Failed to load environment (${envRes?.status}). Check your domain, solution, and environment IDs.`);
        setLoading(false);
        return;
      }
      const myEnv = await envRes.json();
      
      setEnv({
        id: myEnv.id,
        consumerName: myEnv.consumerName,
        type: myEnv.type || '',
        sessionToken: myEnv.sessionToken || '',
        fingerprint: myEnv.fingerprint || '',
        solutionId: myEnv.solutionId || solution,
        authenticationMethod: myEnv.authenticationMethod,
        applicationInfo: myEnv.applicationInfo,
      });

      // Fetch queues (will be filtered to our environment by the gateway)
      const qRes = await authFetch(`/${domain}/${solution}/queues`);
      if (qRes && qRes.ok) {
        const qData = await qRes.json();
        const qList = qData.queues?.queue || [];
        setQueues(qList.map((q: any) => ({
          id: q.id, name: q.name, ownerId: q.ownerId,
          maxConcurrentConnections: q.maxConcurrentConnections,
          messageCount: q.messageCount, polling: q.polling,
          lastAccessed: q.lastAccessed, lastModified: q.lastModified,
        })));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Load failed');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { discoverEnvironment(); }, []);

  // ACL fetch functions
  const fetchAclEntries = async () => {
    if (!domainId || !solutionId || !env) return;
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/acl-entries?environmentId=${env.id}`);
      if (!res || !res.ok) { setError(`Failed to fetch ACL entries (${res?.status})`); return; }
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

  const [inputDomain, setInputDomain] = useState('');
  const [inputSolution, setInputSolution] = useState('');
  const [inputEnvId, setInputEnvId] = useState('');

  const handleConnect = () => {
    if (inputDomain.trim() && inputSolution.trim() && inputEnvId.trim()) {
      loadEnvironment(inputDomain.trim(), inputSolution.trim(), inputEnvId.trim());
    }
  };

  const tabClass = (t: Tab) => t === tab
    ? 'px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white'
    : 'px-4 py-2 text-sm font-medium rounded text-gray-600 hover:text-gray-900 hover:bg-gray-100';

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).catch(() => {});
  };

  // If we haven't loaded the environment yet, show the domain/solution form
  if (!env) {
    return (
      <div className="min-h-screen bg-gray-50">
        <nav className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
          <span className="text-lg font-semibold text-gray-800">SIF Unity Broker</span>
          <button onClick={() => { logout(); navigate('/login'); }} className="text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded px-3 py-1 hover:bg-gray-50">Logout</button>
        </nav>
        <main className="max-w-md mx-auto px-6 py-16">
          <div className="bg-white rounded-lg shadow-md p-6">
            <h1 className="text-lg font-semibold text-gray-800 mb-4">Connect to Your Solution</h1>
            <p className="text-sm text-gray-500 mb-4">Enter the domain and solution your environment belongs to.</p>
            <div className="space-y-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Domain ID</label>
                <input type="text" value={inputDomain} onChange={(e) => setInputDomain(e.target.value)} placeholder="e.g. iowa" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Solution ID</label>
                <input type="text" value={inputSolution} onChange={(e) => setInputSolution(e.target.value)} placeholder="e.g. production" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Environment ID (UUID)</label>
                <input type="text" value={inputEnvId} onChange={(e) => setInputEnvId(e.target.value)} placeholder="e.g. 4a74e1ea-78f1-4cce-a7e0-e9b43e272ef3" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <button onClick={handleConnect} disabled={!inputDomain.trim() || !inputSolution.trim() || !inputEnvId.trim()} className="w-full bg-blue-600 text-white rounded py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-50 transition-colors">
                Connect
              </button>
            </div>
            {error && !loading && <p className="text-red-600 text-sm mt-3">{error}</p>}
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white border-b border-gray-200 px-6 py-3 flex items-center justify-between">
        <span className="text-lg font-semibold text-gray-800">SIF Unity Broker</span>
        <button onClick={() => { logout(); navigate('/login'); }} className="text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded px-3 py-1 hover:bg-gray-50">Logout</button>
      </nav>
      <main className="max-w-5xl mx-auto px-6 py-8">
        <h1 className="text-2xl font-semibold text-gray-800 mb-6">{env.consumerName || 'My'} Environment</h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        <div className="flex gap-2 mb-6">
          <button className={tabClass('details')} onClick={() => setTab('details')}>Details</button>
          <button className={tabClass('queues')} onClick={() => setTab('queues')}>Queues</button>
          <button className={tabClass('acl')} onClick={() => { setTab('acl'); if (aclEntries.length === 0 && !aclLoading) loadAclTab(); }}>ACL</button>
          <button className={tabClass('users')} onClick={() => setTab('users')}>Users</button>
        </div>

        {tab === 'details' && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">General</h2>
              <dl className="space-y-3 text-sm">
                <div><dt className="text-gray-500">Name</dt><dd className="font-medium text-gray-800">{env.consumerName}</dd></div>
                <div><dt className="text-gray-500">Type</dt><dd className="font-medium text-gray-800">{env.type}</dd></div>
                <div><dt className="text-gray-500">Solution</dt><dd className="font-medium text-gray-800">{env.solutionId}</dd></div>
                <div><dt className="text-gray-500">Auth Method</dt><dd className="font-medium text-gray-800">{env.authenticationMethod || 'SIF_HMACSHA256'}</dd></div>
                {env.applicationInfo?.applicationProduct && (
                  <>
                    <div><dt className="text-gray-500">Product</dt><dd className="font-medium text-gray-800">{env.applicationInfo.applicationProduct.productName || '—'}</dd></div>
                    <div><dt className="text-gray-500">Vendor</dt><dd className="font-medium text-gray-800">{env.applicationInfo.applicationProduct.vendorName || '—'}</dd></div>
                  </>
                )}
              </dl>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Security</h2>
              <dl className="space-y-3 text-sm">
                <div>
                  <dt className="text-gray-500">Environment ID</dt>
                  <dd className="font-medium text-gray-800 font-mono text-xs flex items-center gap-2">
                    {env.id}
                    <button onClick={() => copyToClipboard(env.id)} className="text-blue-600 hover:text-blue-800 text-xs">Copy</button>
                  </dd>
                </div>
                <div>
                  <dt className="text-gray-500">Session Token</dt>
                  <dd className="font-medium text-gray-800 font-mono text-xs truncate max-w-[250px]">{env.sessionToken}</dd>
                </div>
                <div><dt className="text-gray-500">Fingerprint</dt><dd className="font-medium text-gray-800 font-mono text-xs">{env.fingerprint || '—'}</dd></div>
              </dl>
            </div>
          </div>
        )}

        {tab === 'queues' && (
          <div>
            <div className="flex justify-end mb-3">
              <button onClick={() => loadEnvironment(domainId, solutionId, env.id)} className="text-gray-400 hover:text-gray-600 text-lg" title="Refresh queues">↻</button>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-gray-50 border-b border-gray-200">
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Polling</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Max Connections</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Messages</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                </tr>
              </thead>
              <tbody>
                {queues.length === 0 ? (
                  <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No queues found</td></tr>
                ) : queues.map((q) => (
                  <tr key={q.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-3 text-gray-800">{q.name}</td>
                    <td className="px-4 py-3 text-gray-600">{q.polling || '—'}</td>
                    <td className="px-4 py-3 text-gray-600">{q.maxConcurrentConnections ?? '—'}</td>
                    <td className="px-4 py-3 text-gray-600">{q.messageCount ?? '—'}</td>
                    <td className="px-4 py-3 text-right">
                      <button onClick={() => setLiveQueue(q)} className="text-blue-600 hover:text-blue-800 text-xs font-medium">Live Messages</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          </div>
        )}

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

        {tab === 'users' && (
          <UsersTab domainId={domainId} solutionId={solutionId} envId={env.id} authFetch={authFetch} />
        )}

        {liveQueue && domainId && solutionId && (
          <LiveQueueChart
            domainId={domainId}
            solutionId={solutionId}
            queueId={liveQueue.id}
            queueName={liveQueue.name}
            onClose={() => setLiveQueue(null)}
          />
        )}
      </main>
    </div>
  );
}
