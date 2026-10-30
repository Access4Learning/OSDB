import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';
import LiveQueueChart from '../components/LiveQueueChart';
import PendingQueueMessagesDialog from '../components/PendingQueueMessagesDialog';
import NamedQueriesTab from '../components/NamedQueriesTab';
import SolutionTagsPage from './SolutionTagsPage';
import { discardOldestPendingQueueMessage, purgeQueueMessages } from '../utils/queueActions';

interface Environment {
  id: string;
  applicationName: string;
  type: string;
  sessionToken?: string;
}

interface Queue {
  id: string;
  name: string;
  ownerId: string;
  maxConcurrentConnections?: number;
  messageCount?: number;
  lastAccessed?: string;
}

interface SolutionServiceEntry {
  id: string;
  serviceName: string;
  serviceType: string;
  createdAt: string;
}

interface SolutionInfo {
  id: string;
  solutionId: string;
  description: string;
  domainId: string;
  createdAt?: string;
}

type Tab = 'details' | 'environments' | 'queues' | 'services' | 'zones' | 'tags' | 'namedQueries';

export default function SolutionOverviewPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();
  const { domainId, solutionId } = useParams<{ domainId: string; solutionId: string }>();

  const [tab, setTab] = useState<Tab>('details');
  // Set when an XQUERYTEMPLATE service name is clicked on the Services tab.
  const [editQueryToken, setEditQueryToken] = useState<string | null>(null);
  const [solution, setSolution] = useState<SolutionInfo | null>(null);
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [queues, setQueues] = useState<Queue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Create environment form
  const [showNewEnv, setShowNewEnv] = useState(false);
  const [newEnvName, setNewEnvName] = useState('');
  const [newEnvType, setNewEnvType] = useState('BROKERED');
  const [newEnvDefaultZone, setNewEnvDefaultZone] = useState('');
  const [newEnvAppKey, setNewEnvAppKey] = useState('');
  const [newEnvAppProductName, setNewEnvAppProductName] = useState('');
  const [newEnvAppVendorName, setNewEnvAppVendorName] = useState('');
  const [newEnvShowAdvanced, setNewEnvShowAdvanced] = useState(false);
  const [newEnvDefaultProviderUrl, setNewEnvDefaultProviderUrl] = useState('');
  const [newEnvAppProductVersion, setNewEnvAppProductVersion] = useState('');
  const [newEnvAppIconURI, setNewEnvAppIconURI] = useState('');
  const [newEnvAdapterProductName, setNewEnvAdapterProductName] = useState('');
  const [newEnvAdapterVendorName, setNewEnvAdapterVendorName] = useState('');
  const [newEnvAdapterProductVersion, setNewEnvAdapterProductVersion] = useState('');
  const [newEnvAdapterIconURI, setNewEnvAdapterIconURI] = useState('');
  const [creatingEnv, setCreatingEnv] = useState(false);
  const [liveQueue, setLiveQueue] = useState<Queue | null>(null);
  const [pendingQueue, setPendingQueue] = useState<Queue | null>(null);
  const [queueAction, setQueueAction] = useState<string | null>(null);
  const [newEnvCredentials, setNewEnvCredentials] = useState<{ sessionToken: string; sharedSecret: string } | null>(null);

  // Services tab state
  const [services, setServices] = useState<SolutionServiceEntry[]>([]);
  const [showNewService, setShowNewService] = useState(false);
  const [newServiceName, setNewServiceName] = useState('');
  const [newServiceType, setNewServiceType] = useState('OBJECT');
  const [creatingService, setCreatingService] = useState(false);

  // Zones tab state. `id` is the SIF business identifier; `uuid` is the internal UUID.
  const [zones, setZones] = useState<{ id: string; uuid: string; description: string; createdAt: string }[]>([]);
  const [showNewZone, setShowNewZone] = useState(false);
  const [newZoneId, setNewZoneId] = useState('');
  const [newZoneDescription, setNewZoneDescription] = useState('');
  const [creatingZone, setCreatingZone] = useState(false);

  const authFetch = async (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts, extraHeaders);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  const fetchSolution = async () => {
    const res = await authFetch(`/${domainId}/solutions/${solutionId}`);
    if (!res) return;
    if (res.status === 403) {
      // Solution admin can't access solution management endpoint — that's fine,
      // we can still load zones/environments/queues directly
      setSolution({ id: '', solutionId: solutionId || '', domainId: domainId || '', description: '' });
      return;
    }
    if (!res.ok) { setError(`Failed to fetch solution (${res.status})`); return; }
    const data = await res.json();
    setSolution(data);
  };

  const fetchEnvironments = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/environments`);
    if (!res) return;
    if (!res.ok) { setError(`Failed to fetch environments (${res.status})`); return; }
    const data = await res.json();
    const list: Environment[] = data.environments?.environment
      ? data.environments.environment.map((e: any) => ({ id: e.id, applicationName: e.consumerName, type: e.type || '', sessionToken: e.sessionToken }))
      : [];
    setEnvironments(list);
  };

  const fetchQueues = async () => {
    // Use X-Run-As-Environment to impersonate broker-services and see all solution queues
    const res = await authFetch(`/${domainId}/${solutionId}/queues`, undefined, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!res) return;
    if (!res.ok) { setError(`Failed to fetch queues (${res.status})`); return; }
    const data = await res.json();
    const list: Queue[] = data.queues?.queue
      ? data.queues.queue.map((q: any) => ({
          id: q.id, name: q.name, ownerId: q.ownerId,
          maxConcurrentConnections: q.maxConcurrentConnections,
          messageCount: q.messageCount,
          lastAccessed: q.lastAccessed,
        }))
      : [];
    setQueues(list);
  };

  const fetchServices = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/solution-services`);
    if (!res) { setLoading(false); return; }
    if (!res.ok) { setError(`Failed to fetch services (${res.status})`); setLoading(false); return; }
    const data = await res.json();
    setServices(Array.isArray(data) ? data : []);
  };

  const fetchZones = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/zones`);
    if (!res) { setLoading(false); return; }
    if (!res.ok) { setError(`Failed to fetch zones (${res.status})`); setLoading(false); return; }
    const data = await res.json();
    const list = data.zones?.zone || [];
    setZones(Array.isArray(list) ? list : []);
  };

  const handleCreateZone = async () => {
    if (!newZoneId.trim()) return;
    setCreatingZone(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/zones/zone`, {
        method: 'POST',
        body: JSON.stringify({ zone: { id: newZoneId.trim(), description: newZoneDescription.trim() || null } }),
      }, { 'X-Run-As-Environment': 'broker-services' });
      if (!res) return;
      if (res.status === 409) { throw new Error('Zone with this ID already exists'); }
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to create zone (${res.status}): ${t}`); }
      setNewZoneId(''); setNewZoneDescription(''); setShowNewZone(false);
      fetchZones();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreatingZone(false);
    }
  };

  const handleDeleteZone = async (zone: { id: string }) => {
    if (!window.confirm(`Delete zone "${zone.id}"? This will also delete all contexts within it.`)) return;
    const res = await authFetch(`/${domainId}/${solutionId}/zones/${zone.id}`, { method: 'DELETE' }, { 'X-Run-As-Environment': 'broker-services' });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete zone (${res.status})`); return; }
    setZones((prev) => prev.filter((z) => z.id !== zone.id));
  };

  const handleCreateService = async () => {
    if (!newServiceName.trim()) return;
    setCreatingService(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/solution-services`, {
        method: 'POST',
        body: JSON.stringify({ serviceName: newServiceName.trim(), serviceType: newServiceType }),
      });
      if (!res) return;
      if (res.status === 409) { throw new Error('Service with this name and type already exists'); }
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to create service (${res.status}): ${t}`); }
      setNewServiceName(''); setShowNewService(false);
      fetchServices();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreatingService(false);
    }
  };

  const handleDeleteService = async (svc: SolutionServiceEntry) => {
    if (!window.confirm(`Delete service "${svc.serviceName}" (${svc.serviceType})?`)) return;
    const res = await authFetch(`/${domainId}/${solutionId}/solution-services/${svc.id}`, { method: 'DELETE' });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete service (${res.status})`); return; }
    setServices((prev) => prev.filter((s) => s.id !== svc.id));
  };

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    await Promise.all([fetchSolution(), fetchEnvironments()]);
    setLoading(false);
  };

  const loadQueues = async () => {
    setLoading(true);
    setError(null);
    await fetchQueues();
    setLoading(false);
  };

  const loadServices = async () => {
    setLoading(true);
    setError(null);
    await fetchServices();
    setLoading(false);
  };

  const loadZones = async () => {
    setLoading(true);
    setError(null);
    await fetchZones();
    setLoading(false);
  };

  useEffect(() => { loadAll(); }, []);

  useEffect(() => {
    if (tab === 'queues' && queues.length === 0) {
      loadQueues();
    }
    if (tab === 'services' && services.length === 0) {
      loadServices();
    }
    if (tab === 'zones' && zones.length === 0) {
      loadZones();
    }
    if (tab === 'environments' && zones.length === 0) {
      fetchZones();
    }
  }, [tab]);

  const resetNewEnvForm = () => {
    setNewEnvName('');
    setNewEnvType('BROKERED');
    setNewEnvDefaultZone('');
    setNewEnvAppKey('');
    setNewEnvAppProductName('');
    setNewEnvAppVendorName('');
    setNewEnvShowAdvanced(false);
    setNewEnvDefaultProviderUrl('');
    setNewEnvAppProductVersion('');
    setNewEnvAppIconURI('');
    setNewEnvAdapterProductName('');
    setNewEnvAdapterVendorName('');
    setNewEnvAdapterProductVersion('');
    setNewEnvAdapterIconURI('');
  };

  const handleCreateEnv = async () => {
    if (!newEnvName.trim()) return;
    setCreatingEnv(true);
    setError(null);
    try {
      // Build applicationInfo only if at least one sub-field is non-empty
      const hasAppProduct = !!(newEnvAppProductName.trim() || newEnvAppVendorName.trim() || newEnvAppProductVersion.trim() || newEnvAppIconURI.trim());
      const hasAdapterProduct = !!(newEnvAdapterProductName.trim() || newEnvAdapterVendorName.trim() || newEnvAdapterProductVersion.trim() || newEnvAdapterIconURI.trim());
      const hasApplicationInfo = !!(newEnvAppKey.trim() || hasAppProduct || hasAdapterProduct);

      const applicationInfo = hasApplicationInfo ? {
        applicationKey: newEnvAppKey.trim() || null,
        applicationProduct: hasAppProduct ? {
          vendorName: newEnvAppVendorName.trim() || null,
          productName: newEnvAppProductName.trim() || null,
          productVersion: newEnvAppProductVersion.trim() || null,
          iconURI: newEnvAppIconURI.trim() || null,
        } : null,
        adapterProduct: hasAdapterProduct ? {
          vendorName: newEnvAdapterVendorName.trim() || null,
          productName: newEnvAdapterProductName.trim() || null,
          productVersion: newEnvAdapterProductVersion.trim() || null,
          iconURI: newEnvAdapterIconURI.trim() || null,
        } : null,
      } : null;

      // Wrapped under the environment key per the SIF 3.7 envelope convention, with the D2 field
      // name (consumerName) and the D3 zone shape. defaultZone is omitted entirely when there is no
      // zone -- a present-but-empty wrapper is the non-conformant form D3 removed.
      const body: any = {
        environment: {
          consumerName: newEnvName.trim(),
          type: newEnvType,
          defaultZone: newEnvDefaultZone ? { id: newEnvDefaultZone } : null,
          applicationInfo,
        },
      };

      const res = await authFetch(`/${domainId}/${solutionId}/environments/environment`, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      if (!res) return;
      if (!res.ok) { const t = await res.text(); throw new Error(`Failed to create environment (${res.status}): ${t}`); }
      const data = await res.json();
      // Registration response is wrapped under "environment" (SIF 3.7).
      const created = data.environment;
      setNewEnvCredentials({ sessionToken: created.sessionToken, sharedSecret: created.sharedSecret });
      resetNewEnvForm();
      setShowNewEnv(false);
      fetchEnvironments();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setCreatingEnv(false);
    }
  };

  const handleDeleteEnv = async (env: Environment) => {
    if (!window.confirm(`Delete environment "${env.applicationName}"?`)) return;
    const res = await authFetch(`/${domainId}/${solutionId}/environments/${env.id}`, { method: 'DELETE' });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete environment (${res.status})`); return; }
    setEnvironments((prev) => prev.filter((e) => e.id !== env.id));
  };

  const handleDiscardOldestPendingMessage = async (q: Queue) => {
    if (!window.confirm(`Discard the oldest pending message from queue "${q.name}"? This cannot be undone.`)) return;
    setQueueAction(`${q.id}:discard`);
    setError(null);
    try {
      const result = await discardOldestPendingQueueMessage(authFetch, domainId!, solutionId!, q.id);
      if (!result) return;
      if (result.discarded) {
        window.alert(`Discarded pending message ${result.message?.messageId} held by consumer "${result.message?.consumerName}" from queue "${q.name}".`);
      } else {
        window.alert(`Queue "${q.name}" has no pending message to discard.`);
      }
      await fetchQueues();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to discard oldest pending queue message');
    } finally {
      setQueueAction(null);
    }
  };

  const handlePurgeQueue = async (q: Queue) => {
    if (!window.confirm(`Purge ALL messages from queue "${q.name}"? The queue and subscriptions will remain, but discarded messages cannot be recovered.`)) return;
    setQueueAction(`${q.id}:purge`);
    setError(null);
    try {
      const accepted = await purgeQueueMessages(authFetch, domainId!, solutionId!, q.id);
      if (!accepted) return;
      window.alert(`Purge started for queue "${q.name}". Refresh to monitor its message count.`);
      await fetchQueues();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to purge queue');
    } finally {
      setQueueAction(null);
    }
  };

  const handleDeleteQueue = async (q: Queue) => {
    if (!window.confirm(`Delete queue "${q.name}"?`)) return;
    const res = await authFetch(`/${domainId}/${solutionId}/queues/${q.id}`, { method: 'DELETE' }, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete queue (${res.status})`); return; }
    setQueues((prev) => prev.filter((x) => x.id !== q.id));
  };

  const formatLastAccessed = (ts?: string) => {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  };

  const tabClass = (t: Tab) => t === tab
    ? 'px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white'
    : 'px-4 py-2 text-sm font-medium rounded text-gray-600 hover:text-gray-900 hover:bg-gray-100';

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-5xl mx-auto px-6 py-8">
        <button onClick={() => navigate(`/domains/${domainId}`)} className="text-sm text-blue-600 hover:text-blue-800 mb-4 inline-block">← Back to Solutions</button>
        <h1 className="text-2xl font-semibold text-gray-800 mb-6">{solutionId} Solution Overview</h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        <div className="flex gap-2 mb-6">
          <button className={tabClass('details')} onClick={() => setTab('details')}>Details</button>
          <button className={tabClass('environments')} onClick={() => setTab('environments')}>Environments</button>
          <button className={tabClass('queues')} onClick={() => setTab('queues')}>Queues</button>
          <button className={tabClass('services')} onClick={() => setTab('services')}>Services</button>
          <button className={tabClass('zones')} onClick={() => setTab('zones')}>Zones</button>
          <button className={tabClass('tags')} onClick={() => setTab('tags')}>Tags</button>
          <button className={tabClass('namedQueries')} onClick={() => setTab('namedQueries')}>Named Queries</button>
        </div>

        {loading ? <p className="text-gray-500 text-sm">Loading...</p> : (
          <>
            {tab === 'details' && solution && (
              <div className="bg-white rounded-lg border border-gray-200 p-6">
                <dl className="grid grid-cols-2 gap-4 text-sm">
                  <div><dt className="text-gray-500">Solution ID</dt><dd className="font-medium text-gray-800">{solution.solutionId}</dd></div>
                  <div><dt className="text-gray-500">Domain</dt><dd className="font-medium text-gray-800">{solution.domainId}</dd></div>
                  <div><dt className="text-gray-500">Description</dt><dd className="font-medium text-gray-800">{solution.description || '—'}</dd></div>
                  <div><dt className="text-gray-500">UUID</dt><dd className="font-medium text-gray-800">{solution.id}</dd></div>
                  {solution.createdAt && <div><dt className="text-gray-500">Created</dt><dd className="font-medium text-gray-800">{solution.createdAt}</dd></div>}
                </dl>
              </div>
            )}

            {tab === 'environments' && (
              <div>
                <div className="flex justify-end mb-4">
                  <button onClick={() => setShowNewEnv(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">Create New Environment</button>
                </div>
                {showNewEnv && (
                  <div className="bg-white border border-gray-200 rounded-lg p-6 mb-4 shadow-sm">
                    <h2 className="text-sm font-semibold text-gray-700 mb-4">Create New Environment</h2>

                    {/* Main fields */}
                    <div className="space-y-4">
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Application Name <span className="text-red-500">*</span></label>
                        <input type="text" value={newEnvName} onChange={(e) => setNewEnvName(e.target.value)} placeholder="e.g. MyApp" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Type <span className="text-red-500">*</span></label>
                        <select value={newEnvType} onChange={(e) => setNewEnvType(e.target.value)} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                          <option value="BROKERED">BROKERED</option>
                          <option value="DIRECT">DIRECT</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Default Zone</label>
                        <select value={newEnvDefaultZone} onChange={(e) => setNewEnvDefaultZone(e.target.value)} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                          <option value="">— None —</option>
                          {zones.map((z) => (
                            <option key={z.id} value={z.id}>{z.id}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Application Key</label>
                        <input type="text" value={newEnvAppKey} onChange={(e) => setNewEnvAppKey(e.target.value)} placeholder="Optional application key" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Application Product Name</label>
                        <input type="text" value={newEnvAppProductName} onChange={(e) => setNewEnvAppProductName(e.target.value)} placeholder="Optional product name" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Application Vendor Name</label>
                        <input type="text" value={newEnvAppVendorName} onChange={(e) => setNewEnvAppVendorName(e.target.value)} placeholder="Optional vendor name" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                    </div>

                    {/* Advanced panel */}
                    <div className="mt-5 border-t border-gray-200 pt-4">
                      <button
                        type="button"
                        onClick={() => setNewEnvShowAdvanced(!newEnvShowAdvanced)}
                        className="flex items-center gap-2 text-sm font-medium text-gray-600 hover:text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 rounded px-1 py-0.5"
                        aria-expanded={newEnvShowAdvanced}
                      >
                        <span>{newEnvShowAdvanced ? '▲' : '▼'}</span>
                        <span>Advanced</span>
                      </button>
                      {newEnvShowAdvanced && (
                        <div className="mt-4 space-y-4">
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Default Provider URL</label>
                            <input type="text" value={newEnvDefaultProviderUrl} onChange={(e) => setNewEnvDefaultProviderUrl(e.target.value)} placeholder="https://..." className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Application Product Version</label>
                            <input type="text" value={newEnvAppProductVersion} onChange={(e) => setNewEnvAppProductVersion(e.target.value)} placeholder="e.g. 1.0.0" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Application Product Icon</label>
                            <input type="text" value={newEnvAppIconURI} onChange={(e) => setNewEnvAppIconURI(e.target.value)} placeholder="https://..." className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Adapter Product Name</label>
                            <input type="text" value={newEnvAdapterProductName} onChange={(e) => setNewEnvAdapterProductName(e.target.value)} placeholder="Optional adapter product name" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Adapter Vendor Name</label>
                            <input type="text" value={newEnvAdapterVendorName} onChange={(e) => setNewEnvAdapterVendorName(e.target.value)} placeholder="Optional adapter vendor name" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Adapter Product Version</label>
                            <input type="text" value={newEnvAdapterProductVersion} onChange={(e) => setNewEnvAdapterProductVersion(e.target.value)} placeholder="e.g. 2.0.0" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">Adapter Product Icon</label>
                            <input type="text" value={newEnvAdapterIconURI} onChange={(e) => setNewEnvAdapterIconURI(e.target.value)} placeholder="https://..." className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Action buttons */}
                    <div className="mt-5 flex gap-3">
                      <button onClick={handleCreateEnv} disabled={creatingEnv || !newEnvName.trim()} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creatingEnv ? 'Creating...' : 'Create'}</button>
                      <button onClick={() => { setShowNewEnv(false); resetNewEnvForm(); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2">Cancel</button>
                    </div>
                  </div>
                )}
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">ID</th>
                        <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Delete</th>
                      </tr>
                    </thead>
                    <tbody>
                      {environments.length === 0 ? (
                        <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">No environments found</td></tr>
                      ) : environments.map((env) => (
                        <tr key={env.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3"><button onClick={() => navigate(`/domains/${domainId}/solutions/${solutionId}/environments/${env.id}`)} className="text-blue-600 hover:text-blue-800 hover:underline">{env.applicationName}</button></td>
                          <td className="px-4 py-3 text-gray-600">{env.type}</td>
                          <td className="px-4 py-3 text-gray-500 text-xs font-mono">{env.id}</td>
                          <td className="px-4 py-3 text-right"><button onClick={() => handleDeleteEnv(env)} className="text-red-500 hover:text-red-700 font-medium" title="Delete environment">✕</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {tab === 'queues' && (
              <div>
                <div className="flex justify-end mb-4">
                  <button onClick={loadQueues} className="text-gray-400 hover:text-gray-600 text-sm" title="Refresh">↻ Refresh</button>
                </div>
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Name</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Owner</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Messages</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Last Accessed</th>
                        <th className="text-right px-4 py-3 font-medium text-gray-600">Actions</th>
                      </tr>
                    </thead>
                    <tbody>
                      {queues.length === 0 ? (
                        <tr><td colSpan={5} className="px-4 py-6 text-center text-gray-400">No queues found in this solution</td></tr>
                      ) : queues.map((q) => (
                        <tr key={q.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-800">{q.name}</td>
                          <td className="px-4 py-3 text-gray-600 text-xs font-mono">{q.ownerId}</td>
                          <td className="px-4 py-3 text-gray-600">{q.messageCount ?? '—'}</td>
                          <td className="px-4 py-3 text-gray-600 text-xs">{formatLastAccessed(q.lastAccessed)}</td>
                          <td className="px-4 py-3 text-right flex gap-2 justify-end">
                            <button onClick={() => setLiveQueue(q)} className="text-blue-600 hover:text-blue-800 text-xs font-medium">Live Messages</button>
                            <button onClick={() => setPendingQueue(q)} className="text-purple-600 hover:text-purple-800 text-xs font-medium">Pending</button>
                            <button
                              onClick={() => handleDiscardOldestPendingMessage(q)}
                              disabled={queueAction !== null}
                              className="text-amber-600 hover:text-amber-800 text-xs font-medium disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                              {queueAction === `${q.id}:discard` ? 'Discarding...' : 'Discard Oldest Pending'}
                            </button>
                            <button
                              onClick={() => handlePurgeQueue(q)}
                              disabled={queueAction !== null}
                              className="text-red-600 hover:text-red-800 text-xs font-medium disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                              {queueAction === `${q.id}:purge` ? 'Purging...' : 'Purge All'}
                            </button>
                            <button onClick={() => handleDeleteQueue(q)} disabled={queueAction !== null} className="text-red-500 hover:text-red-700 text-xs font-medium disabled:opacity-40 disabled:cursor-not-allowed">Delete</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {tab === 'services' && (
              <div>
                <div className="flex justify-end mb-4">
                  <button onClick={() => setShowNewService(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">Add Service</button>
                </div>
                {showNewService && (
                  <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 shadow-sm">
                    <h2 className="text-sm font-medium text-gray-700 mb-3">Add Service</h2>
                    <div className="flex gap-3 items-end">
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500 mb-1">Service Name</label>
                        <input type="text" value={newServiceName} onChange={(e) => setNewServiceName(e.target.value)} placeholder="e.g. StudentPersonal" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div>
                        <label className="block text-xs text-gray-500 mb-1">Service Type</label>
                        <select value={newServiceType} onChange={(e) => setNewServiceType(e.target.value)} className="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                          <option value="UTILITY">UTILITY</option>
                          <option value="OBJECT">OBJECT</option>
                          <option value="FUNCTIONAL">FUNCTIONAL</option>
                          <option value="SERVICEPATH">SERVICEPATH</option>
                          <option value="SERVICE">SERVICE</option>
                        </select>
                        {/* XQUERYTEMPLATE is deliberately absent. Those service rows are created for
                            you when you add a Named Query -- a query service with no template behind
                            it names a query that does not exist. */}
                      </div>
                      <button onClick={handleCreateService} disabled={creatingService || !newServiceName.trim()} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creatingService ? 'Creating...' : 'Create'}</button>
                      <button onClick={() => { setShowNewService(false); setNewServiceName(''); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2">Cancel</button>
                    </div>
                  </div>
                )}
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Service Name</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Service Type</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Created</th>
                        <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Delete</th>
                      </tr>
                    </thead>
                    <tbody>
                      {services.length === 0 ? (
                        <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">No services found in this solution</td></tr>
                      ) : services.map((svc) => (
                        <tr key={svc.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3 text-gray-800">
                            {svc.serviceType === 'XQUERYTEMPLATE' ? (
                              <button
                                onClick={() => { setEditQueryToken(svc.serviceName); setTab('namedQueries'); }}
                                className="text-blue-600 hover:text-blue-800 hover:underline"
                                title="Open this Named Query template"
                              >
                                {svc.serviceName}
                              </button>
                            ) : svc.serviceName}
                          </td>
                          <td className="px-4 py-3 text-gray-600">{svc.serviceType}</td>
                          <td className="px-4 py-3 text-gray-600 text-xs">{svc.createdAt ? new Date(svc.createdAt).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : '—'}</td>
                          <td className="px-4 py-3 text-right"><button onClick={() => handleDeleteService(svc)} className="text-red-500 hover:text-red-700 font-medium" title="Delete service">✕</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {tab === 'zones' && (
              <div>
                <div className="flex justify-end mb-4">
                  <button onClick={() => setShowNewZone(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">Add New Zone</button>
                </div>
                {showNewZone && (
                  <div className="bg-white border border-gray-200 rounded-lg p-4 mb-4 shadow-sm">
                    <h2 className="text-sm font-medium text-gray-700 mb-3">Add New Zone</h2>
                    <div className="flex gap-3 items-end">
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500 mb-1">Zone ID</label>
                        <input type="text" value={newZoneId} onChange={(e) => setNewZoneId(e.target.value)} placeholder="e.g. district-1" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500 mb-1">Description (optional)</label>
                        <input type="text" value={newZoneDescription} onChange={(e) => setNewZoneDescription(e.target.value)} placeholder="e.g. District 1 schools" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <button onClick={handleCreateZone} disabled={creatingZone || !newZoneId.trim()} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creatingZone ? 'Creating...' : 'Create'}</button>
                      <button onClick={() => { setShowNewZone(false); setNewZoneId(''); setNewZoneDescription(''); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2">Cancel</button>
                    </div>
                  </div>
                )}
                <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 border-b border-gray-200">
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Zone ID</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                        <th className="text-left px-4 py-3 font-medium text-gray-600">Created</th>
                        <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Delete</th>
                      </tr>
                    </thead>
                    <tbody>
                      {zones.length === 0 ? (
                        <tr><td colSpan={4} className="px-4 py-6 text-center text-gray-400">No zones found in this solution</td></tr>
                      ) : zones.map((zone) => (
                        <tr key={zone.id} className="border-b border-gray-100 hover:bg-gray-50">
                          <td className="px-4 py-3 font-medium"><button onClick={() => navigate(`/domains/${domainId}/solutions/${solutionId}/zones/${zone.id}`)} className="text-blue-600 hover:text-blue-800 hover:underline">{zone.id}</button></td>
                          <td className="px-4 py-3 text-gray-600">{zone.description || '—'}</td>
                          <td className="px-4 py-3 text-gray-600 text-xs">{zone.createdAt ? new Date(zone.createdAt).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' }) : '—'}</td>
                          <td className="px-4 py-3 text-right"><button onClick={() => handleDeleteZone(zone)} className="text-red-500 hover:text-red-700 font-medium" title="Delete zone">✕</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {tab === 'tags' && (
              <SolutionTagsPage domainId={domainId!} solutionId={solutionId!} />
            )}

            {tab === 'namedQueries' && (
              <NamedQueriesTab
                domainId={domainId!}
                solutionId={solutionId!}
                authFetch={authFetch}
                openForEdit={editQueryToken}
                onOpenForEditHandled={() => setEditQueryToken(null)}
              />
            )}
          </>
        )}
      </main>
      {liveQueue && (
        <LiveQueueChart
          domainId={domainId!}
          solutionId={solutionId!}
          queueId={liveQueue.id}
          queueName={liveQueue.name}
          onClose={() => setLiveQueue(null)}
        />
      )}
      {pendingQueue && (
        <PendingQueueMessagesDialog
          domainId={domainId!}
          solutionId={solutionId!}
          queueId={pendingQueue.id}
          queueName={pendingQueue.name}
          authFetch={authFetch}
          onClose={() => setPendingQueue(null)}
        />
      )}
      {newEnvCredentials && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-lg w-full mx-4 p-6">
            <div className="flex items-center gap-2 mb-4">
              <div className="w-8 h-8 bg-green-100 rounded-full flex items-center justify-center">
                <span className="text-green-600 text-lg">✓</span>
              </div>
              <h3 className="text-lg font-semibold text-gray-900">Environment Created</h3>
            </div>
            <div className="bg-amber-50 border border-amber-200 rounded p-3 mb-4">
              <p className="text-sm text-amber-800 font-medium">Save these credentials now — the shared secret will not be shown again.</p>
            </div>
            <div className="space-y-3">
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Session Token</label>
                <div className="flex items-center gap-2">
                  <code className="block w-full bg-gray-100 rounded px-3 py-2 text-sm font-mono text-gray-800 break-all">{newEnvCredentials.sessionToken}</code>
                  <button onClick={() => navigator.clipboard.writeText(newEnvCredentials.sessionToken)} className="text-gray-400 hover:text-gray-600 shrink-0" title="Copy">📋</button>
                </div>
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-500 mb-1">Shared Secret</label>
                <div className="flex items-center gap-2">
                  <code className="block w-full bg-gray-100 rounded px-3 py-2 text-sm font-mono text-gray-800 break-all">{newEnvCredentials.sharedSecret}</code>
                  <button onClick={() => navigator.clipboard.writeText(newEnvCredentials.sharedSecret)} className="text-gray-400 hover:text-gray-600 shrink-0" title="Copy">📋</button>
                </div>
              </div>
            </div>
            <div className="mt-6 flex justify-end">
              <button onClick={() => setNewEnvCredentials(null)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">I've saved these credentials</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
