import { useEffect, useState } from 'react';

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

interface EnvironmentFormData {
  consumerName: string;
  defaultZoneId: string;
  applicationKey: string;
  defaultProviderUrl: string;
  appProductName: string;
  appVendorName: string;
  appProductVersion: string;
  appIconURI: string;
  adapterProductName: string;
  adapterVendorName: string;
  adapterProductVersion: string;
  adapterIconURI: string;
}

interface ZoneEntry {
  id: string;
  zoneId?: string;
}

// ── Props ──

export interface EnvironmentDetailViewProps {
  domainId: string;
  solutionId: string;
  envId: string;
  authFetch: (path: string, opts?: RequestInit) => Promise<Response | null>;
}

// ── Helper components ──

function FormInput({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (value: string) => void; placeholder?: string }) {
  return (
    <div>
      <label className="block text-gray-500 text-sm mb-1">{label}</label>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
      />
    </div>
  );
}

function ReadOnlyField({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-gray-500">{label}</dt>
      <dd className="font-medium text-gray-800">{value || '—'}</dd>
    </div>
  );
}

// ── Main component ──

export default function EnvironmentDetailView({ domainId, solutionId, envId, authFetch }: EnvironmentDetailViewProps) {
  const [env, setEnv] = useState<EnvironmentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
  const [showUrls, setShowUrls] = useState(false);
  const [rotatingSecret, setRotatingSecret] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [newSessionToken, setNewSessionToken] = useState<string | null>(null);
  const [zones, setZones] = useState<ZoneEntry[]>([]);
  const [formData, setFormData] = useState<EnvironmentFormData>({
    consumerName: '',
    defaultZoneId: '',
    applicationKey: '',
    defaultProviderUrl: '',
    appProductName: '',
    appVendorName: '',
    appProductVersion: '',
    appIconURI: '',
    adapterProductName: '',
    adapterVendorName: '',
    adapterProductVersion: '',
    adapterIconURI: '',
  });

  // ── Data fetching ──

  const fetchEnv = async () => {
    try {
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
    } catch {
      setError('Failed to fetch environment details');
    }
  };

  const fetchZones = async () => {
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/zones`);
      if (!res || !res.ok) return;
      const data = await res.json();
      const zoneList: ZoneEntry[] = data.zones?.zone
        ? data.zones.zone.map((z: any) => ({ id: z.id, zoneId: z.id }))
        : [];
      setZones(zoneList);
    } catch {
      // silently fail — zones are supplementary
    }
  };

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      await Promise.all([fetchEnv(), fetchZones()]);
      setLoading(false);
    };
    load();
  }, [domainId, solutionId, envId]);

  // ── Edit mode handlers ──

  const startEditing = () => {
    if (!env) return;
    setFormData({
      consumerName: env.consumerName,
      defaultZoneId: env.defaultZoneId || '',
      applicationKey: env.applicationInfo?.applicationKey || '',
      defaultProviderUrl: '',
      appProductName: env.applicationInfo?.applicationProduct?.productName || '',
      appVendorName: env.applicationInfo?.applicationProduct?.vendorName || '',
      appProductVersion: env.applicationInfo?.applicationProduct?.productVersion || '',
      appIconURI: env.applicationInfo?.applicationProduct?.iconURI || '',
      adapterProductName: env.applicationInfo?.adapterProduct?.productName || '',
      adapterVendorName: env.applicationInfo?.adapterProduct?.vendorName || '',
      adapterProductVersion: env.applicationInfo?.adapterProduct?.productVersion || '',
      adapterIconURI: env.applicationInfo?.adapterProduct?.iconURI || '',
    });
    setSaveSuccess(null);
    setEditing(true);
  };

  const cancelEditing = () => {
    setEditing(false);
    setSaveSuccess(null);
  };

  const handleSave = async () => {
    setSaving(true);
    setError(null);
    setSaveSuccess(null);

    const body: any = {
      consumerName: formData.consumerName || null,
      defaultZone: formData.defaultZoneId ? { id: formData.defaultZoneId } : null,
      applicationInfo: {
        applicationKey: formData.applicationKey || null,
        applicationProduct: {
          productName: formData.appProductName || null,
          vendorName: formData.appVendorName || null,
          productVersion: formData.appProductVersion || null,
          iconURI: formData.appIconURI || null,
        },
        adapterProduct: {
          productName: formData.adapterProductName || null,
          vendorName: formData.adapterVendorName || null,
          productVersion: formData.adapterProductVersion || null,
          iconURI: formData.adapterIconURI || null,
        },
      },
    };

    // Clean up null fields in nested objects
    const appProduct = body.applicationInfo.applicationProduct;
    if (!appProduct.productName && !appProduct.vendorName && !appProduct.productVersion && !appProduct.iconURI) {
      body.applicationInfo.applicationProduct = null;
    }
    const adapterProduct = body.applicationInfo.adapterProduct;
    if (!adapterProduct.productName && !adapterProduct.vendorName && !adapterProduct.productVersion && !adapterProduct.iconURI) {
      body.applicationInfo.adapterProduct = null;
    }
    if (!body.applicationInfo.applicationKey && !body.applicationInfo.applicationProduct && !body.applicationInfo.adapterProduct) {
      body.applicationInfo = null;
    }

    try {
      const res = await authFetch(`/${domainId}/${solutionId}/environments/${envId}`, {
        method: 'PUT',
        body: JSON.stringify(body),
      });

      if (!res) { setSaving(false); return; }
      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        setError(`Failed to update environment (${res.status})${errText ? ': ' + errText : ''}`);
        setSaving(false);
        return;
      }

      setSaveSuccess('Environment updated successfully.');
      setEditing(false);
      await fetchEnv();
    } catch {
      setError('Failed to update environment');
    }
    setSaving(false);
  };

  const handleRotateCredentials = async () => {
    if (!window.confirm('This will regenerate BOTH the session token and shared secret. The environment will need to re-authenticate with the new credentials. Continue?')) return;
    setRotatingSecret(true);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/environments/${envId}/rotate-credentials`, { method: 'POST', body: '{}' });
      setRotatingSecret(false);
      if (!res || !res.ok) { setError(`Failed to rotate credentials (${res?.status})`); return; }
      const data = await res.json();
      setNewSecret(data.sharedSecret);
      setNewSessionToken(data.sessionToken);
    } catch {
      setRotatingSecret(false);
      setError('Failed to rotate credentials');
    }
  };

  const updateField = (field: keyof EnvironmentFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text).catch(() => {});
  };

  // ── Render ──

  if (loading) return <p className="text-gray-500 text-sm">Loading...</p>;
  if (!env) return <p className="text-gray-500 text-sm">Environment not found.</p>;

  // Shared Security card content (same in read and edit mode)
  const securityCard = (
    <div className="bg-white rounded-lg border border-gray-200 p-6">
      <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Security</h2>
      {(newSecret || newSessionToken) && (
        <div className="mb-4 p-3 bg-amber-50 border border-amber-200 rounded text-xs text-amber-800">
          <strong>Credentials rotated.</strong> Both the session token and shared secret have been regenerated. Save them now — they won't be shown again.
        </div>
      )}
      <dl className="space-y-3 text-sm">
        <div>
          <dt className="text-gray-500">Shared Secret</dt>
          <dd className={`font-medium text-gray-800 flex items-center gap-2 flex-wrap ${newSecret ? 'bg-yellow-100 border border-yellow-300 rounded px-2 py-1' : ''}`}>
            <span className="font-mono text-xs">{newSecret || '••••••••'}</span>
            {newSecret && <button onClick={() => copyToClipboard(newSecret)} className="text-blue-600 hover:text-blue-800 text-xs">Copy</button>}
            {newSecret && <span className="text-xs text-amber-600">New — save this now</span>}
            {!newSecret && <span className="text-xs text-gray-400 italic">Not available after registration</span>}
            <button
              onClick={handleRotateCredentials}
              disabled={rotatingSecret}
              className="text-xs text-blue-600 border border-blue-300 rounded px-2 py-0.5 hover:bg-blue-50 disabled:opacity-50"
            >
              {rotatingSecret ? 'Rotating...' : 'Rotate Credentials'}
            </button>
          </dd>
        </div>
        <div>
          <dt className="text-gray-500">Environment ID</dt>
          <dd className="font-medium text-gray-800 font-mono text-xs flex items-center gap-2">
            <span>{env.id}</span>
            <button onClick={() => copyToClipboard(env.id)} className="text-blue-600 hover:text-blue-800 text-xs">Copy</button>
          </dd>
        </div>
        <div>
          <dt className="text-gray-500">Session Token</dt>
          <dd className={`font-medium text-gray-800 flex items-center gap-2 ${newSessionToken ? 'bg-yellow-100 border border-yellow-300 rounded px-2 py-1' : ''}`}>
            <span className="font-mono text-xs truncate max-w-[200px]">{newSessionToken || env.sessionToken || '—'}</span>
            {(newSessionToken || env.sessionToken) && <button onClick={() => copyToClipboard(newSessionToken || env.sessionToken!)} className="text-blue-600 hover:text-blue-800 text-xs">Copy</button>}
            {newSessionToken && <span className="text-xs text-amber-600">New — save this now</span>}
          </dd>
        </div>
        <ReadOnlyField label="Fingerprint" value={env.fingerprint || ''} />
      </dl>
    </div>
  );

  // Infrastructure Service URLs collapsible section
  const infrastructureUrlsSection = env.infrastructureServices && env.infrastructureServices.length > 0 && (
    <div className="mt-6 bg-white rounded-lg border border-gray-200">
      <button
        onClick={() => setShowUrls(!showUrls)}
        className="w-full flex items-center justify-between px-6 py-4 text-sm font-semibold text-gray-700 uppercase tracking-wide hover:bg-gray-50"
      >
        <span>Infrastructure Service URLs</span>
        <span className="text-gray-400">{showUrls ? '▲' : '▼'}</span>
      </button>
      {showUrls && (
        <div className="border-t border-gray-200 px-6 py-4">
          <dl className="space-y-3 text-sm">
            {env.infrastructureServices!.filter((svc) => svc.name.toLowerCase() !== 'provisionrequests').map((svc) => (
              <div key={svc.name} className="flex flex-col">
                <dt className="text-gray-500 uppercase text-xs font-medium">{svc.name}</dt>
                <dd className="font-mono text-xs text-gray-800 mt-0.5 break-all">{svc.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  );

  return (
    <div>
      {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}
      {saveSuccess && <div className="bg-green-50 border border-green-200 text-green-700 text-sm rounded px-4 py-3 mb-4">{saveSuccess}</div>}

      {/* ── Read Mode ── */}
      {!editing && (
        <>
          <div className="flex justify-end mb-4">
            <button onClick={startEditing} className="px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white hover:bg-blue-700">Edit</button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">General</h2>
              <dl className="space-y-3 text-sm">
                <ReadOnlyField label="Name" value={env.consumerName} />
                <ReadOnlyField label="Authentication Method" value={env.authenticationMethod || 'SIF_HMACSHA256'} />
                <ReadOnlyField label="Default Zone" value={env.defaultZoneId || ''} />
                <ReadOnlyField label="Application Key" value={env.applicationInfo?.applicationKey || ''} />
              </dl>
            </div>
            {securityCard}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Application</h2>
              <dl className="space-y-3 text-sm">
                <ReadOnlyField label="Product Name" value={env.applicationInfo?.applicationProduct?.productName || ''} />
                <ReadOnlyField label="Vendor Name" value={env.applicationInfo?.applicationProduct?.vendorName || ''} />
                <ReadOnlyField label="Product Version" value={env.applicationInfo?.applicationProduct?.productVersion || ''} />
                <ReadOnlyField label="Product Icon" value={env.applicationInfo?.applicationProduct?.iconURI || ''} />
              </dl>
            </div>
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Adapter</h2>
              <dl className="space-y-3 text-sm">
                <ReadOnlyField label="Product Name" value={env.applicationInfo?.adapterProduct?.productName || ''} />
                <ReadOnlyField label="Vendor Name" value={env.applicationInfo?.adapterProduct?.vendorName || ''} />
                <ReadOnlyField label="Product Version" value={env.applicationInfo?.adapterProduct?.productVersion || ''} />
                <ReadOnlyField label="Product Icon" value={env.applicationInfo?.adapterProduct?.iconURI || ''} />
              </dl>
            </div>
          </div>
          {infrastructureUrlsSection}
        </>
      )}

      {/* ── Edit Mode ── */}
      {editing && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {/* General section */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">General</h2>
              <div className="space-y-3 text-sm">
                <FormInput label="Name" value={formData.consumerName} onChange={(v) => updateField('consumerName', v)} placeholder="Consumer name" />
                <div>
                  <label className="block text-gray-500 text-sm mb-1">Authentication Method</label>
                  <select disabled className="w-full border border-gray-200 rounded px-3 py-1.5 text-sm text-gray-500 bg-gray-50 cursor-not-allowed">
                    <option>SIF_HMACSHA256</option>
                  </select>
                </div>
                <div>
                  <label className="block text-gray-500 text-sm mb-1">Default Zone</label>
                  <select
                    value={formData.defaultZoneId}
                    onChange={(e) => updateField('defaultZoneId', e.target.value)}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                  >
                    <option value="">— Select zone —</option>
                    {zones.map((z) => (
                      <option key={z.id} value={z.zoneId || z.id}>{z.zoneId || z.id}</option>
                    ))}
                  </select>
                </div>
                <FormInput label="Application Key" value={formData.applicationKey} onChange={(v) => updateField('applicationKey', v)} placeholder="Application key" />
                <FormInput label="Default Provider URL" value={formData.defaultProviderUrl} onChange={(v) => updateField('defaultProviderUrl', v)} placeholder="https://..." />
              </div>
            </div>

            {/* Security section (read-only + Change Secret) */}
            {securityCard}

            {/* Application section */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Application</h2>
              <div className="space-y-3 text-sm">
                <FormInput label="Product Name" value={formData.appProductName} onChange={(v) => updateField('appProductName', v)} placeholder="Product name" />
                <FormInput label="Vendor Name" value={formData.appVendorName} onChange={(v) => updateField('appVendorName', v)} placeholder="Vendor name" />
                <FormInput label="Product Version" value={formData.appProductVersion} onChange={(v) => updateField('appProductVersion', v)} placeholder="1.0.0" />
                <FormInput label="Product Icon" value={formData.appIconURI} onChange={(v) => updateField('appIconURI', v)} placeholder="https://..." />
              </div>
            </div>

            {/* Adapter section */}
            <div className="bg-white rounded-lg border border-gray-200 p-6">
              <h2 className="text-sm font-semibold text-gray-700 mb-4 uppercase tracking-wide">Adapter</h2>
              <div className="space-y-3 text-sm">
                <FormInput label="Product Name" value={formData.adapterProductName} onChange={(v) => updateField('adapterProductName', v)} placeholder="Product name" />
                <FormInput label="Vendor Name" value={formData.adapterVendorName} onChange={(v) => updateField('adapterVendorName', v)} placeholder="Vendor name" />
                <FormInput label="Product Version" value={formData.adapterProductVersion} onChange={(v) => updateField('adapterProductVersion', v)} placeholder="1.0.0" />
                <FormInput label="Product Icon" value={formData.adapterIconURI} onChange={(v) => updateField('adapterIconURI', v)} placeholder="https://..." />
              </div>
            </div>
          </div>

          {/* Save / Cancel buttons */}
          <div className="flex gap-3">
            <button
              onClick={handleSave}
              disabled={saving}
              className="px-4 py-2 text-sm font-medium rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {saving ? 'Saving...' : 'Save'}
            </button>
            <button
              onClick={cancelEditing}
              disabled={saving}
              className="px-4 py-2 text-sm font-medium rounded border border-gray-300 text-gray-700 hover:bg-gray-100 disabled:opacity-50"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
