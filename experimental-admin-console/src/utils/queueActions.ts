export type AuthFetch = (
  path: string,
  options?: RequestInit,
  extraHeaders?: Record<string, string>
) => Promise<Response | null>;

const BROKER_SERVICES_HEADERS = {
  'X-Run-As-Environment': 'broker-services',
};

async function responseError(action: string, response: Response): Promise<Error> {
  const detail = await response.text();
  return new Error(`${action} failed (${response.status})${detail ? `: ${detail}` : ''}`);
}

export interface PendingQueueMessage {
  messageId: string;
  streamId: string;
  consumerName: string;
  idleTimeMs: number;
  deliveryCount: number;
}

export interface DiscardNextResult {
  discarded: boolean;
  message?: PendingQueueMessage;
}

/** Returns messages currently reserved by any consumer connection. */
export async function fetchPendingQueueMessages(
  authFetch: AuthFetch,
  domainId: string,
  solutionId: string,
  queueId: string
): Promise<PendingQueueMessage[] | null> {
  const response = await authFetch(
    `/${domainId}/${solutionId}/queues/${queueId}/messages/pending?limit=100`,
    undefined,
    BROKER_SERVICES_HEADERS
  );

  if (!response) return null;
  if (!response.ok) throw await responseError('Get pending queue messages', response);
  const data = await response.json();
  return Array.isArray(data.pendingMessages) ? data.pendingMessages : [];
}

/** ACKs and deletes the globally oldest pending message across all consumers. */
export async function discardOldestPendingQueueMessage(
  authFetch: AuthFetch,
  domainId: string,
  solutionId: string,
  queueId: string
): Promise<DiscardNextResult | null> {
  const response = await authFetch(
    `/${domainId}/${solutionId}/queues/${queueId}/messages/pending/oldest`,
    { method: 'DELETE' },
    BROKER_SERVICES_HEADERS
  );

  if (!response) return null;
  if (response.status === 204) return { discarded: false };
  if (!response.ok) throw await responseError('Discard oldest pending queue message', response);
  return { discarded: true, message: await response.json() };
}

/** Starts the queue-service asynchronous purge, which preserves the queue. */
export async function purgeQueueMessages(
  authFetch: AuthFetch,
  domainId: string,
  solutionId: string,
  queueId: string
): Promise<boolean> {
  const response = await authFetch(
    `/${domainId}/${solutionId}/queues/${queueId}/messages`,
    { method: 'DELETE' },
    BROKER_SERVICES_HEADERS
  );

  if (!response) return false;
  if (!response.ok) throw await responseError('Purge queue', response);
  return true;
}
