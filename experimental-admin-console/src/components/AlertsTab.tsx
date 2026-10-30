import { useEffect, useState, useCallback } from 'react';

// Types
export type AlertLevel = 'INFO' | 'STATECHANGE' | 'EVENT' | 'WARNING' | 'ERROR';
export type Exchange = 'REQUEST' | 'RESPONSE' | 'EVENT' | 'TIMEOUT' | 'OTHER';
export type SortableColumn = 'level' | 'reporter' | 'exchange';

export interface Alert {
  id: string;
  reporter: string;
  cause: string | null;
  exchange: Exchange;
  level: AlertLevel;
  description: string;
  messageID: string | null;
  body: string | null;
  error: string | null;
  xpath: string | null;
  category: number | null;
  code: number | null;
  internal: string | null;
}

export interface AlertsTabProps {
  domainId: string;
  solutionId: string;
  envId: string;
  authFetch: (path: string, opts?: RequestInit, extraHeaders?: Record<string, string>) => Promise<Response | null>;
}

// Pure utility functions

export function truncateDescription(desc: string | null): string {
  if (desc === null) return '';
  if (desc.length <= 50) return desc;
  return desc.slice(0, 50) + '\u2026';
}

export function levelColorClass(level: AlertLevel): string {
  switch (level) {
    case 'ERROR':
      return 'bg-red-100 text-red-800';
    case 'WARNING':
      return 'bg-yellow-100 text-yellow-800';
    case 'INFO':
      return 'bg-blue-100 text-blue-800';
    case 'STATECHANGE':
      return 'bg-green-100 text-green-800';
    case 'EVENT':
      return 'bg-gray-100 text-gray-800';
  }
}

export function displayMessageId(messageId: string | null | undefined): string {
  if (messageId === null || messageId === undefined || messageId === '') return '-';
  return messageId;
}

export function filterAlerts(
  alerts: Alert[],
  textFilter: string,
  levelFilter: AlertLevel | 'ALL'
): Alert[] {
  return alerts.filter((alert) => {
    if (levelFilter !== 'ALL' && alert.level !== levelFilter) return false;
    if (textFilter === '') return true;
    const lower = textFilter.toLowerCase();
    return (
      alert.reporter.toLowerCase().includes(lower) ||
      alert.description.toLowerCase().includes(lower) ||
      alert.exchange.toLowerCase().includes(lower) ||
      (alert.messageID ?? '').toLowerCase().includes(lower)
    );
  });
}

export function sortAlerts(
  alerts: Alert[],
  column: SortableColumn | null,
  direction: 'asc' | 'desc'
): Alert[] {
  if (column === null) return alerts;
  const sorted = [...alerts].sort((a, b) => {
    const aVal = a[column];
    const bVal = b[column];
    return aVal.localeCompare(bVal);
  });
  return direction === 'asc' ? sorted : sorted.reverse();
}

export function parseAlertsResponse(data: unknown): Alert[] {
  if (data === null || data === undefined || typeof data !== 'object') return [];
  const obj = data as Record<string, unknown>;
  const alerts = obj['alerts'];
  if (alerts === null || alerts === undefined || typeof alerts !== 'object') return [];
  const alertsObj = alerts as Record<string, unknown>;
  const alertArray = alertsObj['alert'];
  if (!Array.isArray(alertArray)) return [];
  return alertArray as Alert[];
}

