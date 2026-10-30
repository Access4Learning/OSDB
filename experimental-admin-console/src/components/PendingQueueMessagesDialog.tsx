import { useCallback, useEffect, useState } from 'react';
import {
  type AuthFetch,
  type PendingQueueMessage,
  fetchPendingQueueMessages,
} from '../utils/queueActions';

interface Props {
  domainId: string;
  solutionId: string;
  queueId: string;
  queueName: string;
  authFetch: AuthFetch;
  onClose: () => void;
}

function formatIdleTime(idleTimeMs: number): string {
  if (idleTimeMs < 1_000) return `${idleTimeMs} ms`;
  if (idleTimeMs < 60_000) return `${Math.floor(idleTimeMs / 1_000)} sec`;
  if (idleTimeMs < 3_600_000) return `${Math.floor(idleTimeMs / 60_000)} min`;
  return `${Math.floor(idleTimeMs / 3_600_000)} hr`;
}

export default function PendingQueueMessagesDialog({
  domainId,
  solutionId,
  queueId,
  queueName,
  authFetch,
  onClose,
}: Props) {
  const [messages, setMessages] = useState<PendingQueueMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const pending = await fetchPendingQueueMessages(
        authFetch,
        domainId,
        solutionId,
        queueId
      );
      if (pending) setMessages(pending);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load pending messages');
    } finally {
      setLoading(false);
    }
  }, [authFetch, domainId, solutionId, queueId]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-[900px] max-w-[95vw] max-h-[85vh] p-6 flex flex-col">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h2 className="text-lg font-semibold text-gray-800">{queueName} — Pending Messages</h2>
            <p className="text-xs text-gray-500 mt-1">
              Messages currently reserved or in process across all consumer connections. Showing up to 100 oldest entries.
            </p>
          </div>
          <button onClick={load} disabled={loading} className="text-blue-600 hover:text-blue-800 text-sm disabled:opacity-50">
            {loading ? 'Loading...' : '↻ Refresh'}
          </button>
        </div>

        {error && <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">{error}</div>}

        <div className="border border-gray-200 rounded overflow-auto flex-1">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-gray-50">
              <tr className="border-b border-gray-200">
                <th className="text-left px-3 py-2 font-medium text-gray-600">Message ID</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">Consumer</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">Idle</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">Deliveries</th>
                <th className="text-left px-3 py-2 font-medium text-gray-600">Stream ID</th>
              </tr>
            </thead>
            <tbody>
              {!loading && messages.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-gray-400">No pending messages</td></tr>
              ) : messages.map((message) => (
                <tr key={message.streamId} className="border-b border-gray-100">
                  <td className="px-3 py-2 text-gray-700 text-xs font-mono">{message.messageId}</td>
                  <td className="px-3 py-2 text-gray-700">{message.consumerName}</td>
                  <td className="px-3 py-2 text-gray-600">{formatIdleTime(message.idleTimeMs)}</td>
                  <td className="px-3 py-2 text-gray-600">{message.deliveryCount}</td>
                  <td className="px-3 py-2 text-gray-500 text-xs font-mono">{message.streamId}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex justify-end mt-4">
          <button onClick={onClose} className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700">
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
