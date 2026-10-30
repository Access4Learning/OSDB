import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Navbar from '../components/Navbar';
import { useAuth } from '../context/AuthContext';
import { authenticatedFetch } from '../utils/auth';

interface TagDefinition {
  id: string;
  tagName: string;
  tagDescription: string | null;
  applicableEntityTypes: string[];
  allowedValues: string[] | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

const ENTITY_TYPES = ['SOLUTION', 'ZONE', 'CONTEXT', 'ENVIRONMENT', 'QUEUE'];

export default function TagDefinitionsPage() {
  const { sessionToken, sharedSecret, logout } = useAuth();
  const navigate = useNavigate();

  const [definitions, setDefinitions] = useState<TagDefinition[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);

  // Modal state
  const [showModal, setShowModal] = useState(false);
  const [editingDef, setEditingDef] = useState<TagDefinition | null>(null);
  const [formTagName, setFormTagName] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formEntityTypes, setFormEntityTypes] = useState<string[]>([]);
  const [formAllowedValues, setFormAllowedValues] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Delete confirmation
  const [deletingDef, setDeletingDef] = useState<TagDefinition | null>(null);

  const authFetch = async (path: string, opts?: RequestInit) => {
    if (!sessionToken || !sharedSecret) return null;
    const res = await authenticatedFetch(path, sessionToken, sharedSecret, opts);
    if (res.status === 401) { logout(); navigate('/login'); return null; }
    return res;
  };

  const fetchDefinitions = async () => {
    setLoading(true);
    setError(null);
    setForbidden(false);
    const res = await authFetch('/admin/tag-definitions');
    if (!res) return;
    if (res.status === 403) {
      setForbidden(true);
      setLoading(false);
      return;
    }
    if (!res.ok) {
      setError(`Failed to fetch tag definitions (${res.status})`);
      setLoading(false);
      return;
    }
    const data = await res.json();
    setDefinitions(Array.isArray(data) ? data : []);
    setLoading(false);
  };

