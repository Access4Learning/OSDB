// NamedQueriesTab - Named Query Template management for SolutionOverviewPage
import { useEffect, useState, useCallback } from 'react';

// --- Interfaces ---

export interface XQueryTemplate {
  id: string; // queryToken (the "id" field in SIF response)
  type: 'SINGULAR' | 'FORMULA' | 'EXTENDED';
  status: 'PENDING' | 'APPROVED' | 'DISALLOWED';
  description?: string;
}

export interface XQueryTemplateDetail extends XQueryTemplate {
  qualifier?: string;
  script?: string;
  /** D6: the array is nested inside the plural wrapper, like operations.operation. */
  parameters?: XQueryParameters;
  returnType?: string;
}

/** The `parameters` wrapper from the SIF schema — plural outside, singular array inside. */
export interface XQueryParameters {
  parameter: XQueryParameter[];
}

/**
 * A parameter as it appears on the wire.
 *
 * Note `default`, not `defaultValue`: that is the schema's property name. The broker's Java
 * record calls its component `defaultValue` only because `default` is a reserved word there,
 * and maps it with @JsonProperty("default"). Sending `defaultValue` is a 400.
 */
export interface XQueryParameter {
  name: string;
  type: string;
  default?: string;
  description?: string;
  required?: boolean;
}

export interface CreateFormState {
  queryToken: string;
  type: 'SINGULAR' | 'FORMULA' | 'EXTENDED';
  status: 'PENDING' | 'APPROVED' | 'DISALLOWED';
  qualifier: string;
  description: string;
  script: string;
  returnType: string;
}

export interface ParameterFormRow {
  name: string;
  type: string;
  defaultValue: string;
  description: string;
  required: boolean;
}

