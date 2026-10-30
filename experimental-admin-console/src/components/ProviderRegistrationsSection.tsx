import { useEffect, useState } from 'react';

// SIF 3.7 wire shapes. The spec nests singular members inside plural wrappers
// (operations.operation, supportedRequestTypes.supportedRequestType), and types the
// endpoint as an object rather than a bare string. These mirror the broker exactly —
// the broker runs with FAIL_ON_UNKNOWN_PROPERTIES enabled, so a flat field is a 400,
// not something it quietly ignores.

interface Operations {
  operation: string[];
}

interface SupportedRequestType {
  requestType: 'IMMEDIATE' | 'DELAYED';
  operations: Operations;
}

interface SupportedRequestTypes {
  supportedRequestType: SupportedRequestType[];
}

/** The `protocol` definition — an object, so it can carry protocol settings a bare string cannot. */
interface Protocol {
  location: string;
}

interface ProviderRegistration {
  id: string;
  environmentId: string;
  serviceName: string;
  zoneId: string;
  contextId: string | null;
  /** Note the capital P — the schema spells it endPoint. Optional: DELAYED-only providers have none. */
  endPoint: Protocol | null;
  serviceType: string;
  /** The provider's name as the zone administrator refers to it. Not `consumerName`, which is not part of the provider schema. */
  providerName: string;
  supportedRequestTypes: SupportedRequestTypes;
  registeredAt: string;
}

interface AvailableProviderOption {
  contextServiceUuid: string;
  zoneId: string;
  contextId: string;
  serviceName: string;
  serviceType: string;
}

interface ProviderRegistrationsSectionProps {
  domainId: string;
  solutionId: string;
  envId: string;
  authFetch: (path: string, init?: RequestInit, extraHeaders?: Record<string, string>) => Promise<Response | null>;
}

const OPERATIONS = ['CREATE', 'QUERY', 'UPDATE', 'DELETE'];