  useEffect(() => {
    fetchDefinitions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreateModal = () => {
    setEditingDef(null);
    setFormTagName('');
    setFormDescription('');
    setFormEntityTypes([]);
    setFormAllowedValues('');
    setShowModal(true);
  };

  const openEditModal = (def: TagDefinition) => {
    setEditingDef(def);
    setFormTagName(def.tagName);
    setFormDescription(def.tagDescription || '');
    setFormEntityTypes([...def.applicableEntityTypes]);
    setFormAllowedValues(def.allowedValues ? def.allowedValues.join(', ') : '');
    setShowModal(true);
  };

  const closeModal = () => {
    setShowModal(false);
    setEditingDef(null);
  };

  const toggleEntityType = (type: string) => {
    setFormEntityTypes((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]
    );
  };

  const parseAllowedValues = (input: string): string[] | null => {
    const trimmed = input.trim();
    if (!trimmed) return null;
    return trimmed.split(',').map((v) => v.trim()).filter((v) => v.length > 0);
  };

  const handleSubmit = async () => {
    if (!formTagName.trim() || formEntityTypes.length === 0) return;
    setSubmitting(true);
    setError(null);

    const allowedValues = parseAllowedValues(formAllowedValues);

    try {
      if (editingDef) {
        // Update
        const res = await authFetch(`/admin/tag-definitions/${editingDef.id}`, {
          method: 'PUT',
          body: JSON.stringify({
            tagDescription: formDescription.trim() || null,
            applicableEntityTypes: formEntityTypes,
            allowedValues,
          }),
        });
        if (!res) return;
        if (!res.ok) {
          const text = await res.text();
          throw new Error(`Failed to update (${res.status}): ${text}`);
        }
      } else {
        // Create
        const res = await authFetch('/admin/tag-definitions', {
          method: 'POST',
          body: JSON.stringify({
            tagName: formTagName.trim(),
            tagDescription: formDescription.trim() || null,
            applicableEntityTypes: formEntityTypes,
            allowedValues,
            createdBy: 'admin-console',
          }),
        });
        if (!res) return;
        if (res.status === 409) {
          throw new Error('A tag definition with this name already exists');
        }
        if (!res.ok) {
          const text = await res.text();
          throw new Error(`Failed to create (${res.status}): ${text}`);
        }
      }
      closeModal();
      fetchDefinitions();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Operation failed');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingDef) return;
    setError(null);
    try {
      const res = await authFetch(`/admin/tag-definitions/${deletingDef.id}`, {
        method: 'DELETE',
      });
      if (!res) return;
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`Failed to delete (${res.status}): ${text}`);
      }
      setDefinitions((prev) => prev.filter((d) => d.id !== deletingDef.id));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Delete failed');
    } finally {
      setDeletingDef(null);
    }
  };

  const formatDate = (ts: string) => {
    const d = new Date(ts);
    return d.toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' });
  };

  if (forbidden) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <main className="max-w-4xl mx-auto px-6 py-8">
          <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3">
            Access denied. Tag Definitions management requires Global Domain Admin permissions.
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-6xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold text-gray-800">Tag Definitions</h1>
            <button
              onClick={fetchDefinitions}
              className="text-gray-400 hover:text-gray-600 transition-colors"
              title="Refresh"
            >
              ↻
            </button>
          </div>
          <button
            onClick={openCreateModal}
            className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 transition-colors"
          >
            Create Definition
          </button>
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
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Tag Name</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Description</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Entity Types</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Allowed Values</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Created By</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Updated</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600 w-24">Actions</th>
                </tr>
              </thead>
              <tbody>
                {definitions.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-4 py-6 text-center text-gray-400">
                      No tag definitions found
                    </td>
                  </tr>
                ) : (
                  definitions.map((def) => (
                    <tr key={def.id} className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="px-4 py-3 font-mono text-gray-800">{def.tagName}</td>
                      <td className="px-4 py-3 text-gray-600 max-w-xs truncate" title={def.tagDescription || ''}>
                        {def.tagDescription || '—'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-1">
                          {def.applicableEntityTypes.map((t) => (
                            <span key={t} className="inline-block bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded">
                              {t}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {def.allowedValues ? (
                          <div className="flex flex-wrap gap-1">
                            {def.allowedValues.map((v) => (
                              <span key={v} className="inline-block bg-gray-100 text-gray-700 text-xs px-2 py-0.5 rounded">
                                {v}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <span className="text-gray-400 italic">any</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-gray-600">{def.createdBy}</td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{formatDate(def.updatedAt)}</td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => openEditModal(def)}
                          className="text-blue-600 hover:text-blue-800 text-xs font-medium mr-3"
                          title="Edit"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => setDeletingDef(def)}
                          className="text-red-500 hover:text-red-700 text-xs font-medium"
                          title="Delete"
                        >
                          Delete
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

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-lg mx-4 p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-4">
              {editingDef ? 'Edit Tag Definition' : 'Create Tag Definition'}
            </h2>

            <div className="space-y-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  Tag Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={formTagName}
                  onChange={(e) => setFormTagName(e.target.value)}
                  disabled={!!editingDef}
                  placeholder="e.g. MY_TAG_NAME"
                  className={`w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 ${editingDef ? 'bg-gray-100 cursor-not-allowed' : ''}`}
                />
                {editingDef && (
                  <p className="text-xs text-gray-400 mt-1">Tag name cannot be changed after creation</p>
                )}
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">Description</label>
                <textarea
                  value={formDescription}
                  onChange={(e) => setFormDescription(e.target.value)}
                  placeholder="Optional description of this tag definition"
                  rows={3}
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none"
                />
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">
                  Applicable Entity Types <span className="text-red-500">*</span>
                </label>
                <div className="flex flex-wrap gap-2 mt-1">
                  {ENTITY_TYPES.map((type) => (
                    <button
                      key={type}
                      type="button"
                      onClick={() => toggleEntityType(type)}
                      className={`text-xs px-3 py-1.5 rounded border transition-colors ${
                        formEntityTypes.includes(type)
                          ? 'bg-blue-600 text-white border-blue-600'
                          : 'bg-white text-gray-600 border-gray-300 hover:border-blue-400'
                      }`}
                    >
                      {type}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-xs text-gray-500 mb-1">Allowed Values</label>
                <input
                  type="text"
                  value={formAllowedValues}
                  onChange={(e) => setFormAllowedValues(e.target.value)}
                  placeholder="Comma-separated values (leave empty for unrestricted)"
                  className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
                <p className="text-xs text-gray-400 mt-1">
                  Leave empty to allow any value. Separate multiple values with commas.
                </p>
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                onClick={closeModal}
                className="text-gray-500 hover:text-gray-700 text-sm px-4 py-2"
              >
                Cancel
              </button>
              <button
                onClick={handleSubmit}
                disabled={submitting || !formTagName.trim() || formEntityTypes.length === 0}
                className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? 'Saving...' : editingDef ? 'Update' : 'Create'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Dialog */}
      {deletingDef && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-semibold text-gray-800 mb-2">Delete Tag Definition</h2>
            <p className="text-sm text-gray-600 mb-1">
              Are you sure you want to delete <span className="font-mono font-medium">{deletingDef.tagName}</span>?
            </p>
            <p className="text-xs text-gray-400 mb-4">
              Existing entity tags using this definition will not be affected.
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setDeletingDef(null)}
                className="text-gray-500 hover:text-gray-700 text-sm px-4 py-2"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                className="bg-red-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-red-700 transition-colors"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
