import { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

interface EntityTag {
  id: string;
  solutionId: string;
  entityType: string;
  entityId: string;
  tagKey: string;
  tagValue: string | null;
  createdAt: string;
}

interface TagDefinition {
  id: string;
  tagName: string;
  tagDescription: string | null;
  applicableEntityTypes: string[];
  allowedValues: string[] | null;
}

interface SolutionTagsPageProps {
  domainId: string;
  solutionId: string;
}

const ENTITY_TYPES = ['SOLUTION', 'ZONE', 'CONTEXT', 'ENVIRONMENT', 'QUEUE'];

type SortField = 'tagKey' | 'entityType' | 'entityId';
type SortDir = 'asc' | 'desc';

export default function SolutionTagsPage({ domainId, solutionId }: SolutionTagsPageProps) {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();

  const [tags, setTags] = useState<EntityTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters
  const [filterTagKey, setFilterTagKey] = useState('');
  const [filterEntityType, setFilterEntityType] = useState('');
  const [filterTagValue, setFilterTagValue] = useState('');

  // Sorting
  const [sortField, setSortField] = useState<SortField>('tagKey');
  const [sortDir, setSortDir] = useState<SortDir>('asc');

  // Assign tag modal
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [definitions, setDefinitions] = useState<TagDefinition[]>([]);
  const [assignEntityType, setAssignEntityType] = useState('');
  const [assignEntityId, setAssignEntityId] = useState('');
  const [assignTagDef, setAssignTagDef] = useState<TagDefinition | null>(null);
  const [assignTagValue, setAssignTagValue] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const authFetch = async (path: string, opts?: RequestInit) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  const fetchTags = async () => {
    setLoading(true);
    setError(null);
    const res = await authFetch(`/${domainId}/${solutionId}/tags`);
    if (!res) return;
    if (!res.ok) {
      setError(`Failed to fetch tags (${res.status})`);
      setLoading(false);
      return;
    }
    const data = await res.json();
    setTags(Array.isArray(data) ? data : []);
    setLoading(false);
  };

  const fetchDefinitions = async () => {
    const res = await authFetch('/admin/tag-definitions');
    if (!res) return;
    if (res.ok) {
      const data = await res.json();
      setDefinitions(Array.isArray(data) ? data : []);
    }
  };

  useEffect(() => {
    fetchTags();
    fetchDefinitions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Filtered and sorted tags
  const filteredTags = useMemo(() => {
    let result = [...tags];

    if (filterTagKey) {
      const lower = filterTagKey.toLowerCase();
      result = result.filter((t) => t.tagKey.toLowerCase().includes(lower));
    }
    if (filterEntityType) {
      result = result.filter((t) => t.entityType === filterEntityType);
    }
    if (filterTagValue) {
      const lower = filterTagValue.toLowerCase();
      result = result.filter((t) => (t.tagValue || '').toLowerCase().includes(lower));
    }

    result.sort((a, b) => {
      const aVal = a[sortField] || '';
      const bVal = b[sortField] || '';
      const cmp = aVal.localeCompare(bVal);
      return sortDir === 'asc' ? cmp : -cmp;
    });

    return result;
  }, [tags, filterTagKey, filterEntityType, filterTagValue, sortField, sortDir]);

  const handleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDir('asc');
    }
  };

  const sortIndicator = (field: SortField) => {
    if (sortField !== field) return '';
    return sortDir === 'asc' ? ' ↑' : ' ↓';
  };

  // Assign tag modal helpers
  const filteredDefinitions = useMemo(() => {
    if (!assignEntityType) return definitions;
    return definitions.filter((d) => d.applicableEntityTypes.includes(assignEntityType));
  }, [definitions, assignEntityType]);

  const openAssignModal = () => {
    setAssignEntityType('');
    setAssignEntityId('');
    setAssignTagDef(null);
    setAssignTagValue('');
    setShowAssignModal(true);
  };

  const closeAssignModal = () => {
    setShowAssignModal(false);
    setAssignTagDef(null);
  };

  const handleAssignEntityTypeChange = (type: string) => {
    setAssignEntityType(type);
    setAssignTagDef(null);
    setAssignTagValue('');
  };

  const handleAssignTagDefChange = (defId: string) => {
    const def = definitions.find((d) => d.id === defId) || null;
    setAssignTagDef(def);
    setAssignTagValue('');
  };

  const handleAssignSubmit = async () => {
    if (!assignEntityType || !assignEntityId.trim() || !assignTagDef) return;
    setSubmitting(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/tags`, {
        method: 'POST',
        body: JSON.stringify({
          entityType: assignEntityType,
          entityId: assignEntityId.trim(),
          tagKey: assignTagDef.tagName,
          tagValue: assignTagValue.trim() || null,
        }),
      });
      if (!res) return;
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Failed to assign tag (${res.status}): ${text}`);
      }
      closeAssignModal();
      fetchTags();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Assign failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRemoveTag = async (tag: EntityTag) => {
    setError(null);
    try {
      const res = await authFetch(
        `/${domainId}/${solutionId}/tags/${tag.entityType}/${tag.entityId}/${tag.tagKey}`,
        { method: 'DELETE' }
      );
      if (!res) return;
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Failed to remove tag (${res.status}): ${text}`);
      }
      setTags((prev) => prev.filter((t) => t.id !== tag.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Remove failed');
    }
  };

  return (
    <div>
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-3">
          <button
            onClick={fetchTags}
            className="text-gray-400 hover:text-gray-600 text-sm"
            title="Refresh"
          >
            ↻ Refresh
          </button>
        </div>
        <button
          onClick={openAssignModal}
          className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 transition-colors"
        >
          Assign Tag
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
          {error}
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-3 mb-4">
        <input
          type="text"
          value={filterTagKey}
          onChange={(e) => setFilterTagKey(e.target.value)}
          placeholder="Filter by tag key..."
          className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-48"
        />
        <select
          value={filterEntityType}
          onChange={(e) => setFilterEntityType(e.target.value)}
          className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">All entity types</option>
          {ENTITY_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
        <input
          type="text"
          value={filterTagValue}
          onChange={(e) => setFilterTagValue(e.target.value)}
          placeholder="Filter by value..."
          className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 w-48"
        />
      </div>

      {loading ? (
        <p className="text-gray-500 text-sm">Loading...</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th
                  className="text-left px-4 py-3 font-medium text-gray-600 cursor-pointer select-none hover:text-gray-900"
                  onClick={() => handleSort('tagKey')}
                >
                  Tag Key{sortIndicator('tagKey')}
                </th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Value</th>
                <th
                  className="text-left px-4 py-3 font-medium text-gray-600 cursor-pointer select-none hover:text-gray-900"
                  onClick={() => handleSort('entityType')}
                >
                  Entity Type{sortIndicator('entityType')}
                </th>
                <th
                  className="text-left px-4 py-3 font-medium text-gray-600 cursor-pointer select-none hover:text-gray-900"
                  onClick={() => handleSort('entityId')}
                >
                  Entity ID{sortIndicator('entityId')}
                </th>
                <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Remove</th>
              </tr>
            </thead>
            <tbody>
              {filteredTags.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-6 text-center text-gray-400">
                    {tags.length === 0 ? 'No tags assigned in this solution' : 'No tags match the current filters'}
                  </td>
                </tr>
              ) : (
                filteredTags.map((tag) => (
                  <tr key={tag.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-gray-800">{tag.tagKey}</td>
                    <td className="px-4 py-3 text-gray-600">{tag.tagValue || '—'}</td>
                    <td className="px-4 py-3">
                      <span className="inline-block bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded">
                        {tag.entityType}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs font-mono">{tag.entityId}</td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => handleRemoveTag(tag)}
                        className="text-red-500 hover:text-red-700 text-xs font-medium"
                        title="Remove tag"
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Assign Tag Modal */}
      {showAssignModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-4">Assign Tag</h2>

            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  Entity Type <span className="text-red-500">*</span>
                </label>
                <select
                  value={assignEntityType}
                  onChange={(e) => handleAssignEntityTypeChange(e.target.value)}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">Select entity type...</option>
                  {ENTITY_TYPES.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  Entity ID <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={assignEntityId}
                  onChange={(e) => setAssignEntityId(e.target.value)}
                  placeholder="UUID of the entity"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  Tag Definition <span className="text-red-500">*</span>
                </label>
                <select
                  value={assignTagDef?.id || ''}
                  onChange={(e) => handleAssignTagDefChange(e.target.value)}
                  disabled={!assignEntityType}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
                >
                  <option value="">{assignEntityType ? 'Select tag definition...' : 'Select entity type first'}</option>
                  {filteredDefinitions.map((d) => (
                    <option key={d.id} value={d.id}>{d.tagName}</option>
                  ))}
                </select>
                {assignTagDef?.tagDescription && (
                  <p className="text-xs text-gray-400 mt-1">{assignTagDef.tagDescription}</p>
                )}
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">Value</label>
                {assignTagDef?.allowedValues ? (
                  <select
                    value={assignTagValue}
                    onChange={(e) => setAssignTagValue(e.target.value)}
                    className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">Select value...</option>
                    {assignTagDef.allowedValues.map((v) => (
                      <option key={v} value={v}>{v}</option>
                    ))}
                  </select>
                ) : (
                  <input
                    type="text"
                    value={assignTagValue}
                    onChange={(e) => setAssignTagValue(e.target.value)}
                    placeholder={assignTagDef ? 'Enter value (optional)' : 'Select a tag definition first'}
                    disabled={!assignTagDef}
                    className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
                  />
                )}
                {assignTagDef?.allowedValues && (
                  <p className="text-xs text-gray-400 mt-1">
                    Constrained to: {assignTagDef.allowedValues.join(', ')}
                  </p>
                )}
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={closeAssignModal}
                className="text-gray-500 hover:text-gray-700 text-sm px-4 py-2"
              >
                Cancel
              </button>
              <button
                onClick={handleAssignSubmit}
                disabled={submitting || !assignEntityType || !assignEntityId.trim() || !assignTagDef}
                className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? 'Assigning...' : 'Assign'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