export interface NamedQueriesTabProps {
  domainId: string;
  solutionId: string;
  /**
   * A query token to open in edit mode as soon as the list loads. Set when arriving from the
   * Services tab, where an XQUERYTEMPLATE service links to the template behind it.
   */
  openForEdit?: string | null;
  /** Called once the deep-link has been consumed, so returning to the tab does not reopen it. */
  onOpenForEditHandled?: () => void;
  authFetch: (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => Promise<Response | null>;
}

// --- Pure helper functions ---

export function truncateDescription(text: string | undefined, maxLength: number = 80): string {
  if (!text) return '—';
  if (text.length <= maxLength) return text;
  return text.slice(0, maxLength) + '...';
}

export function getStatusBadgeClass(status: string): string {
  switch (status) {
    case 'APPROVED': return 'bg-green-100 text-green-700';
    case 'PENDING': return 'bg-yellow-100 text-yellow-700';
    case 'DISALLOWED': return 'bg-red-100 text-red-700';
    default: return 'bg-gray-100 text-gray-700';
  }
}

export function getTypeBadgeClass(): string {
  return 'bg-blue-100 text-blue-700';
}

/**
 * Turns the form rows into wire parameters.
 *
 * Absent optionals are **omitted, not sent as null**: the SIF schemas type their optional
 * properties without `nullable`, so an explicit null is nonconformant. `undefined` is dropped
 * by JSON.stringify, which is exactly what we want; `null` would be serialised.
 */
export function filterValidParameters(params: ParameterFormRow[]): XQueryParameter[] {
  return params
    .filter(p => p.name.trim() !== '')
    .map(p => ({
      name: p.name.trim(),
      type: p.type.trim(),
      default: p.defaultValue.trim() || undefined,
      description: p.description.trim() || undefined,
      required: p.required,
    }));
}

export function isSubmitDisabled(queryToken: string): boolean {
  return queryToken.trim() === '';
}

// --- Initial form state ---

const initialFormState: CreateFormState = {
  queryToken: '',
  type: 'SINGULAR',
  status: 'PENDING',
  qualifier: '',
  description: '',
  script: '',
  returnType: '',
};

// --- Component ---

export default function NamedQueriesTab({ domainId, solutionId, authFetch, openForEdit, onOpenForEditHandled }: NamedQueriesTabProps) {
  // List state
  const [templates, setTemplates] = useState<XQueryTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Detail state (Task 1.3)
  const [selectedTemplate, setSelectedTemplate] = useState<XQueryTemplateDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Create form state (Task 1.4)
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [formState, setFormState] = useState<CreateFormState>(initialFormState);
  const [parameters, setParameters] = useState<ParameterFormRow[]>([]);
  const [submitting, setSubmitting] = useState(false);

  // Edit state. editToken is the token currently being edited, or null when not editing.
  const [editToken, setEditToken] = useState<string | null>(null);
  const [editForm, setEditForm] = useState<CreateFormState>(initialFormState);
  const [editParameters, setEditParameters] = useState<ParameterFormRow[]>([]);
  const [editSubmitting, setEditSubmitting] = useState(false);

  const handleDelete = useCallback(async (e: React.MouseEvent, template: XQueryTemplate) => {
    e.stopPropagation();
    const confirmed = window.confirm(`Delete named query template "${template.id}"?`);
    if (!confirmed) return;
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/xquerys/${template.id}`,
        { method: 'DELETE' },
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res) return;
      if (res.status === 204) {
        setTemplates(prev => prev.filter(t => t.id !== template.id));
        if (selectedTemplate && selectedTemplate.id === template.id) {
          setSelectedTemplate(null);
        }
      } else {
        setError(`Failed to delete template (${res.status})`);
      }
    } catch {
      setError('Network error');
    }
  }, [domainId, solutionId, authFetch, selectedTemplate]);

  /**
   * Sends a partial update. Only the supplied keys are changed server-side, so callers pass the
   * minimum -- Approve sends just the status.
   */
  const patchTemplate = useCallback(async (token: string, changes: Record<string, unknown>) => {
    const res = await authFetch(
      `/${domainId}/${solutionId}/xquerys/${token}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ xquery: changes }),
      },
      { 'X-Run-As-Environment': 'broker-services' }
    );
    if (!res) return null;
    if (!res.ok) {
      const text = await res.text();
      setError(text || `Failed to update template (${res.status})`);
      return null;
    }
    const data = await res.json();
    return data?.xquery ?? null;
  }, [domainId, solutionId, authFetch]);

  const handleApprove = useCallback(async (token: string) => {
    const updated = await patchTemplate(token, { status: 'APPROVED' });
    if (!updated) return;
    setTemplates(prev => prev.map(x => (x.id === token ? { ...x, status: updated.status } : x)));
    setSelectedTemplate(prev => (prev && prev.id === token ? { ...prev, ...updated } : prev));
  }, [patchTemplate]);

  const beginEdit = useCallback((detail: XQueryTemplateDetail) => {
    setEditToken(detail.id);
    setEditForm({
      queryToken: detail.id,
      type: detail.type,
      status: detail.status,
      qualifier: detail.qualifier ?? '',
      description: detail.description ?? '',
      script: detail.script ?? '',
      returnType: detail.returnType ?? '',
    });
    setEditParameters(
      (detail.parameters?.parameter ?? []).map(prm => ({
        name: prm.name ?? '',
        type: prm.type ?? '',
        defaultValue: prm.default ?? '',
        description: prm.description ?? '',
        required: prm.required ?? false,
      }))
    );
  }, []);

  const saveEdit = useCallback(async () => {
    if (!editToken) return;
    setEditSubmitting(true);
    setError(null);
    // The token is not updatable -- context services, provider registrations and consumer
    // requests are all coupled to it by name, so renaming would strand them.
    const updated = await patchTemplate(editToken, {
      type: editForm.type,
      status: editForm.status,
      qualifier: editForm.qualifier.trim(),
      description: editForm.description.trim(),
      script: editForm.script.trim(),
      parameters: { parameter: filterValidParameters(editParameters) },
      returnType: editForm.returnType.trim(),
    });
    setEditSubmitting(false);
    if (!updated) return;
    setTemplates(prev => prev.map(x => (x.id === editToken ? { ...x, ...updated } : x)));
    setSelectedTemplate(prev => (prev && prev.id === editToken ? { ...prev, ...updated } : prev));
    setEditToken(null);
  }, [editToken, editForm, editParameters, patchTemplate]);

  const handleRowClick = useCallback(async (template: XQueryTemplate) => {
    // Toggle close if same template is already selected
    if (selectedTemplate && selectedTemplate.id === template.id) {
      setSelectedTemplate(null);
      return;
    }
    // Fetch detail for the clicked template
    setDetailLoading(true);
    setSelectedTemplate(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/xquerys/${template.id}`,
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res) { setDetailLoading(false); return; }
      if (!res.ok) {
        setError(`Failed to fetch template detail (${res.status})`);
        setDetailLoading(false);
        return;
      }
      const data = await res.json();
      const detail: XQueryTemplateDetail = data?.xquery || null;
      setSelectedTemplate(detail);
    } catch {
      setError('Network error');
    }
    setDetailLoading(false);
  }, [domainId, solutionId, authFetch, selectedTemplate]);

  const fetchTemplates = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/xquerys`,
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (!res) { setLoading(false); return; }
      if (res.status === 204) {
        setTemplates([]);
        setLoading(false);
        return;
      }
      if (!res.ok) {
        setError(`Failed to fetch named query templates (${res.status})`);
        setLoading(false);
        return;
      }
      const data = await res.json();
      const list = data?.xquerys?.xquery || [];
      setTemplates(Array.isArray(list) ? list : []);
    } catch {
      setError('Network error');
    }
    setLoading(false);
  }, [domainId, solutionId, authFetch]);

  useEffect(() => {
    fetchTemplates();
  }, [fetchTemplates]);

  // Deep-link from the Services tab. The list carries only summary fields, so fetch the full
  // template before opening the editor -- otherwise script and parameters would edit as blank
  // and saving would wipe them.
  useEffect(() => {
    if (!openForEdit || loading) return;
    let cancelled = false;
    (async () => {
      const res = await authFetch(
        `/${domainId}/${solutionId}/xquerys/${openForEdit}`,
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );
      if (cancelled) return;
      if (!res || !res.ok) {
        setError(
          `No Named Query template named "${openForEdit}" — the service row exists but its template does not.`
        );
      } else {
        const data = await res.json();
        if (data?.xquery) {
          setSelectedTemplate(data.xquery);
          beginEdit(data.xquery);
        }
      }
      onOpenForEditHandled?.();
    })();
    return () => { cancelled = true; };
  }, [openForEdit, loading, domainId, solutionId, authFetch, beginEdit, onOpenForEditHandled]);

  if (loading) {
    return <p className="text-gray-500 text-sm">Loading named query templates...</p>;
  }

  return (
    <div>
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
          {error}
        </div>
      )}

      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-semibold text-gray-800">Named Query Templates</h2>
        <div className="flex gap-2">
          <button onClick={() => setShowCreateForm(true)} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">Create Template</button>
          <button
            onClick={fetchTemplates}
            className="text-gray-400 hover:text-gray-600 text-sm"
            title="Refresh"
          >
            ↻ Refresh
          </button>
        </div>
      </div>

      {showCreateForm && (
        <div className="bg-white border border-gray-200 rounded-lg p-6 mb-4 shadow-sm">
          <h3 className="text-md font-semibold text-gray-800 mb-4">Create Named Query Template</h3>
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
              {error}
            </div>
          )}
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Query Token *</label>
              <input
                type="text"
                value={formState.queryToken}
                onChange={(e) => setFormState({ ...formState, queryToken: e.target.value })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="e.g. StudentGPA"
              />
              <p className="text-xs text-gray-400 mt-1">Unique name consumers use to invoke this query.</p>
              </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Type</label>
              <select
                value={formState.type}
                onChange={(e) => setFormState({ ...formState, type: e.target.value as CreateFormState['type'] })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="SINGULAR">SINGULAR</option>
                <option value="FORMULA">FORMULA</option>
                <option value="EXTENDED">EXTENDED</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Status</label>
              <select
                value={formState.status}
                onChange={(e) => setFormState({ ...formState, status: e.target.value as CreateFormState['status'] })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="PENDING">PENDING</option>
                <option value="APPROVED">APPROVED</option>
                <option value="DISALLOWED">DISALLOWED</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Qualifier</label>
              <input
                type="text"
                value={formState.qualifier}
                onChange={(e) => setFormState({ ...formState, qualifier: e.target.value })}
                placeholder="Reason the query was disallowed"
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">Only meaningful when Status is DISALLOWED — the schema uses it for the reason.</p>
              </div>
            <div className="col-span-2">
              <label className="block text-xs text-gray-500 mb-1">Description</label>
              <input
                type="text"
                value={formState.description}
                onChange={(e) => setFormState({ ...formState, description: e.target.value })}
                placeholder="Returns a snapshot of student data filtered by parameters"
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">Free text shown to consumers browsing available queries.</p>
              </div>
            <div className="col-span-2">
              <label className="block text-xs text-gray-500 mb-1">Script</label>
              <textarea
                  placeholder="SELECT * FROM students WHERE grade = :grade"
                value={formState.script}
                onChange={(e) => setFormState({ ...formState, script: e.target.value })}
                rows={4}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">The query body. Reference parameters by name, e.g. <code>:grade</code>.</p>
              </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Return Type</label>
              <input
                type="text"
                value={formState.returnType}
                onChange={(e) => setFormState({ ...formState, returnType: e.target.value })}
                placeholder="http://www.sifassociation.org/datamodel/na/3.3:students"
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">Fully qualified name of the root element this query returns. Must be an absolute URI — include a scheme, e.g. <code>http:</code> or <code>urn:</code>. A bare word such as <code>void</code> is rejected.</p>
              </div>
          </div>

          {/* Dynamic Parameters Section */}
          <div className="mb-4">
            <div className="flex justify-between items-center mb-2">
              <label className="block text-xs text-gray-500">Parameters</label>
              <p className="text-xs text-gray-400 mt-1">Name and Type are required per row. Type is free text — the list suggests common XSD types (e.g. <code>xs:string</code>, <code>xs:unsignedInt</code>). Leave Default blank to omit it.</p>
              <button
                type="button"
                onClick={() => setParameters([...parameters, { name: '', type: '', defaultValue: '', description: '', required: false }])}
                className="text-blue-600 hover:text-blue-800 text-xs font-medium"
              >
                + Add Parameter
              </button>
            </div>
            {/* Free-text per the schema ("a well understood type", example xs:unsignedInt),
                so this suggests the common XSD types without restricting the field. */}
            <datalist id="xsd-types">
              <option value="xs:string" />
              <option value="xs:boolean" />
              <option value="xs:int" />
              <option value="xs:unsignedInt" />
              <option value="xs:decimal" />
              <option value="xs:date" />
              <option value="xs:dateTime" />
              <option value="xs:anyURI" />
            </datalist>
            {parameters.map((param, idx) => (
              <div key={idx} className="flex gap-2 items-center mb-2">
                <input
                  type="text"
                  placeholder="Name"
                  value={param.name}
                  onChange={(e) => {
                    const updated = [...parameters];
                    updated[idx] = { ...updated[idx], name: e.target.value };
                    setParameters(updated);
                  }}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <input
                  type="text"
                  list="xsd-types"
                  placeholder="Type"
                  value={param.type}
                  onChange={(e) => {
                    const updated = [...parameters];
                    updated[idx] = { ...updated[idx], type: e.target.value };
                    setParameters(updated);
                  }}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <input
                  type="text"
                  placeholder="Default"
                  value={param.defaultValue}
                  onChange={(e) => {
                    const updated = [...parameters];
                    updated[idx] = { ...updated[idx], defaultValue: e.target.value };
                    setParameters(updated);
                  }}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <input
                  type="text"
                  placeholder="Description"
                  value={param.description}
                  onChange={(e) => {
                    const updated = [...parameters];
                    updated[idx] = { ...updated[idx], description: e.target.value };
                    setParameters(updated);
                  }}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <label className="flex items-center gap-1 text-xs text-gray-500 whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={param.required}
                    onChange={(e) => {
                      const updated = [...parameters];
                      updated[idx] = { ...updated[idx], required: e.target.checked };
                      setParameters(updated);
                    }}
                  />
                  Req
                </label>
                <button
                  type="button"
                  onClick={() => setParameters(parameters.filter((_, i) => i !== idx))}
                  className="text-red-500 hover:text-red-700 font-medium text-sm"
                  title="Remove parameter"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>

          {/* Form Actions */}
          <div className="flex gap-2">
            <button
              disabled={isSubmitDisabled(formState.queryToken) || submitting}
              onClick={async () => {
                setSubmitting(true);
                setError(null);
                try {
                  // Wrapped under `xquery` per the SIF 3.7 envelope convention. Optional fields
                  // are omitted when empty rather than sent as null: the schemas type their
                  // optionals without `nullable`, so an explicit null is nonconformant.
                  // script and returnType are required, so they are always sent.
                  const payload = {
                    xquery: {
                      id: formState.queryToken.trim(),
                      type: formState.type,
                      status: formState.status,
                      ...(formState.qualifier.trim() ? { qualifier: formState.qualifier.trim() } : {}),
                      ...(formState.description.trim() ? { description: formState.description.trim() } : {}),
                      script: formState.script.trim(),
                      parameters: { parameter: filterValidParameters(parameters) },
                      returnType: formState.returnType.trim(),
                    },
                  };
                  // POST goes to the singular sub-path. POSTing to the collection URL is a 405
                  // by design -- that is what this form used to do.
                  const res = await authFetch(
                    `/${domainId}/${solutionId}/xquerys/xquery`,
                    { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) },
                    { 'X-Run-As-Environment': 'broker-services' }
                  );
                  if (!res) { setSubmitting(false); return; }
                  if (res.status === 201) {
                    setShowCreateForm(false);
                    setFormState(initialFormState);
                    setParameters([]);
                    setTemplates([...templates, { id: payload.xquery.id, type: payload.xquery.type, status: payload.xquery.status, description: payload.xquery.description }]);
                  } else if (res.status === 409) {
                    setError('A template with this query token already exists');
                  } else if (res.status === 400) {
                    const text = await res.text();
                    setError(text || `Failed to create template (${res.status})`);
                  } else {
                    setError(`Failed to create template (${res.status})`);
                  }
                } catch {
                  setError('Network error');
                }
                setSubmitting(false);
              }}
              className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {submitting ? 'Creating...' : 'Create Template'}
            </button>
            <button
              onClick={() => {
                setShowCreateForm(false);
                setFormState(initialFormState);
                setParameters([]);
                setError(null);
              }}
              className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="bg-gray-50 border-b border-gray-200">
              <th className="text-left px-4 py-3 font-medium text-gray-700">Query Token</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">Type</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">Status</th>
              <th className="text-left px-4 py-3 font-medium text-gray-700">Description</th>
              <th className="text-right px-4 py-3 font-medium text-gray-700">Actions</th>
            </tr>
          </thead>
          <tbody>
            {templates.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                  No named query templates found
                </td>
              </tr>
            ) : (
              templates.map((template) => (
                <tr key={template.id} className="border-b border-gray-100 last:border-b-0 hover:bg-gray-50 cursor-pointer" onClick={() => handleRowClick(template)}>
                  <td className="px-4 py-3 text-gray-800 font-medium">{template.id}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${getTypeBadgeClass()}`}>
                      {template.type}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${getStatusBadgeClass(template.status)}`}>
                      {template.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-600">{truncateDescription(template.description)}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      className="text-red-500 hover:text-red-700 font-medium"
                      title="Delete"
                      onClick={(e) => handleDelete(e, template)}
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

      {editToken && (
        <div className="bg-white rounded-lg border border-blue-200 p-6 mt-4 shadow-sm">
          <h3 className="text-md font-semibold text-gray-800 mb-1">Edit Named Query Template</h3>
          <p className="text-xs text-gray-400 mb-4">
            Query Token <span className="font-mono text-gray-600">{editToken}</span> cannot be changed
            &mdash; zone bindings and provider registrations reference it by name. Delete and recreate to
            rename.
          </p>
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">Type</label>
              <select
                value={editForm.type}
                onChange={(e) => setEditForm({ ...editForm, type: e.target.value as CreateFormState['type'] })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="SINGULAR">SINGULAR</option>
                <option value="FORMULA">FORMULA</option>
                <option value="EXTENDED">EXTENDED</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Status</label>
              <select
                value={editForm.status}
                onChange={(e) => setEditForm({ ...editForm, status: e.target.value as CreateFormState['status'] })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value="PENDING">PENDING</option>
                <option value="APPROVED">APPROVED</option>
                <option value="DISALLOWED">DISALLOWED</option>
              </select>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Qualifier</label>
              <input
                type="text"
                value={editForm.qualifier}
                onChange={(e) => setEditForm({ ...editForm, qualifier: e.target.value })}
                placeholder="Reason the query was disallowed"
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              <p className="text-xs text-gray-400 mt-1">Only meaningful when Status is DISALLOWED.</p>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">Return Type</label>
              <input
                type="text"
                value={editForm.returnType}
                onChange={(e) => setEditForm({ ...editForm, returnType: e.target.value })}
                placeholder="http://www.sifassociation.org/datamodel/na/3.3:students"
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
              {/* Clearing this is rejected, not honoured -- returnType is required on create, so an
                  empty value would leave a template the broker cannot emit conformantly. */}
              <p className="text-xs text-gray-400 mt-1">Must be an absolute URI. Cannot be cleared.</p>
            </div>
            <div className="col-span-2">
              <label className="block text-xs text-gray-500 mb-1">Description</label>
              <input
                type="text"
                value={editForm.description}
                onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs text-gray-500 mb-1">Script</label>
              <textarea
                value={editForm.script}
                onChange={(e) => setEditForm({ ...editForm, script: e.target.value })}
                rows={4}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          <div className="mb-4">
            <div className="flex items-center justify-between">
              <label className="block text-xs text-gray-500">Parameters</label>
              <button
                onClick={() => setEditParameters([...editParameters, { name: '', type: '', defaultValue: '', description: '', required: false }])}
                className="text-blue-600 hover:text-blue-800 text-xs"
              >
                + Add Parameter
              </button>
            </div>
            <p className="text-xs text-gray-400 mt-1">
              Rows with a blank Name are dropped on save. Leave Default blank to omit it.
            </p>
            {editParameters.map((param, idx) => (
              <div key={idx} className="flex items-center gap-2 mt-2">
                <input
                  type="text"
                  placeholder="Name"
                  value={param.name}
                  onChange={(e) => {
                    const updated = [...editParameters];
                    updated[idx] = { ...updated[idx], name: e.target.value };
                    setEditParameters(updated);
                  }}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                />
                <input
                  type="text"
                  list="xsd-types"
                  placeholder="Type"
                  value={param.type}
                  onChange={(e) => {
                    const updated = [...editParameters];
                    updated[idx] = { ...updated[idx], type: e.target.value };
                    setEditParameters(updated);
                  }}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                />
                <input
                  type="text"
                  placeholder="Default"
                  value={param.defaultValue}
                  onChange={(e) => {
                    const updated = [...editParameters];
                    updated[idx] = { ...updated[idx], defaultValue: e.target.value };
                    setEditParameters(updated);
                  }}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                />
                <input
                  type="text"
                  placeholder="Description"
                  value={param.description}
                  onChange={(e) => {
                    const updated = [...editParameters];
                    updated[idx] = { ...updated[idx], description: e.target.value };
                    setEditParameters(updated);
                  }}
                  className="flex-1 border border-gray-300 rounded px-2 py-1 text-sm"
                />
                <label className="flex items-center gap-1 text-xs text-gray-500 whitespace-nowrap">
                  <input
                    type="checkbox"
                    checked={param.required}
                    onChange={(e) => {
                      const updated = [...editParameters];
                      updated[idx] = { ...updated[idx], required: e.target.checked };
                      setEditParameters(updated);
                    }}
                  />
                  Required
                </label>
                <button
                  onClick={() => setEditParameters(editParameters.filter((_, i) => i !== idx))}
                  className="text-red-500 hover:text-red-700 text-sm px-1"
                  title="Remove parameter"
                >
                  &#10005;
                </button>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <button
              disabled={editSubmitting}
              onClick={saveEdit}
              className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {editSubmitting ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              onClick={() => { setEditToken(null); setError(null); }}
              className="text-gray-500 hover:text-gray-700 text-sm px-3 py-2"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {detailLoading && <p className="text-gray-500 text-sm mt-4">Loading details...</p>}
      {selectedTemplate && !detailLoading && !editToken && (
        <div className="bg-white rounded-lg border border-gray-200 p-6 mt-4">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-md font-semibold text-gray-800">Template Detail</h3>
            <div className="flex items-center gap-2">
              {selectedTemplate.status !== 'APPROVED' && (
                <button
                  onClick={() => handleApprove(selectedTemplate.id)}
                  className="bg-green-600 text-white text-sm font-medium px-3 py-1.5 rounded hover:bg-green-700"
                  title="Set status to APPROVED"
                >
                  Approve
                </button>
              )}
              <button
                onClick={() => beginEdit(selectedTemplate)}
                className="bg-blue-600 text-white text-sm font-medium px-3 py-1.5 rounded hover:bg-blue-700"
              >
                Edit
              </button>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-4 mb-4">
            <div>
              <span className="text-xs text-gray-500 font-medium">Query Token</span>
              <p className="text-sm text-gray-800">{selectedTemplate.id}</p>
            </div>
            <div>
              <span className="text-xs text-gray-500 font-medium">Type</span>
              <p className="text-sm text-gray-800">{selectedTemplate.type}</p>
            </div>
            <div>
              <span className="text-xs text-gray-500 font-medium">Status</span>
              <p className="text-sm text-gray-800">{selectedTemplate.status}</p>
            </div>
            <div>
              <span className="text-xs text-gray-500 font-medium">Qualifier</span>
              <p className="text-sm text-gray-800">{selectedTemplate.qualifier || '—'}</p>
            </div>
            <div className="col-span-2">
              <span className="text-xs text-gray-500 font-medium">Description</span>
              <p className="text-sm text-gray-800">{selectedTemplate.description || '—'}</p>
            </div>
            <div>
              <span className="text-xs text-gray-500 font-medium">Return Type</span>
              <p className="text-sm text-gray-800">{selectedTemplate.returnType || '—'}</p>
            </div>
          </div>

          {selectedTemplate.script && (
            <div className="mb-4">
              <span className="text-xs text-gray-500 font-medium">Script</span>
              <pre className="bg-gray-100 rounded p-3 text-xs font-mono overflow-x-auto mt-1">{selectedTemplate.script}</pre>
            </div>
          )}

          <div>
            <span className="text-xs text-gray-500 font-medium">Parameters</span>
            {(!selectedTemplate.parameters?.parameter || selectedTemplate.parameters.parameter.length === 0) ? (
              <p className="text-sm text-gray-400 mt-1">No parameters defined</p>
            ) : (
              <div className="bg-white rounded-lg border border-gray-200 overflow-hidden mt-1">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-gray-50 border-b border-gray-200">
                      <th className="text-left px-4 py-2 font-medium text-gray-700">Name</th>
                      <th className="text-left px-4 py-2 font-medium text-gray-700">Type</th>
                      <th className="text-left px-4 py-2 font-medium text-gray-700">Default Value</th>
                      <th className="text-left px-4 py-2 font-medium text-gray-700">Description</th>
                      <th className="text-left px-4 py-2 font-medium text-gray-700">Required</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selectedTemplate.parameters.parameter.map((param, idx) => (
                      <tr key={idx} className="border-b border-gray-100 last:border-b-0">
                        <td className="px-4 py-2 text-gray-800 font-medium">{param.name}</td>
                        <td className="px-4 py-2 text-gray-600">{param.type}</td>
                        <td className="px-4 py-2 text-gray-600">{param.default || '—'}</td>
                        <td className="px-4 py-2 text-gray-600">{param.description || '—'}</td>
                        <td className="px-4 py-2 text-gray-600">{param.required ? 'Yes' : 'No'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