export default function ProviderRegistrationsSection({ domainId, solutionId, envId, authFetch }: ProviderRegistrationsSectionProps) {
  const [registrations, setRegistrations] = useState<ProviderRegistration[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  // Edit state
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editEndpoint, setEditEndpoint] = useState('');
  const [editImmediateOps, setEditImmediateOps] = useState<string[]>([]);
  const [editDelayedOps, setEditDelayedOps] = useState<string[]>([]);
  const [editSubmitting, setEditSubmitting] = useState(false);

  // Available options state
  const [availableOptions, setAvailableOptions] = useState<AvailableProviderOption[]>([]);
  const [optionsLoading, setOptionsLoading] = useState(false);
  const [optionsError, setOptionsError] = useState<string | null>(null);

  // Form state - cascading dropdowns
  const [selectedZone, setSelectedZone] = useState('');
  const [selectedContext, setSelectedContext] = useState('');
  const [selectedService, setSelectedService] = useState('');
  const [selectedServiceType, setSelectedServiceType] = useState('');
  const [endpoint, setEndpoint] = useState('');
  const [immediateOps, setImmediateOps] = useState<string[]>(['QUERY']);
  const [delayedOps, setDelayedOps] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const fetchRegistrations = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/providers?environmentId=${envId}`,
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res) return;
      if (!res.ok) {
        setError(`Failed to fetch provider registrations (${res.status})`);
        setLoading(false);
        return;
      }
      const data = await res.json();
      // SIF collections are doubly nested: {"providers": {"provider": [...]}}. This used to be
      // `Array.isArray(data) ? data : []`, which turned the shape change into a silently empty
      // list — the worst possible failure here, since an empty list is indistinguishable from
      // "this environment provides nothing". Surface a mismatch instead of hiding it.
      const provider = data?.providers?.provider;
      if (!Array.isArray(provider)) {
        setError('Unexpected response shape from GET /providers — expected {"providers":{"provider":[...]}}');
        setRegistrations([]);
        setLoading(false);
        return;
      }
      setRegistrations(provider);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch');
    } finally {
      setLoading(false);
    }
  };

  const fetchAvailableOptions = async () => {
    setOptionsLoading(true);
    setOptionsError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/providers/available-options?environmentId=${envId}`,
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res) return;
      if (!res.ok) {
        setOptionsError(`Failed to fetch available options (${res.status})`);
        setOptionsLoading(false);
        return;
      }
      const data = await res.json();
      setAvailableOptions(Array.isArray(data) ? data : []);
    } catch (err) {
      setOptionsError(err instanceof Error ? err.message : 'Failed to fetch options');
    } finally {
      setOptionsLoading(false);
    }
  };

  useEffect(() => {
    fetchRegistrations();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch available options when form is opened
  useEffect(() => {
    if (showForm) {
      fetchAvailableOptions();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showForm]);

  // Derive distinct zones from available options
  const availableZones = [...new Set(availableOptions.map(opt => opt.zoneId))].sort();

  // Derive contexts filtered by selected zone
  const availableContexts = selectedZone
    ? [...new Set(availableOptions.filter(opt => opt.zoneId === selectedZone).map(opt => opt.contextId))].sort()
    : [];

  // Derive services filtered by selected zone + context
  const availableServices = selectedZone && selectedContext
    ? [...new Set(availableOptions.filter(opt => opt.zoneId === selectedZone && opt.contextId === selectedContext).map(opt => opt.serviceName))].sort()
    : [];

  // Derive service types filtered by selected zone + context + service
  const availableServiceTypes = selectedZone && selectedContext && selectedService
    ? [...new Set(availableOptions.filter(opt => opt.zoneId === selectedZone && opt.contextId === selectedContext && opt.serviceName === selectedService).map(opt => opt.serviceType))].sort()
    : [];

  const toggleOp = (list: string[], setList: (v: string[]) => void, op: string) => {
    setList(list.includes(op) ? list.filter(o => o !== op) : [...list, op]);
  };

  const resetForm = () => {
    setSelectedZone('');
    setSelectedContext('');
    setSelectedService('');
    setSelectedServiceType('');
    setEndpoint('');
    setImmediateOps(['QUERY']);
    setDelayedOps([]);
  };

  const handleZoneChange = (zone: string) => {
    setSelectedZone(zone);
    setSelectedContext('');
    setSelectedService('');
    setSelectedServiceType('');
  };

  const handleContextChange = (context: string) => {
    setSelectedContext(context);
    setSelectedService('');
    setSelectedServiceType('');
  };

  const handleServiceChange = (service: string) => {
    setSelectedService(service);
    setSelectedServiceType('');
  };

  const handleSubmit = async () => {
    if (!selectedZone || !selectedContext || !selectedService || !selectedServiceType) {
      setError('All cascade selections are required.');
      return;
    }
    if (immediateOps.length === 0 && delayedOps.length === 0) {
      setError('Select at least one operation for IMMEDIATE or DELAYED.');
      return;
    }
    if (immediateOps.length > 0 && !endpoint.trim()) {
      setError('Endpoint URL is required for providers with IMMEDIATE operations.');
      return;
    }

    // Resolve contextServiceUuid from selected option
    const matchingOption = availableOptions.find(
      opt => opt.zoneId === selectedZone && opt.contextId === selectedContext && opt.serviceName === selectedService && opt.serviceType === selectedServiceType
    );
    if (!matchingOption) {
      setError('Could not resolve selected option. Please try again.');
      return;
    }

    setSubmitting(true);
    setError(null);

    const supportedRequestType: SupportedRequestType[] = [];
    if (immediateOps.length > 0) {
      supportedRequestType.push({ requestType: 'IMMEDIATE', operations: { operation: immediateOps } });
    }
    if (delayedOps.length > 0) {
      supportedRequestType.push({ requestType: 'DELAYED', operations: { operation: delayedOps } });
    }

    // Wrapped under `provider` per the SIF 3.7 envelope convention. `querySupport` is deliberately
    // absent: it is a response-only field the broker fills in, not something a client asserts.
    const body = {
      provider: {
        environmentId: envId,
        contextServiceUuid: matchingOption.contextServiceUuid,
        endPoint: endpoint.trim() ? { location: endpoint.trim() } : null,
        serviceType: selectedServiceType,
        supportedRequestTypes: { supportedRequestType },
      },
    };

    try {
      const res = await authFetch(`/${domainId}/${solutionId}/providers/provider`, {
        method: 'POST',
        body: JSON.stringify(body),
      }, { 'X-Run-As-Environment': 'broker-services' });
      if (!res) return;
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Failed (${res.status}): ${text}`);
      }
      const created = await res.json();
      const provider = created.provider;
      setRegistrations(prev => [...prev, provider]);
      resetForm();
      setShowForm(false);
      // Re-fetch available options since one was just used
      fetchAvailableOptions();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Create failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (reg: ProviderRegistration) => {
    if (!window.confirm(`Delete provider registration for ${reg.serviceName}?`)) return;
    setError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/providers/${reg.id}`,
        { method: 'DELETE' },
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res || !res.ok) {
        setError(`Failed to delete (${res?.status})`);
        return;
      }
      setRegistrations(prev => prev.filter(r => r.id !== reg.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    }
  };

  const startEditing = (reg: ProviderRegistration) => {
    setEditingId(reg.id);
    setEditEndpoint(reg.endPoint?.location ?? '');
    const entries = reg.supportedRequestTypes.supportedRequestType;
    const immSrt = entries.find(s => s.requestType === 'IMMEDIATE');
    const delSrt = entries.find(s => s.requestType === 'DELAYED');
    setEditImmediateOps(immSrt ? immSrt.operations.operation : []);
    setEditDelayedOps(delSrt ? delSrt.operations.operation : []);
  };

  const cancelEditing = () => {
    setEditingId(null);
    setEditEndpoint('');
    setEditImmediateOps([]);
    setEditDelayedOps([]);
  };

  const handleEditSave = async () => {
    if (editImmediateOps.length === 0 && editDelayedOps.length === 0) {
      setError('Select at least one operation for IMMEDIATE or DELAYED.');
      return;
    }
    if (editImmediateOps.length > 0 && !editEndpoint.trim()) {
      setError('Endpoint URL is required for providers with IMMEDIATE operations.');
      return;
    }

    setEditSubmitting(true);
    setError(null);

    const supportedRequestType: SupportedRequestType[] = [];
    if (editImmediateOps.length > 0) {
      supportedRequestType.push({ requestType: 'IMMEDIATE', operations: { operation: editImmediateOps } });
    }
    if (editDelayedOps.length > 0) {
      supportedRequestType.push({ requestType: 'DELAYED', operations: { operation: editDelayedOps } });
    }

    // Same envelope and nesting as create. These two used to disagree — update took a flat
    // `endpoint` string and a bare array — which meant one resource needed two request shapes.
    const body = {
      provider: {
        endPoint: editEndpoint.trim() ? { location: editEndpoint.trim() } : null,
        supportedRequestTypes: { supportedRequestType },
      },
    };

    try {
      const res = await authFetch(`/${domainId}/${solutionId}/providers/${editingId}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }, { 'X-Run-As-Environment': 'broker-services' });
      if (!res) return;
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Failed (${res.status}): ${text}`);
      }
      const updated = await res.json();
      // PUT returns the `provider` envelope, same as POST.
      setRegistrations(prev => prev.map(r => r.id === editingId ? updated.provider : r));
      cancelEditing();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Update failed');
    } finally {
      setEditSubmitting(false);
    }
  };

  const toggleEditOp = (list: string[], setList: (v: string[]) => void, op: string) => {
    setList(list.includes(op) ? list.filter(o => o !== op) : [...list, op]);
  };

  const isSubmitDisabled = !selectedZone || !selectedContext || !selectedService || !selectedServiceType || (immediateOps.length > 0 && !endpoint.trim()) || (immediateOps.length === 0 && delayedOps.length === 0) || submitting;

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-gray-700">Provider Registrations</h2>
        <div className="flex gap-2">
          <button onClick={fetchRegistrations} className="text-gray-400 hover:text-gray-600 text-sm" title="Refresh">↻</button>
          <button onClick={() => setShowForm(!showForm)} className="bg-blue-600 text-white text-sm px-3 py-1.5 rounded hover:bg-blue-700">
            {showForm ? 'Cancel' : '+ Register Provider'}
          </button>
        </div>
      </div>

      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

      {showForm && (
        <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 mb-4 space-y-3">
          {optionsLoading ? (
            <p className="text-gray-500 text-sm">Loading available options...</p>
          ) : optionsError ? (
            <div className="text-red-600 text-sm">
              <p>{optionsError}</p>
              <button onClick={fetchAvailableOptions} className="text-blue-600 hover:text-blue-800 text-xs mt-1">Retry</button>
            </div>
          ) : availableOptions.length === 0 ? (
            <p className="text-amber-600 text-sm text-center py-4">No provider registrations are possible for this environment. All available services are already registered or no PROVIDE access has been granted.</p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Zone *</label>
                  <select value={selectedZone} onChange={e => handleZoneChange(e.target.value)} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="">Select zone...</option>
                    {availableZones.map(z => <option key={z} value={z}>{z}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Context *</label>
                  <select value={selectedContext} onChange={e => handleContextChange(e.target.value)} disabled={!selectedZone} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-400">
                    <option value="">Select context...</option>
                    {availableContexts.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Service *</label>
                  <select value={selectedService} onChange={e => handleServiceChange(e.target.value)} disabled={!selectedContext} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-400">
                    <option value="">Select service...</option>
                    {availableServices.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Service Type *</label>
                  <select value={selectedServiceType} onChange={e => setSelectedServiceType(e.target.value)} disabled={!selectedService} className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:text-gray-400">
                    <option value="">Select service type...</option>
                    {availableServiceTypes.map(st => <option key={st} value={st}>{st}</option>)}
                  </select>
                </div>
                <div className="col-span-2">
                  <label className="block text-xs text-gray-500 mb-1">Endpoint URL {immediateOps.length > 0 ? '*' : '(optional for DELAYED-only)'}</label>
                  <input type="text" value={endpoint} onChange={e => setEndpoint(e.target.value)} placeholder="https://api.example.com/students" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 mt-2">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">IMMEDIATE Operations</label>
                  <div className="flex gap-2 flex-wrap">
                    {OPERATIONS.map(op => (
                      <label key={op} className="flex items-center gap-1 text-xs text-gray-700">
                        <input type="checkbox" checked={immediateOps.includes(op)} onChange={() => toggleOp(immediateOps, setImmediateOps, op)} className="rounded" />
                        {op}
                      </label>
                    ))}
                  </div>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">DELAYED Operations</label>
                  <div className="flex gap-2 flex-wrap">
                    {OPERATIONS.map(op => (
                      <label key={op} className="flex items-center gap-1 text-xs text-gray-700">
                        <input type="checkbox" checked={delayedOps.includes(op)} onChange={() => toggleOp(delayedOps, setDelayedOps, op)} className="rounded" />
                        {op}
                      </label>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex justify-end pt-2">
                <button onClick={handleSubmit} disabled={isSubmitDisabled} className="bg-blue-600 text-white text-sm px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50">
                  {submitting ? 'Registering...' : 'Register Provider'}
                </button>
              </div>
            </>
          )}
        </div>
      )}

      {loading ? (
        <p className="text-gray-500 text-sm">Loading provider registrations...</p>
      ) : registrations.length === 0 ? (
        <p className="text-gray-400 text-sm text-center py-6">No provider registrations for this environment.</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="text-left px-4 py-3 font-medium text-gray-600">Service</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Endpoint</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Zone / Context</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Patterns</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600"></th>
              </tr>
            </thead>
            <tbody>
              {registrations.map(reg => (
                editingId === reg.id ? (
                  <tr key={reg.id} className="border-b border-gray-100 bg-blue-50">
                    <td colSpan={5} className="px-4 py-3">
                      <div className="space-y-3">
                        <div className="flex items-center gap-2">
                          <span className="font-medium text-gray-800">{reg.serviceName}</span>
                          <span className="text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">{reg.serviceType}</span>
                          <span className="text-xs text-gray-500">— Editing</span>
                        </div>
                        <div>
                          <label className="block text-xs text-gray-500 mb-1">Endpoint URL {editImmediateOps.length > 0 ? '*' : '(optional for DELAYED-only)'}</label>
                          <input type="text" value={editEndpoint} onChange={e => setEditEndpoint(e.target.value)} placeholder="https://api.example.com/students" className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
                        </div>
                        <div className="grid grid-cols-2 gap-3">
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">IMMEDIATE Operations</label>
                            <div className="flex gap-2 flex-wrap">
                              {OPERATIONS.map(op => (
                                <label key={op} className="flex items-center gap-1 text-xs text-gray-700">
                                  <input type="checkbox" checked={editImmediateOps.includes(op)} onChange={() => toggleEditOp(editImmediateOps, setEditImmediateOps, op)} className="rounded" />
                                  {op}
                                </label>
                              ))}
                            </div>
                          </div>
                          <div>
                            <label className="block text-xs text-gray-500 mb-1">DELAYED Operations</label>
                            <div className="flex gap-2 flex-wrap">
                              {OPERATIONS.map(op => (
                                <label key={op} className="flex items-center gap-1 text-xs text-gray-700">
                                  <input type="checkbox" checked={editDelayedOps.includes(op)} onChange={() => toggleEditOp(editDelayedOps, setEditDelayedOps, op)} className="rounded" />
                                  {op}
                                </label>
                              ))}
                            </div>
                          </div>
                        </div>
                        <div className="flex justify-end gap-2 pt-1">
                          <button onClick={cancelEditing} className="text-gray-600 hover:text-gray-800 text-sm px-3 py-1.5 rounded border border-gray-300">Cancel</button>
                          <button onClick={handleEditSave} disabled={editSubmitting || (editImmediateOps.length === 0 && editDelayedOps.length === 0) || (editImmediateOps.length > 0 && !editEndpoint.trim())} className="bg-blue-600 text-white text-sm px-3 py-1.5 rounded hover:bg-blue-700 disabled:opacity-50">
                            {editSubmitting ? 'Saving...' : 'Save'}
                          </button>
                        </div>
                      </div>
                    </td>
                  </tr>
                ) : (
                <tr key={reg.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-3">
                    <span className="font-medium text-gray-800">{reg.serviceName}</span>
                    <span className="ml-2 text-xs bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded">{reg.serviceType}</span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs text-gray-600 break-all">{reg.endPoint?.location ?? ""}</span>
                  </td>
                  <td className="px-4 py-3 text-gray-600">
                    {reg.zoneId}{reg.contextId ? ` / ${reg.contextId}` : ''}
                  </td>
                  <td className="px-4 py-3">
                    {reg.supportedRequestTypes.supportedRequestType.map(srt => (
                      <div key={srt.requestType} className="text-xs">
                        <span className={`inline-block px-1.5 py-0.5 rounded mr-1 ${srt.requestType === 'IMMEDIATE' ? 'bg-green-100 text-green-700' : 'bg-yellow-100 text-yellow-700'}`}>
                          {srt.requestType}
                        </span>
                        <span className="text-gray-500">{srt.operations.operation.join(', ')}</span>
                      </div>
                    ))}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => startEditing(reg)} className="text-blue-500 hover:text-blue-700 text-xs font-medium mr-2">Edit</button>
                    <button onClick={() => handleDelete(reg)} className="text-red-500 hover:text-red-700 text-xs font-medium">Delete</button>
                  </td>
                </tr>
                )
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
