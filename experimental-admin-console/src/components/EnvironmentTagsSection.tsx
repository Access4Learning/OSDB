import { useEffect, useState } from 'react';

interface EntityTag {
  id: string;
  solutionId: string;
  entityType: string;
  entityId: string;
  tagKey: string;
  tagValue: string | null;
  createdAt: string;
}

interface EnvironmentTagsSectionProps {
  domainId: string;
  solutionId: string;
  envId: string;
  authFetch: (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => Promise<Response | null>;
}

export default function EnvironmentTagsSection({ domainId, solutionId, envId, authFetch }: EnvironmentTagsSectionProps) {
  const [tags, setTags] = useState<EntityTag[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isAdmin, setIsAdmin] = useState(false);

  const fetchTags = async () => {
    setLoading(true);
    setError(null);

    try {
      // Fetch ENVIRONMENT tags for this environment
      const envRes = await authFetch(
        `/${domainId}/${solutionId}/tags?entityType=ENVIRONMENT&entityId=${envId}`
      );
      const envTags: EntityTag[] = [];
      if (envRes && envRes.ok) {
        const data = await envRes.json();
        if (Array.isArray(data)) envTags.push(...data);
      } else if (envRes && !envRes.ok) {
        setError(`Failed to fetch environment tags (${envRes.status})`);
        setLoading(false);
        return;
      }

      // Fetch QUEUE tags — get all queue tags in solution, then filter to queues owned by this environment
      const queueRes = await authFetch(
        `/${domainId}/${solutionId}/tags?entityType=QUEUE`
      );
      const queueTags: EntityTag[] = [];
      if (queueRes && queueRes.ok) {
        const data = await queueRes.json();
        if (Array.isArray(data)) {
          // Get queues owned by this environment to filter queue tags
          const queuesRes = await authFetch(`/${domainId}/${solutionId}/queues`, undefined, {
            'X-Run-As-Environment': 'broker-services',
          });
          if (queuesRes && queuesRes.ok) {
            const queuesData = await queuesRes.json();
            const queueList = queuesData.queues?.queue || [];
            const ownedQueueIds = new Set(
              queueList
                .filter((q: any) => q.ownerId === envId)
                .map((q: any) => q.id)
            );
            queueTags.push(...data.filter((t: EntityTag) => ownedQueueIds.has(t.entityId)));
          }
        }
      }

      setTags([...envTags, ...queueTags]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch tags');
    } finally {
      setLoading(false);
    }
  };

  const checkAdminAccess = async () => {
    // Attempt to access admin endpoint — if not 403, user is admin
    const res = await authFetch('/admin/tag-definitions');
    if (res && res.status !== 403) {
      setIsAdmin(true);
    }
  };

  useEffect(() => {
    fetchTags();
    checkAdminAccess();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleDeleteTag = async (tag: EntityTag) => {
    if (!window.confirm(`Remove tag "${tag.tagKey}" from ${tag.entityType}?`)) return;
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
    <div className="mt-6">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-sm font-semibold text-gray-700">Environment Tags</h2>
        <button
          onClick={fetchTags}
          className="text-gray-400 hover:text-gray-600 text-sm"
          title="Refresh"
        >
          ↻ Refresh
        </button>
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
          {error}
        </div>
      )}

      {loading ? (
        <p className="text-gray-500 text-sm">Loading tags...</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th className="text-left px-4 py-3 font-medium text-gray-600">Tag Key</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Value</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">Entity Type</th>
                {isAdmin && (
                  <th className="text-right px-4 py-3 font-medium text-gray-600 w-20">Actions</th>
                )}
              </tr>
            </thead>
            <tbody>
              {tags.length === 0 ? (
                <tr>
                  <td colSpan={isAdmin ? 4 : 3} className="px-4 py-6 text-center text-gray-400">
                    No tags assigned to this environment or its queues
                  </td>
                </tr>
              ) : (
                tags.map((tag) => (
                  <tr key={tag.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-3 font-mono text-gray-800">{tag.tagKey}</td>
                    <td className="px-4 py-3 text-gray-600">{tag.tagValue || '—'}</td>
                    <td className="px-4 py-3">
                      <span className="inline-block bg-blue-100 text-blue-700 text-xs px-2 py-0.5 rounded">
                        {tag.entityType}
                      </span>
                    </td>
                    {isAdmin && (
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => handleDeleteTag(tag)}
                          className="text-red-500 hover:text-red-700 text-xs font-medium"
                          title="Remove tag"
                        >
                          Remove
                        </button>
                      </td>
                    )}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