export default function AlertsTab({ domainId, solutionId, envId, authFetch }: AlertsTabProps) {
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [textFilter, setTextFilter] = useState('');
  const [levelFilter, setLevelFilter] = useState<AlertLevel | 'ALL'>('ALL');
  const [sortColumn, setSortColumn] = useState<SortableColumn | null>(null);
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');

  const fetchAlerts = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await authFetch(`/${domainId}/${solutionId}/alerts`, undefined, { 'X-Run-As-Environment': 'broker-services' });
      if (!res) { setLoading(false); return; }
      if (!res.ok) {
        setError(`Failed to fetch alerts (${res.status})`);
        setLoading(false);
        return;
      }
      const data = await res.json();
      setAlerts(parseAlertsResponse(data));
    } catch {
      setError('Failed to fetch alerts');
    }
    setLoading(false);
  }, [domainId, solutionId, envId, authFetch]);

  useEffect(() => {
    fetchAlerts();
  }, [fetchAlerts]);

  const handleSort = (column: SortableColumn) => {
    if (sortColumn === column) {
      setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
    } else {
      setSortColumn(column);
      setSortDirection('asc');
    }
  };

  const filtered = filterAlerts(alerts, textFilter, levelFilter);
  const displayed = sortAlerts(filtered, sortColumn, sortDirection);

  if (loading) {
    return <p className="text-gray-500 text-sm">Loading alerts...</p>;
  }

  const levels: AlertLevel[] = ['INFO', 'STATECHANGE', 'EVENT', 'WARNING', 'ERROR'];

  const sortArrow = (column: SortableColumn) => {
    if (sortColumn !== column) return null;
    return sortDirection === 'asc' ? ' ↑' : ' ↓';
  };

  return (
    <div>
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm rounded px-4 py-3 mb-4">
          {error}
        </div>
      )}

      <div className="flex justify-between items-center mb-4">
        <div className="flex gap-3 items-center">
          <input
            type="text"
            value={textFilter}
            onChange={(e) => setTextFilter(e.target.value)}
            placeholder="Filter alerts..."
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          />
          <select
            value={levelFilter}
            onChange={(e) => setLevelFilter(e.target.value as AlertLevel | 'ALL')}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm focus:ring-2 focus:ring-blue-500 focus:outline-none"
          >
            <option value="ALL">All Levels</option>
            {levels.map((level) => (
              <option key={level} value={level}>{level}</option>
            ))}
          </select>
        </div>
        <button
          onClick={fetchAlerts}
          className="px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 border border-gray-300 rounded hover:bg-gray-50"
        >
          Refresh
        </button>
      </div>

      {displayed.length === 0 && !error ? (
        <p className="text-gray-500 text-sm">No alerts found</p>
      ) : (
        <div className="bg-white rounded-lg border border-gray-200">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 border-b border-gray-200">
                <th
                  className="text-left px-4 py-3 font-medium text-gray-700 cursor-pointer select-none"
                  onClick={() => handleSort('level')}
                >
                  Level{sortArrow('level')}
                </th>
                <th
                  className="text-left px-4 py-3 font-medium text-gray-700 cursor-pointer select-none"
                  onClick={() => handleSort('reporter')}
                >
                  Reporter{sortArrow('reporter')}
                </th>
                <th
                  className="text-left px-4 py-3 font-medium text-gray-700 cursor-pointer select-none"
                  onClick={() => handleSort('exchange')}
                >
                  Exchange{sortArrow('exchange')}
                </th>
                <th className="text-left px-4 py-3 font-medium text-gray-700">Description</th>
                <th className="text-left px-4 py-3 font-medium text-gray-700">Message ID</th>
                <th className="text-left px-4 py-3 font-medium text-gray-700">Alert ID</th>
              </tr>
            </thead>
            <tbody>
              {displayed.map((alert) => (
                <tr key={alert.id} className="border-b border-gray-100 last:border-b-0">
                  <td className="px-4 py-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${levelColorClass(alert.level)}`}>
                      {alert.level}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-800">{alert.reporter}</td>
                  <td className="px-4 py-3 text-gray-800">{alert.exchange}</td>
                  <td className="px-4 py-3 text-gray-800">{truncateDescription(alert.description)}</td>
                  <td className="px-4 py-3 text-gray-600">{displayMessageId(alert.messageID)}</td>
                  <td className="px-4 py-3 text-gray-600 font-mono text-xs">{alert.id}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
