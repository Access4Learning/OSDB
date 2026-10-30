import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Navbar from '../components/Navbar';
import UsersTab from '../components/UsersTab';
import EnvironmentDetailView from '../components/EnvironmentDetailView';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';
import LiveQueueChart from '../components/LiveQueueChart';
import PendingQueueMessagesDialog from '../components/PendingQueueMessagesDialog';
import EnvironmentTagsSection from '../components/EnvironmentTagsSection';
import ProviderRegistrationsSection from '../components/ProviderRegistrationsSection';
import AlertsTab from '../components/AlertsTab';
import { useSubscriptionDropdowns } from '../hooks/useSubscriptionDropdowns';
import { discardOldestPendingQueueMessage, purgeQueueMessages } from '../utils/queueActions';

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

interface Subscription {
  id: string;
  queueId: string;
  serviceName: string;
  serviceType: string;
  zoneId: string | null;
  contextId: string | null;
  messageType: string;
  createdAt?: string;
}

type Tab = 'details' | 'queues' | 'acl' | 'providers' | 'users' | 'tags' | 'alerts';

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

interface ContextInfo {
  id: string; // UUID
  zoneId: string;
  contextId: string;
}

interface ZoneInfo {
  id: string; // UUID
  zoneId: string;
}

export default function EnvironmentOverviewPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();
  const { domainId, solutionId, envId } = useParams<{ domainId: string; solutionId: string; envId: string }>();

  const [tab, setTab] = useState<Tab>('details');
  const [env, setEnv] = useState<EnvironmentDetail | null>(null);
  const [queues, setQueues] = useState<Queue[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [liveQueue, setLiveQueue] = useState<Queue | null>(null);
  const [pendingQueue, setPendingQueue] = useState<Queue | null>(null);
  const [queueAction, setQueueAction] = useState<string | null>(null);

  // ACL tab state
  const [aclEntries, setAclEntries] = useState<AclEntry[]>([]);
  const [allContexts, setAllContexts] = useState<ContextInfo[]>([]);
  const [, setAllZones] = useState<ZoneInfo[]>([]);
  const [aclLoading, setAclLoading] = useState(false);
  const [aclFilter, setAclFilter] = useState({ zone: '', context: '', service: '' });
  const [showAllServices, setShowAllServices] = useState(false);
  const [allContextServices, setAllContextServices] = useState<{ id: string; contextUuid: string; contextId: string; zoneId: string; serviceName: string; serviceType: string }[]>([]);
  const [openDropdown, setOpenDropdown] = useState<string | null>(null); // 'zone:zoneName' or 'ctx:zoneName:ctxUuid'
  const [dropdownSub, setDropdownSub] = useState<'add' | 'remove' | null>(null);

  // Create Queue state
  const [showCreateQueue, setShowCreateQueue] = useState(false);
  const [newQueueName, setNewQueueName] = useState('');
  const [newQueueMaxConn, setNewQueueMaxConn] = useState('');
  const [creatingQueue, setCreatingQueue] = useState(false);

  // Subscription management state
  const [expandedQueueId, setExpandedQueueId] = useState<string | null>(null);
  const [subscriptions, setSubscriptions] = useState<Record<string, Subscription[]>>({});
  const [subscriptionLoading, setSubscriptionLoading] = useState<string | null>(null);

  const authFetch = async (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts, extraHeaders);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  const subDropdowns = useSubscriptionDropdowns(
    expandedQueueId || '',
    authFetch,
    domainId!,
    solutionId!,
    envId!,
    expandedQueueId ? (subscriptions[expandedQueueId] || []) : []
  );

  const fetchEnv = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/environments/${envId}`);
    if (!res) return;
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
  };

  const fetchQueues = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/queues`, undefined, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!res) return;
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
  };

  const fetchAclEntries = async () => {
    const res = await authFetch(`/${domainId}/${solutionId}/acl-entries?environmentId=${envId}`, undefined, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!res) return;
    if (!res.ok) { setError(`Failed to fetch ACL entries (${res.status})`); return; }
    const data = await res.json();
    setAclEntries(Array.isArray(data) ? data : []);
  };

  const fetchAllContexts = async () => {
    // First get all zones
    const zonesRes = await authFetch(`/${domainId}/${solutionId}/zones`, undefined, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!zonesRes || !zonesRes.ok) return;
    const zonesData = await zonesRes.json();
    const zoneList: ZoneInfo[] = zonesData.zones?.zone
      ? zonesData.zones.zone.map((z: any) => ({ id: z.id, zoneId: z.id }))
      : [];
    setAllZones(zoneList);

    // Fetch contexts for each zone
    const contextResults: ContextInfo[] = [];
    for (const zone of zoneList) {
      const ctxRes = await authFetch(`/${domainId}/${solutionId}/zones/${zone.zoneId}/contexts`, undefined, {
        'X-Run-As-Environment': 'broker-services',
      });
      if (!ctxRes || !ctxRes.ok) continue;
      const ctxData = await ctxRes.json();
      const contexts: ContextInfo[] = ctxData.contexts?.context
        ? ctxData.contexts.context.map((c: any) => ({ id: c.id, zoneId: c.zoneId, contextId: c.id }))
        : [];
      contextResults.push(...contexts);
    }
    setAllContexts(contextResults);
  };

  const loadAclTab = async () => {
    setAclLoading(true);
    setError(null);
    await Promise.all([fetchAclEntries(), fetchAllContexts()]);
    setAclLoading(false);
  };

  const fetchAllContextServices = async () => {
    // Fetch context-services for each zone (using the zone-level query)
    const zonesRes = await authFetch(`/${domainId}/${solutionId}/zones`, undefined, { 'X-Run-As-Environment': 'broker-services' });
    if (!zonesRes || !zonesRes.ok) return;
    const zonesData = await zonesRes.json();
    const zoneList = zonesData.zones?.zone || [];
    const allCs: { id: string; contextUuid: string; contextId: string; zoneId: string; serviceName: string; serviceType: string }[] = [];
    for (const zone of zoneList) {
      const res = await authFetch(`/${domainId}/${solutionId}/context-services?zoneId=${zone.id}`, undefined, { 'X-Run-As-Environment': 'broker-services' });
      if (!res || !res.ok) continue;
      const data = await res.json();
      if (Array.isArray(data)) allCs.push(...data);
    }
    setAllContextServices(allCs);
  };

  const handleToggleAcl = async (serviceName: string, zoneId: string | null, contextId: string | null, accessType: AccessType, existingEntry: AclEntry | undefined) => {
    if (existingEntry) {
      // Delete existing entry
      const res = await authFetch(`/${domainId}/${solutionId}/acl-entries/${existingEntry.id}`, { method: 'DELETE' }, {
        'X-Run-As-Environment': 'broker-services',
      });
      if (!res || !res.ok) { setError(`Failed to delete ACL entry (${res?.status})`); return; }
      setAclEntries((prev) => prev.filter((e) => e.id !== existingEntry.id));
    } else {
      // Create new entry
      const body = {
        environmentId: envId,
        serviceName,
        zoneId: zoneId || null,
        contextId: contextId || null,
        accessType,
        accessLevel: 'APPROVED',
      };
      const res = await authFetch(`/${domainId}/${solutionId}/acl-entries`, {
        method: 'POST',
        body: JSON.stringify(body),
      }, { 'X-Run-As-Environment': 'broker-services' });
      if (!res || !res.ok) {
        let msg = '';
        try {
          const errBody = await res?.json();
          msg = errBody?.error?.message || JSON.stringify(errBody);
        } catch {
          msg = await res?.text().catch(() => '') || '';
        }
        setError(`Failed to create ACL entry (${res?.status})${msg ? ': ' + msg : ''}`);
        return;
      }
      const created = await res.json();
      setAclEntries((prev) => [...prev, created]);
    }
  };

  const formatLastAccessed = (ts?: string) => {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  };

  // Bulk add ACL entries for all services in a zone or context
  const handleBulkAdd = async (accessType: AccessType, zoneName: string, contextId?: string | null) => {
    // Gather all services that should get this access type
    const targets: { serviceName: string; zoneId: string; ctxId: string | null }[] = [];

    if (contextId) {
      // Scope: single context
      const ctxUuid = allContexts.find((c) => c.zoneId === zoneName && c.contextId === contextId)?.id;
      const servicesInCtx = ctxUuid ? allContextServices.filter((cs) => cs.contextUuid === ctxUuid) : [];
      for (const cs of servicesInCtx) {
        targets.push({ serviceName: cs.serviceName, zoneId: zoneName, ctxId: contextId });
      }
      // Also include services from existing ACL entries for this context
      for (const entry of aclEntries) {
        if (entry.zoneId === zoneName && entry.contextId === contextId && !targets.find((t) => t.serviceName === entry.serviceName)) {
          targets.push({ serviceName: entry.serviceName, zoneId: zoneName, ctxId: contextId });
        }
      }
    } else {
      // Scope: entire zone — all contexts in this zone
      const contextsInZone = allContexts.filter((c) => c.zoneId === zoneName);
      for (const ctx of contextsInZone) {
        const servicesInCtx = allContextServices.filter((cs) => cs.contextUuid === ctx.id);
        for (const cs of servicesInCtx) {
          targets.push({ serviceName: cs.serviceName, zoneId: zoneName, ctxId: ctx.contextId });
        }
        // Also include services from existing ACL entries
        for (const entry of aclEntries) {
          if (entry.zoneId === zoneName && entry.contextId === ctx.contextId && !targets.find((t) => t.serviceName === entry.serviceName && t.ctxId === ctx.contextId)) {
            targets.push({ serviceName: entry.serviceName, zoneId: zoneName, ctxId: ctx.contextId });
          }
        }
      }
    }

    // Filter out targets that already have this access type
    const existingSet = new Set(aclEntries.filter((e) => e.accessType === accessType).map((e) => `${e.serviceName}|${e.zoneId}|${e.contextId}`));
    const toCreate = targets.filter((t) => !existingSet.has(`${t.serviceName}|${t.zoneId}|${t.ctxId}`));

    const newEntries: AclEntry[] = [];
    for (const t of toCreate) {
      const body = { environmentId: envId, serviceName: t.serviceName, zoneId: t.zoneId, contextId: t.ctxId, accessType, accessLevel: 'APPROVED' };
      const res = await authFetch(`/${domainId}/${solutionId}/acl-entries`, { method: 'POST', body: JSON.stringify(body) }, { 'X-Run-As-Environment': 'broker-services' });
      if (res && res.ok) {
        const created = await res.json();
        newEntries.push(created);
      }
    }
    if (newEntries.length > 0) setAclEntries((prev) => [...prev, ...newEntries]);
    setOpenDropdown(null);
    setDropdownSub(null);
  };

  // Bulk remove ACL entries for all services in a zone or context
  const handleBulkRemove = async (accessType: AccessType, zoneName: string, contextId?: string | null) => {
    let entriesToDelete: AclEntry[];
    if (contextId) {
      entriesToDelete = aclEntries.filter((e) => e.zoneId === zoneName && e.contextId === contextId && e.accessType === accessType);
    } else {
      entriesToDelete = aclEntries.filter((e) => e.accessType === accessType && e.zoneId === zoneName);
    }

    const deletedIds: string[] = [];
    for (const entry of entriesToDelete) {
      const res = await authFetch(`/${domainId}/${solutionId}/acl-entries/${entry.id}`, { method: 'DELETE' }, { 'X-Run-As-Environment': 'broker-services' });
      if (res && res.ok) deletedIds.push(entry.id);
    }
    if (deletedIds.length > 0) setAclEntries((prev) => prev.filter((e) => !deletedIds.includes(e.id)));
    setOpenDropdown(null);
    setDropdownSub(null);
  };

  const loadAll = async () => {
    setLoading(true);
    setError(null);
    await Promise.all([fetchEnv(), fetchQueues()]);
    setLoading(false);
  };

  useEffect(() => { loadAll(); }, []);

  useEffect(() => {
    if (tab === 'acl' && aclEntries.length === 0 && !aclLoading) {
      loadAclTab();
    }
  }, [tab]);

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

  const handleCreateQueue = async () => {
    if (!newQueueName.trim()) return;
    setCreatingQueue(true);
    setError(null);
    const body = {
      queue: {
        name: newQueueName.trim(),
        ownerId: envId,
        maxRetries: 3,
        retryDelayMs: 5000,
        maxConcurrentConnections: newQueueMaxConn ? parseInt(newQueueMaxConn, 10) : null,
      },
    };
    const res = await authFetch(`/${domainId}/${solutionId}/queues/queue`, {
      method: 'POST',
      body: JSON.stringify(body),
    }, { 'X-Run-As-Environment': 'broker-services' });
    setCreatingQueue(false);
    if (!res) return;
    if (!res.ok) { setError(`Failed to create queue (${res.status})`); return; }
    const data = await res.json();
    const created = data.queue;
    setQueues((prev) => [...prev, { id: created.id, name: created.name, ownerId: created.ownerId, maxConcurrentConnections: created.maxConcurrentConnections, messageCount: created.messageCount, lastAccessed: created.lastAccessed }]);
    setNewQueueName('');
    setNewQueueMaxConn('');
    setShowCreateQueue(false);
  };

  const fetchSubscriptions = async (queueId: string) => {
    setSubscriptionLoading(queueId);
    const res = await authFetch(`/${domainId}/${solutionId}/subscriptions?queueId=${queueId}`, undefined, {
      'X-Run-As-Environment': 'broker-services',
    });
    setSubscriptionLoading(null);
    if (!res) return;
    if (!res.ok) { setError(`Failed to fetch subscriptions (${res.status})`); return; }
    const data = await res.json();
    const list: Subscription[] = Array.isArray(data) ? data : (data.subscriptions?.subscription || []);
    setSubscriptions((prev) => ({ ...prev, [queueId]: list }));
  };

  const handleDeleteSubscription = async (queueId: string, subId: string) => {
    const res = await authFetch(`/${domainId}/${solutionId}/subscriptions/${subId}`, { method: 'DELETE' }, {
      'X-Run-As-Environment': 'broker-services',
    });
    if (!res) return;
    if (!res.ok) { setError(`Failed to delete subscription (${res.status})`); return; }
    setSubscriptions((prev) => ({ ...prev, [queueId]: (prev[queueId] || []).filter((s) => s.id !== subId) }));
  };

  const tabClass = (t: Tab) => t === tab
    ? 'px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white'
    : 'px-4 py-2 text-sm font-medium rounded text-gray-600 hover:text-gray-900 hover:bg-gray-100';

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-5xl mx-auto px-6 py-8">
        <button onClick={() => navigate(`/domains/${domainId}/solutions/${solutionId}`)} className="text-sm text-blue-600 hover:text-blue-800 mb-4 inline-block">← Back to Solution</button>
        <h1 className="text-2xl font-semibold text-gray-800 mb-6">{env?.consumerName || envId} Environment Overview</h1>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        <div className="flex gap-2 mb-6">
          <button className={tabClass('details')} onClick={() => setTab('details')}>Details</button>
          <button className={tabClass('queues')} onClick={() => setTab('queues')}>Queues</button>
          <button className={tabClass('acl')} onClick={() => setTab('acl')}>Manage ACL</button>
          <button className={tabClass('providers')} onClick={() => setTab('providers')}>Providers</button>
          <button className={tabClass('users')} onClick={() => setTab('users')}>Users</button>
          <button className={tabClass('tags')} onClick={() => setTab('tags')}>Tags</button>
          <button className={tabClass('alerts')} onClick={() => setTab('alerts')}>Alerts</button>
        </div>

        {loading ? <p className="text-gray-500 text-sm">Loading...</p> : (
          <>
            {tab === 'details' && (
              <EnvironmentDetailView domainId={domainId!} solutionId={solutionId!} envId={envId!} authFetch={authFetch} />
            )}

            {tab === 'queues' && (
              <div>
                <div className="flex justify-end gap-2 mb-4">
                  <button onClick={() => setShowCreateQueue((v) => !v)} className="bg-blue-600 text-white text-sm px-3 py-1.5 rounded hover:bg-blue-700">+ Create Queue</button>
                  <button onClick={() => { fetchQueues(); }} className="text-gray-400 hover:text-gray-600 text-sm" title="Refresh">↻ Refresh</button>
                </div>

                {showCreateQueue && (
                  <div className="bg-white rounded-lg border border-gray-200 p-4 mb-4">
                    <h3 className="text-sm font-medium text-gray-700 mb-3">Create New Queue</h3>
                    <div className="flex gap-3 items-end">
                      <div className="flex-1">
                        <label className="block text-xs text-gray-500 mb-1">Queue Name *</label>
                        <input type="text" value={newQueueName} onChange={(e) => setNewQueueName(e.target.value)} placeholder="my-queue" className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <div className="w-48">
                        <label className="block text-xs text-gray-500 mb-1">Max Connections</label>
                        <input type="number" value={newQueueMaxConn} onChange={(e) => setNewQueueMaxConn(e.target.value)} placeholder="(unlimited)" className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                      </div>
                      <button onClick={handleCreateQueue} disabled={creatingQueue || !newQueueName.trim()} className="bg-blue-600 text-white text-sm px-4 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">{creatingQueue ? 'Creating...' : 'Create'}</button>
                      <button onClick={() => { setShowCreateQueue(false); setNewQueueName(''); setNewQueueMaxConn(''); }} className="text-gray-500 hover:text-gray-700 text-sm px-3 py-1.5">Cancel</button>
                    </div>
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
                        <React.Fragment key={q.id}>
                          <tr className="border-b border-gray-100 hover:bg-gray-50">
                            <td className="px-4 py-3 text-gray-800">{q.name}</td>
                            <td className="px-4 py-3 text-gray-600">{q.maxConcurrentConnections ?? '—'}</td>
                            <td className="px-4 py-3 text-gray-600">{q.messageCount ?? '—'}</td>
                            <td className="px-4 py-3 text-gray-600 text-xs">{formatLastAccessed(q.lastAccessed)}</td>
                            <td className="px-4 py-3 text-right flex gap-2 justify-end">
                              <button onClick={() => { const next = expandedQueueId === q.id ? null : q.id; setExpandedQueueId(next); if (next && !subscriptions[q.id]) fetchSubscriptions(q.id); }} className="text-purple-600 hover:text-purple-800 text-xs font-medium">Subscriptions</button>
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
                          {expandedQueueId === q.id && (
                            <tr>
                              <td colSpan={5} className="px-4 py-4 bg-gray-50 border-b border-gray-200">
                                <div className="space-y-4">
                                  <h4 className="text-sm font-medium text-gray-700">Subscriptions for "{q.name}"</h4>

                                  {subscriptionLoading === q.id ? (
                                    <p className="text-xs text-gray-400">Loading subscriptions...</p>
                                  ) : (
                                    <>
                                      {/* Subscription list */}
                                      {(subscriptions[q.id] || []).length === 0 ? (
                                        <p className="text-xs text-gray-400">No subscriptions</p>
                                      ) : (
                                        <div className="bg-white rounded border border-gray-200 overflow-hidden">
                                          <table className="w-full text-xs">
                                            <thead>
                                              <tr className="bg-gray-50 border-b border-gray-200">
                                                <th className="text-left px-3 py-2 font-medium text-gray-600">Service Name</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600">Service Type</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600">Zone ID</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600">Context ID</th>
                                                <th className="text-left px-3 py-2 font-medium text-gray-600">Message Type</th>
                                                <th className="text-right px-3 py-2 font-medium text-gray-600"></th>
                                              </tr>
                                            </thead>
                                            <tbody>
                                              {(subscriptions[q.id] || []).map((sub) => (
                                                <tr key={sub.id} className="border-b border-gray-100">
                                                  <td className="px-3 py-2 text-gray-800">{sub.serviceName}</td>
                                                  <td className="px-3 py-2 text-gray-600">{sub.serviceType || '—'}</td>
                                                  <td className="px-3 py-2 text-gray-600">{sub.zoneId || '—'}</td>
                                                  <td className="px-3 py-2 text-gray-600">{sub.contextId || '—'}</td>
                                                  <td className="px-3 py-2 text-gray-600">{sub.messageType}</td>
                                                  <td className="px-3 py-2 text-right">
                                                    <button onClick={async () => {
                                                      await handleDeleteSubscription(q.id, sub.id);
                                                      subDropdowns.onSubscriptionDeleted({ serviceName: sub.serviceName, zoneId: sub.zoneId, contextId: sub.contextId, messageType: sub.messageType });
                                                    }} className="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
                                                  </td>
                                                </tr>
                                              ))}
                                            </tbody>
                                          </table>
                                        </div>
                                      )}

                                      {/* Create subscription form */}
                                      <div className="border-t border-gray-200 pt-3 mt-3">
                                        <h5 className="text-xs font-medium text-gray-600 mb-2">Add Subscription</h5>

                                        {subDropdowns.loading ? (
                                          <p className="text-xs text-gray-400">Loading ACL entries...</p>
                                        ) : subDropdowns.error ? (
                                          <p className="text-xs text-red-500">{subDropdowns.error}</p>
                                        ) : subDropdowns.serviceNames.length === 0 ? (
                                          <p className="text-xs text-gray-400">All available subscriptions created</p>
                                        ) : (
                                          <>
                                            <div className="grid grid-cols-5 gap-2 items-end">
                                              <div>
                                                <label className="block text-xs text-gray-500 mb-1">Message Type *</label>
                                                <select value={subDropdowns.messageType} onChange={(e) => subDropdowns.setMessageType(e.target.value as 'EVENT' | 'DELAYED_REQUEST')} className="w-full border border-gray-300 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500">
                                                  <option value="EVENT">EVENT</option>
                                                  <option value="DELAYED_REQUEST">DELAYED_REQUEST</option>
                                                </select>
                                              </div>
                                              <div>
                                                <label className="block text-xs text-gray-500 mb-1">Service Name *</label>
                                                <select value={subDropdowns.selectedService || ''} onChange={(e) => subDropdowns.setSelectedService(e.target.value)} disabled={subDropdowns.serviceNames.length === 0} className="w-full border border-gray-300 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed">
                                                  <option value="">— Select service —</option>
                                                  {subDropdowns.serviceNames.map((s) => <option key={s} value={s}>{s}</option>)}
                                                </select>
                                              </div>
                                              <div>
                                                <label className="block text-xs text-gray-500 mb-1">Zone ID *</label>
                                                <select value={subDropdowns.selectedZone || ''} onChange={(e) => subDropdowns.setSelectedZone(e.target.value)} disabled={!subDropdowns.selectedService} className="w-full border border-gray-300 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed">
                                                  <option value="">{subDropdowns.selectedService ? '— Select zone —' : '— Select service first —'}</option>
                                                  {subDropdowns.zoneIds.map((z) => <option key={z} value={z}>{z || '(wildcard)'}</option>)}
                                                </select>
                                              </div>
                                              <div>
                                                <label className="block text-xs text-gray-500 mb-1">Context ID *</label>
                                                <select value={subDropdowns.selectedContext || ''} onChange={(e) => subDropdowns.setSelectedContext(e.target.value)} disabled={subDropdowns.selectedZone === null} className="w-full border border-gray-300 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed">
                                                  <option value="">{subDropdowns.selectedZone !== null ? '— Select context —' : '— Select zone first —'}</option>
                                                  {subDropdowns.contextIds.map((c) => <option key={c} value={c}>{c || '(wildcard)'}</option>)}
                                                </select>
                                              </div>
                                              <div>
                                                <label className="block text-xs text-gray-500 mb-1">Service Type</label>
                                                <select value={subDropdowns.serviceType} onChange={(e) => subDropdowns.setServiceType(e.target.value)} className="w-full border border-gray-300 rounded px-2 py-1 text-xs text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500">
                                                  <option value="OBJECT">OBJECT</option>
                                                  <option value="UTILITY">UTILITY</option>
                                                  <option value="FUNCTIONAL">FUNCTIONAL</option>
                                                  <option value="SERVICEPATH">SERVICEPATH</option>
                                                </select>
                                              </div>
                                            </div>
                                            <button onClick={async () => { await subDropdowns.submitSubscription(); await fetchSubscriptions(q.id); }} disabled={!subDropdowns.isFormComplete || subDropdowns.isSubmitting} className="mt-2 bg-blue-600 text-white text-xs px-3 py-1 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed">
                                              {subDropdowns.isSubmitting ? 'Creating...' : 'Add Subscription'}
                                            </button>
                                          </>
                                        )}
                                      </div>
                                    </>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </React.Fragment>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {tab === 'users' && (
              <UsersTab domainId={domainId!} solutionId={solutionId!} envId={envId!} authFetch={authFetch} />
            )}

            {tab === 'acl' && (
              <div onClick={() => { if (openDropdown) { setOpenDropdown(null); setDropdownSub(null); } }}>
                {aclLoading ? <p className="text-gray-500 text-sm">Loading ACL entries...</p> : (
                  <>
                    {/* Toggle + Filters */}
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex gap-4 flex-1">
                        <input type="text" placeholder="Filter by zone..." value={aclFilter.zone} onChange={(e) => setAclFilter((f) => ({ ...f, zone: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        <input type="text" placeholder="Filter by context..." value={aclFilter.context} onChange={(e) => setAclFilter((f) => ({ ...f, context: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        <input type="text" placeholder="Filter by service..." value={aclFilter.service} onChange={(e) => setAclFilter((f) => ({ ...f, service: e.target.value }))} className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        <button onClick={() => loadAclTab()} className="text-gray-400 hover:text-gray-600 text-sm px-2" title="Refresh">↻</button>
                      </div>
                      <button
                        onClick={async () => {
                          const next = !showAllServices;
                          setShowAllServices(next);
                          if (next && allContextServices.length === 0) {
                            await fetchAllContextServices();
                          }
                        }}
                        className="ml-4 text-sm text-blue-600 hover:text-blue-800 whitespace-nowrap"
                      >
                        {showAllServices ? 'Show Only ACL Services' : 'Show All Services'}
                      </button>
                    </div>

                    {/* Flat ACL Table */}
                    {(() => {
                      // Build context lookup for "Show All Services" feature (contextUuid → { zoneName, contextName })
                      const contextLookup = new Map<string, { zoneName: string; contextName: string }>();
                      for (const ctx of allContexts) {
                        contextLookup.set(ctx.id, { zoneName: ctx.zoneId, contextName: ctx.contextId });
                      }

                      // Group ACL entries by zone → context → service → accessTypes
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

                      // When "Show All Services" is on, merge all context-services into the grid
                      if (showAllServices) {
                        for (const cs of allContextServices) {
                          // Use zoneId and contextId from the ContextServiceDTO response
                          const zoneName = cs.zoneId || '(unknown zone)';
                          const contextName = cs.contextId || '(unknown)';
                          if (!zoneMap.has(zoneName)) zoneMap.set(zoneName, new Map());
                          const ctxMap = zoneMap.get(zoneName)!;
                          if (!ctxMap.has(contextName)) ctxMap.set(contextName, new Map());
                          const svcMap = ctxMap.get(contextName)!;
                          if (!svcMap.has(cs.serviceName)) svcMap.set(cs.serviceName, new Map());
                        }
                      }

                      // Convert to array structure
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

                      // Compute total row count for each zone and context (for rowSpan)
                      const zoneRowCounts = filtered.map((zg) => zg.contexts.reduce((sum, cg) => sum + cg.rows.length, 0));
                      const ctxRowCounts = filtered.map((zg) => zg.contexts.map((cg) => cg.rows.length));

                      // Dropdown renderer
                      const renderDropdown = (key: string, headerText: string, zoneName: string, contextId?: string | null) => {
                        if (openDropdown !== key) return null;
                        return (
                          <div className="absolute left-0 top-full mt-1 z-50 bg-white border border-gray-200 rounded-lg shadow-lg py-1 min-w-[180px]" onClick={(e) => e.stopPropagation()}>
                            <div className="px-3 py-1.5 text-xs font-semibold text-gray-500 border-b border-gray-100">{headerText}</div>
                            {dropdownSub === null && (
                              <>
                                <button onClick={() => setDropdownSub('add')} className="w-full text-left px-3 py-1.5 text-sm text-gray-700 hover:bg-blue-50">Add ▸</button>
                                <button onClick={() => setDropdownSub('remove')} className="w-full text-left px-3 py-1.5 text-sm text-gray-700 hover:bg-red-50">Remove ▸</button>
                              </>
                            )}
                            {dropdownSub === 'add' && (
                              <>
                                <button onClick={() => { setDropdownSub(null); }} className="w-full text-left px-3 py-1 text-xs text-gray-400 hover:bg-gray-50">◂ Back</button>
                                {ACCESS_TYPES.map((at) => (
                                  <button key={at} onClick={() => handleBulkAdd(at, zoneName, contextId)} className="w-full text-left px-3 py-1.5 text-sm text-gray-700 hover:bg-blue-50">{at}</button>
                                ))}
                              </>
                            )}
                            {dropdownSub === 'remove' && (
                              <>
                                <button onClick={() => { setDropdownSub(null); }} className="w-full text-left px-3 py-1 text-xs text-gray-400 hover:bg-gray-50">◂ Back</button>
                                {ACCESS_TYPES.map((at) => (
                                  <button key={at} onClick={() => handleBulkRemove(at, zoneName, contextId)} className="w-full text-left px-3 py-1.5 text-sm text-red-600 hover:bg-red-50">{at}</button>
                                ))}
                              </>
                            )}
                          </div>
                        );
                      };

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
                                const zoneKey = `zone:${zg.zoneName}`;
                                const totalZoneRows = zoneRowCounts[zIdx];
                                let zoneRowRendered = false;

                                return zg.contexts.map((cg, cIdx) => {
                                  const ctxKey = `ctx:${zg.zoneName}:${cg.contextId || '__wildcard__'}`;
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
                                            <div className="relative inline-flex items-center gap-1">
                                              <span>{zg.zoneName}</span>
                                              <button
                                                onClick={(e) => { e.stopPropagation(); setOpenDropdown(openDropdown === zoneKey ? null : zoneKey); setDropdownSub(null); }}
                                                className="text-gray-400 hover:text-gray-600 text-xs ml-1"
                                                title="Zone actions"
                                              >⋯</button>
                                              {renderDropdown(zoneKey, `${zg.zoneName} zone`, zg.zoneName)}
                                            </div>
                                          </td>
                                        )}
                                        {showCtx && (
                                          <td rowSpan={totalCtxRows} className="px-4 py-2 align-top text-gray-600 border-r border-gray-100">
                                            <div className="relative inline-flex items-center gap-1 pl-2">
                                              <span>{cg.contextName}</span>
                                              <button
                                                onClick={(e) => { e.stopPropagation(); setOpenDropdown(openDropdown === ctxKey ? null : ctxKey); setDropdownSub(null); }}
                                                className="text-gray-400 hover:text-gray-600 text-xs ml-1"
                                                title="Context actions"
                                              >⋯</button>
                                              {renderDropdown(ctxKey, `${zg.zoneName} zone, ${cg.contextName} context`, zg.zoneName, cg.contextId)}
                                            </div>
                                          </td>
                                        )}
                                        <td className="px-4 py-2 text-gray-800">{row.serviceName}</td>
                                        {ACCESS_TYPES.map((at) => {
                                          const entry = row.accessMap.get(at);
                                          const isApproved = entry && entry.accessLevel === 'APPROVED';
                                          return (
                                            <td key={at} className="text-center px-2 py-2">
                                              <button
                                                onClick={() => handleToggleAcl(row.serviceName, row.zoneId, row.contextId, at, entry)}
                                                className={`w-7 h-7 rounded border text-xs font-bold transition-colors ${
                                                  isApproved
                                                    ? 'bg-green-50 border-green-300 text-green-600 hover:bg-red-50 hover:border-red-300 hover:text-red-500'
                                                    : 'bg-gray-50 border-gray-200 text-gray-300 hover:bg-blue-50 hover:border-blue-300 hover:text-blue-500'
                                                }`}
                                                title={isApproved ? `Remove ${at}` : `Add ${at}`}
                                              >
                                                {isApproved ? '✓' : ''}
                                              </button>
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

            {tab === 'tags' && (
              <EnvironmentTagsSection
                domainId={domainId!}
                solutionId={solutionId!}
                envId={envId!}
                authFetch={authFetch}
              />
            )}

            {tab === 'alerts' && (
              <AlertsTab
                domainId={domainId!}
                solutionId={solutionId!}
                envId={envId!}
                authFetch={authFetch}
              />
            )}

            {tab === 'providers' && (
              <ProviderRegistrationsSection
                domainId={domainId!}
                solutionId={solutionId!}
                envId={envId!}
                authFetch={authFetch}
              />
            )}
          </>
        )}

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
      </main>
    </div>
  );
}
