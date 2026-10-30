import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import {
  truncateDescription,
  displayMessageId,
  levelColorClass,
  filterAlerts,
  sortAlerts,
  AlertLevel,
  Exchange,
  Alert,
  SortableColumn,
} from './AlertsTab';

// Feature: admin-environment-alerts-ui, Property 2: Description truncation rule
describe('Property 2: Description truncation rule', () => {
  /** Validates: Requirements 3.5 */
  it('strings ≤50 chars unchanged; >50 chars truncated to first 50 + "…"', () => {
    fc.assert(
      fc.property(fc.string({ minLength: 0, maxLength: 200 }), (input) => {
        const result = truncateDescription(input);
        if (input.length <= 50) {
          expect(result).toBe(input);
        } else {
          expect(result).toBe(input.slice(0, 50) + '\u2026');
          expect(result.length).toBe(51);
        }
      }),
      { numRuns: 100 }
    );
  });

  it('null input returns empty string', () => {
    expect(truncateDescription(null)).toBe('');
  });
});

// Feature: admin-environment-alerts-ui, Property 3: MessageID display rule
describe('Property 3: MessageID display rule', () => {
  /** Validates: Requirements 3.6, 3.7 */
  it('null, undefined, or empty → "-"; non-empty → unchanged', () => {
    fc.assert(
      fc.property(
        fc.option(fc.string({ minLength: 0, maxLength: 50 }), { nil: null }),
        (messageId) => {
          const result = displayMessageId(messageId);
          if (messageId === null || messageId === '') {
            expect(result).toBe('-');
          } else {
            expect(result).toBe(messageId);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('undefined returns "-"', () => {
    expect(displayMessageId(undefined)).toBe('-');
  });
});

// Feature: admin-environment-alerts-ui, Property 4: Level color mapping completeness
describe('Property 4: Level color mapping completeness', () => {
  /** Validates: Requirements 4.1, 4.2, 4.3, 4.4, 4.5 */
  it('levelColorClass returns non-empty string with expected Tailwind bg class for every AlertLevel', () => {
    const levels: AlertLevel[] = ['INFO', 'STATECHANGE', 'EVENT', 'WARNING', 'ERROR'];
    const expectedBgClass: Record<AlertLevel, string> = {
      ERROR: 'bg-red-100',
      WARNING: 'bg-yellow-100',
      INFO: 'bg-blue-100',
      STATECHANGE: 'bg-green-100',
      EVENT: 'bg-gray-100',
    };

    const arbLevel = fc.constantFrom(...levels);

    fc.assert(
      fc.property(arbLevel, (level) => {
        const result = levelColorClass(level);
        expect(result).toBeTruthy();
        expect(result.length).toBeGreaterThan(0);
        expect(result).toContain(expectedBgClass[level]);
      }),
      { numRuns: 100 }
    );
  });
});

// Feature: admin-environment-alerts-ui, Property 5: Combined filter correctness
describe('Property 5: Combined filter correctness', () => {
  const alertLevels: AlertLevel[] = ['INFO', 'STATECHANGE', 'EVENT', 'WARNING', 'ERROR'];
  const exchanges: Exchange[] = ['REQUEST', 'RESPONSE', 'EVENT', 'TIMEOUT', 'OTHER'];

  const alertArb = fc.record({
    id: fc.uuid(),
    reporter: fc.string({ minLength: 1, maxLength: 30 }),
    cause: fc.option(fc.string({ maxLength: 20 }), { nil: null }),
    exchange: fc.constantFrom(...exchanges),
    level: fc.constantFrom(...alertLevels),
    description: fc.string({ minLength: 1, maxLength: 100 }),
    messageID: fc.option(fc.string({ minLength: 1, maxLength: 30 }), { nil: null }),
    body: fc.constant(null),
    error: fc.constant(null),
    xpath: fc.constant(null),
    category: fc.constant(null),
    code: fc.constant(null),
    internal: fc.constant(null),
  });

  const levelFilterArb = fc.constantFrom<AlertLevel | 'ALL'>(...alertLevels, 'ALL');

  function matchesTextFilter(alert: Alert, textFilter: string): boolean {
    if (textFilter === '') return true;
    const lower = textFilter.toLowerCase();
    return (
      alert.reporter.toLowerCase().includes(lower) ||
      alert.description.toLowerCase().includes(lower) ||
      alert.exchange.toLowerCase().includes(lower) ||
      (alert.messageID ?? '').toLowerCase().includes(lower)
    );
  }

  function matchesLevelFilter(alert: Alert, levelFilter: AlertLevel | 'ALL'): boolean {
    return levelFilter === 'ALL' || alert.level === levelFilter;
  }

  it('every returned alert matches both filters and no excluded alert satisfies both', () => {
    // **Validates: Requirements 5.1, 5.3, 5.4**
    fc.assert(
      fc.property(
        fc.array(alertArb, { minLength: 0, maxLength: 20 }),
        fc.string({ minLength: 0, maxLength: 15 }),
        levelFilterArb,
        (alerts, textFilter, levelFilter) => {
          const result = filterAlerts(alerts, textFilter, levelFilter);

          // Every returned alert matches both filters
          for (const alert of result) {
            if (!matchesLevelFilter(alert, levelFilter)) return false;
            if (!matchesTextFilter(alert, textFilter)) return false;
          }

          // No excluded alert satisfies both filter criteria
          const resultIds = new Set(result.map((a) => a.id));
          for (const alert of alerts) {
            if (!resultIds.has(alert.id)) {
              if (matchesLevelFilter(alert, levelFilter) && matchesTextFilter(alert, textFilter)) {
                return false;
              }
            }
          }

          return true;
        }
      ),
      { numRuns: 100 }
    );
  });
});

// Feature: admin-environment-alerts-ui, Property 6: Sort ordering
describe('Property 6: Sort ordering', () => {
  const alertLevels = ['INFO', 'STATECHANGE', 'EVENT', 'WARNING', 'ERROR'] as const;
  const exchanges = ['REQUEST', 'RESPONSE', 'EVENT', 'TIMEOUT', 'OTHER'] as const;

  const alertArb = fc.record({
    id: fc.uuid(),
    reporter: fc.string({ minLength: 1, maxLength: 30 }),
    cause: fc.constant(null),
    exchange: fc.constantFrom(...exchanges),
    level: fc.constantFrom(...alertLevels),
    description: fc.string({ minLength: 1, maxLength: 100 }),
    messageID: fc.constant(null),
    body: fc.constant(null),
    error: fc.constant(null),
    xpath: fc.constant(null),
    category: fc.constant(null),
    code: fc.constant(null),
    internal: fc.constant(null),
  });

  it('ascending sort produces elements with sort key ≤ next; descending ≥ next', () => {
    // **Validates: Requirements 6.1, 6.2, 6.3**
    fc.assert(
      fc.property(
        fc.array(alertArb),
        fc.constantFrom<SortableColumn>('level', 'reporter', 'exchange'),
        fc.constantFrom<'asc' | 'desc'>('asc', 'desc'),
        (alerts, column, direction) => {
          const sorted = sortAlerts(alerts as Alert[], column, direction);
          for (let i = 0; i < sorted.length - 1; i++) {
            const cmp = sorted[i][column].localeCompare(sorted[i + 1][column]);
            if (direction === 'asc') {
              if (cmp > 0) return false;
            } else {
              if (cmp < 0) return false;
            }
          }
          return true;
        }
      ),
      { numRuns: 100 }
    );
  });
});


// Component unit tests for AlertsTab
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, beforeEach } from 'vitest';
import AlertsTab from './AlertsTab';

const DOMAIN_ID = 'domain-1';
const SOLUTION_ID = 'solution-1';
const ENV_ID = 'env-1';

const mockAlertsResponse = {
  alerts: {
    alert: [
      {
        id: 'uuid-1',
        reporter: 'TestApp',
        cause: null,
        exchange: 'REQUEST',
        level: 'ERROR',
        description: 'Something went wrong',
        messageID: 'msg-123',
        body: null,
        error: null,
        xpath: null,
        category: null,
        code: null,
        internal: null,
      },
      {
        id: 'uuid-2',
        reporter: 'OtherApp',
        cause: null,
        exchange: 'EVENT',
        level: 'WARNING',
        description: 'Disk space low',
        messageID: null,
        body: null,
        error: null,
        xpath: null,
        category: null,
        code: null,
        internal: null,
      },
      {
        id: 'uuid-3',
        reporter: 'InfoService',
        cause: null,
        exchange: 'RESPONSE',
        level: 'INFO',
        description: 'Sync completed',
        messageID: 'msg-456',
        body: null,
        error: null,
        xpath: null,
        category: null,
        code: null,
        internal: null,
      },
    ],
  },
};

function createMockAuthFetch(response?: Response | null) {
  return vi.fn(async (_path: string, _opts?: RequestInit, _extraHeaders?: Record<string, string>): Promise<Response | null> => {
    return response ?? new Response(JSON.stringify(mockAlertsResponse), { status: 200 });
  });
}

function renderAlertsTab(authFetch = createMockAuthFetch()) {
  return {
    authFetch,
    ...render(
      <AlertsTab
        domainId={DOMAIN_ID}
        solutionId={SOLUTION_ID}
        envId={ENV_ID}
        authFetch={authFetch}
      />
    ),
  };
}

describe('AlertsTab Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading indicator during fetch', () => {
    const authFetch = vi.fn(() => new Promise<Response | null>(() => {}));
    render(
      <AlertsTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    expect(screen.getByText(/loading alerts/i)).toBeInTheDocument();
  });

  it('displays error message with status code on API failure', async () => {
    const authFetch = createMockAuthFetch(new Response(JSON.stringify({}), { status: 500 }));
    render(
      <AlertsTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText(/failed to fetch alerts \(500\)/i)).toBeInTheDocument();
    });
  });

  it('shows "No alerts found" empty state', async () => {
    const emptyResponse = new Response(JSON.stringify({ alerts: { alert: [] } }), { status: 200 });
    const authFetch = createMockAuthFetch(emptyResponse);
    render(
      <AlertsTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText(/no alerts found/i)).toBeInTheDocument();
    });
  });

  it('refresh button triggers re-fetch preserving filters', async () => {
    const authFetch = createMockAuthFetch();
    render(
      <AlertsTab domainId={DOMAIN_ID} solutionId={SOLUTION_ID} envId={ENV_ID} authFetch={authFetch} />
    );

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    // Set text filter
    fireEvent.change(screen.getByPlaceholderText(/filter alerts/i), { target: { value: 'Test' } });

    // Click refresh
    fireEvent.click(screen.getByRole('button', { name: /refresh/i }));

    await waitFor(() => {
      // authFetch called at least twice (initial + refresh)
      expect(authFetch.mock.calls.length).toBeGreaterThanOrEqual(2);
    });

    // Filter still applied after refresh
    expect(screen.getByPlaceholderText(/filter alerts/i)).toHaveValue('Test');
  });

  it('sort direction toggles on column header click', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    const headers = screen.getAllByRole('columnheader');
    const levelHeader = headers[0]; // Level is first column

    // First click → ascending sort
    fireEvent.click(levelHeader);
    expect(levelHeader).toHaveTextContent('Level ↑');

    // Second click → descending sort
    fireEvent.click(levelHeader);
    expect(levelHeader).toHaveTextContent('Level ↓');
  });

  it('arrow indicator visible on active sort column', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    const headers = screen.getAllByRole('columnheader');
    const levelHeader = headers[0];
    const reporterHeader = headers[1];

    // No arrow initially
    expect(levelHeader).toHaveTextContent('Level');
    expect(reporterHeader).toHaveTextContent('Reporter');

    // Click reporter column
    fireEvent.click(reporterHeader);
    expect(reporterHeader).toHaveTextContent('Reporter ↑');

    // Level should not have arrow
    expect(levelHeader).toHaveTextContent('Level');
    expect(levelHeader.textContent).not.toContain('↑');
    expect(levelHeader.textContent).not.toContain('↓');
  });

  it('text filter updates displayed alerts immediately', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    // All 3 alerts visible initially
    expect(screen.getByText('TestApp')).toBeInTheDocument();
    expect(screen.getByText('OtherApp')).toBeInTheDocument();
    expect(screen.getByText('InfoService')).toBeInTheDocument();

    // Type filter that matches only "TestApp"
    fireEvent.change(screen.getByPlaceholderText(/filter alerts/i), { target: { value: 'TestApp' } });

    // Only TestApp visible
    expect(screen.getByText('TestApp')).toBeInTheDocument();
    expect(screen.queryByText('OtherApp')).not.toBeInTheDocument();
    expect(screen.queryByText('InfoService')).not.toBeInTheDocument();
  });

  it('level dropdown filters by selected level', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    // Select WARNING level
    const dropdown = screen.getByDisplayValue('All Levels');
    fireEvent.change(dropdown, { target: { value: 'WARNING' } });

    // Only WARNING alert visible
    expect(screen.getByText('OtherApp')).toBeInTheDocument();
    expect(screen.queryByText('TestApp')).not.toBeInTheDocument();
    expect(screen.queryByText('InfoService')).not.toBeInTheDocument();
  });

  it('combined text + level filter applies logical AND', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    // Set level to ERROR
    fireEvent.change(screen.getByDisplayValue('All Levels'), { target: { value: 'ERROR' } });

    // Set text filter to something that matches no ERROR alerts
    fireEvent.change(screen.getByPlaceholderText(/filter alerts/i), { target: { value: 'OtherApp' } });

    // No alerts match both ERROR level AND "OtherApp" text
    expect(screen.getByText(/no alerts found/i)).toBeInTheDocument();
  });

  it('renders correct columns in order', async () => {
    renderAlertsTab();

    await waitFor(() => {
      expect(screen.getByText('TestApp')).toBeInTheDocument();
    });

    const headers = screen.getAllByRole('columnheader');
    expect(headers).toHaveLength(6);
    expect(headers[0]).toHaveTextContent('Level');
    expect(headers[1]).toHaveTextContent('Reporter');
    expect(headers[2]).toHaveTextContent('Exchange');
    expect(headers[3]).toHaveTextContent('Description');
    expect(headers[4]).toHaveTextContent('Message ID');
    expect(headers[5]).toHaveTextContent('Alert ID');
  });
});
