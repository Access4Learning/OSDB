import { useEffect, useRef, useState, useCallback } from 'react';
import { useAuth } from '../context/AuthContext';

interface Props {
  domainId: string;
  solutionId: string;
  queueId: string;
  queueName: string;
  onClose: () => void;
}

interface DataPoint {
  time: string;
  count: number;
}

export default function LiveQueueChart({ domainId, solutionId, queueId, queueName, onClose }: Props) {
  const { apiFetch } = useAuth();
  const [data, setData] = useState<DataPoint[]>([]);
  const [paused, setPaused] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const poll = useCallback(async () => {
    try {
      const res = await apiFetch(`/${domainId}/${solutionId}/queues/${queueId}`, {
        headers: { 'X-Run-As-Environment': 'broker-services' },
      });
      if (!res.ok) return;
      const q = await res.json();
      const count = q.queue?.messageCount ?? 0;
      const now = new Date().toLocaleTimeString();
      setData((prev) => {
        const next = [...prev, { time: now, count }];
        return next.slice(-30);
      });
    } catch {
      // ignore poll errors
    }
  }, [apiFetch, domainId, solutionId, queueId]);

  useEffect(() => {
    poll(); // initial
    intervalRef.current = setInterval(() => {
      if (!paused) poll();
    }, 2000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [poll, paused]);

  // SVG chart dimensions
  const W = 600, H = 200, PAD = 40;

  const maxCount = Math.max(1, ...data.map((d) => d.count));
  const points = data.map((d, i) => {
    const x = PAD + (i / Math.max(1, data.length - 1)) * (W - PAD * 2);
    const y = H - PAD - (d.count / maxCount) * (H - PAD * 2);
    return { x, y, ...d };
  });
  const linePath = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${p.x} ${p.y}`).join(' ');

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl w-[700px] max-w-[90vw] p-6">
        <h2 className="text-lg font-semibold text-gray-800 mb-4">{queueName} — Live Messages</h2>

        <div className="border border-gray-200 rounded bg-gray-50 p-2 mb-4">
          <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-48">
            {/* Y axis */}
            <line x1={PAD} y1={PAD / 2} x2={PAD} y2={H - PAD} stroke="#ccc" />
            {/* X axis */}
            <line x1={PAD} y1={H - PAD} x2={W - PAD} y2={H - PAD} stroke="#ccc" />
            {/* Y label */}
            <text x={4} y={H / 2} fontSize="10" fill="#666" transform={`rotate(-90, 12, ${H / 2})`}>Messages</text>
            {/* Max label */}
            <text x={PAD - 4} y={PAD / 2 + 4} fontSize="9" fill="#999" textAnchor="end">{maxCount}</text>
            <text x={PAD - 4} y={H - PAD + 4} fontSize="9" fill="#999" textAnchor="end">0</text>
            {/* Line */}
            {points.length > 1 && <path d={linePath} fill="none" stroke="#3b82f6" strokeWidth="2" />}
            {/* Dots */}
            {points.map((p, i) => <circle key={i} cx={p.x} cy={p.y} r="3" fill="#3b82f6" />)}
            {/* X labels (first and last) */}
            {points.length > 0 && (
              <>
                <text x={points[0].x} y={H - PAD + 14} fontSize="8" fill="#999" textAnchor="middle">{points[0].time}</text>
                <text x={points[points.length - 1].x} y={H - PAD + 14} fontSize="8" fill="#999" textAnchor="middle">{points[points.length - 1].time}</text>
              </>
            )}
            {/* No data message */}
            {data.length === 0 && (
              <text x={W / 2} y={H / 2} fontSize="12" fill="#999" textAnchor="middle">Waiting for data...</text>
            )}
          </svg>
        </div>

        <div className="flex gap-3 justify-end">
          <button
            onClick={() => setPaused((p) => !p)}
            className="border border-gray-300 text-gray-700 text-sm font-medium px-4 py-2 rounded hover:bg-gray-50"
          >
            {paused ? 'Resume' : 'Pause'}
          </button>
          <button
            onClick={onClose}
            className="bg-blue-600 text-white text-sm font-medium px-4 py-2 rounded hover:bg-blue-700"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
