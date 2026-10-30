import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import {
  AclEntry,
  ActiveSubscription,
  MessageType,
  getEligibleAclEntries,
  getEligibleServiceNames,
  getEligibleZoneIds,
  getEligibleContextIds,
} from '../utils/subscriptionFilterUtils';

export type AuthFetchFn = (
  path: string,
  opts?: RequestInit,
  extraHeaders?: Record<string, string>
) => Promise<Response | null>;

export interface Subscription {
  id: string;
  queueId: string;
  serviceName: string;
  serviceType: string;
  zoneId: string | null;
  contextId: string | null;
  messageType: string;
  createdAt?: string;
}

export interface UseSubscriptionDropdownsResult {
  loading: boolean;
  error: string | null;
  messageType: MessageType;
  serviceNames: string[];
  zoneIds: string[];
  contextIds: string[];
  selectedService: string | null;
  selectedZone: string | null;
  selectedContext: string | null;
  serviceType: string;
  isFormComplete: boolean;
  isSubmitting: boolean;
  setMessageType: (type: MessageType) => void;
  setSelectedService: (service: string) => void;
  setSelectedZone: (zone: string) => void;
  setSelectedContext: (context: string) => void;
  setServiceType: (type: string) => void;
  submitSubscription: () => Promise<void>;
  onSubscriptionDeleted: (sub: ActiveSubscription) => void;
}

export function useSubscriptionDropdowns(
  queueId: string,
  authFetch: AuthFetchFn,
  domainId: string,
  solutionId: string,
  envId: string,
  existingSubscriptions: Subscription[]
): UseSubscriptionDropdownsResult {
  const [aclEntries, setAclEntries] = useState<AclEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [messageType, setMessageTypeState] = useState<MessageType>('EVENT');
  const [selectedService, setSelectedServiceState] = useState<string | null>(null);
  const [selectedZone, setSelectedZoneState] = useState<string | null>(null);
  const [selectedContext, setSelectedContextState] = useState<string | null>(null);
  const [serviceType, setServiceType] = useState('OBJECT');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const [activeSubscriptions, setActiveSubscriptions] = useState<ActiveSubscription[]>(() =>
    existingSubscriptions.map((sub) => ({
      serviceName: sub.serviceName,
      zoneId: sub.zoneId,
      contextId: sub.contextId,
      messageType: sub.messageType,
    }))
  );

  // Keep activeSubscriptions in sync when existingSubscriptions prop changes
  const existingSubsRef = useRef(existingSubscriptions);
  useEffect(() => {
    if (existingSubsRef.current !== existingSubscriptions) {
      existingSubsRef.current = existingSubscriptions;
      setActiveSubscriptions(
        existingSubscriptions.map((sub) => ({
          serviceName: sub.serviceName,
          zoneId: sub.zoneId,
          contextId: sub.contextId,
          messageType: sub.messageType,
        }))
      );
    }
  }, [existingSubscriptions]);

  // Fetch ACL entries on mount or when queueId changes
  useEffect(() => {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000);

    const fetchAclEntries = async () => {
      setLoading(true);
      setError(null);

      try {
        const res = await authFetch(
          `/${domainId}/${solutionId}/acl-entries?environmentId=${envId}`,
          { signal: controller.signal },
          { 'X-Run-As-Environment': 'broker-services' }
        );

        clearTimeout(timeoutId);

        if (!res) {
          setError('Could not load ACL entries');
          setLoading(false);
          return;
        }

        if (!res.ok) {
          setError(`Could not load ACL entries (${res.status})`);
          setLoading(false);
          return;
        }

        const data = await res.json();
        const entries: AclEntry[] = data.aclEntries || data || [];
        setAclEntries(entries);
        setLoading(false);
      } catch (err: unknown) {
        clearTimeout(timeoutId);
        if (err instanceof Error && err.name === 'AbortError') {
          setError('Request timed out while loading ACL entries');
        } else {
          setError('Could not load ACL entries');
        }
        setLoading(false);
      }
    };

    fetchAclEntries();

    return () => {
      clearTimeout(timeoutId);
      controller.abort();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queueId, domainId, solutionId, envId]);

  // Compute eligible entries for current message type
  const eligibleEntries = useMemo(
    () => getEligibleAclEntries(aclEntries, messageType),
    [aclEntries, messageType]
  );

  // Compute dropdown options
  const serviceNames = useMemo(
    () => getEligibleServiceNames(eligibleEntries, activeSubscriptions, messageType),
    [eligibleEntries, activeSubscriptions, messageType]
  );

  const zoneIds = useMemo(
    () =>
      selectedService
        ? getEligibleZoneIds(eligibleEntries, selectedService, activeSubscriptions, messageType)
        : [],
    [eligibleEntries, selectedService, activeSubscriptions, messageType]
  );

  const contextIds = useMemo(
    () =>
      selectedService && selectedZone !== null
        ? getEligibleContextIds(eligibleEntries, selectedService, selectedZone, activeSubscriptions, messageType)
        : [],
    [eligibleEntries, selectedService, selectedZone, activeSubscriptions, messageType]
  );

  // Cascading reset: message type change clears all selections
  const setMessageType = useCallback((type: MessageType) => {
    setMessageTypeState(type);
    setSelectedServiceState(null);
    setSelectedZoneState(null);
    setSelectedContextState(null);
  }, []);

  // Cascading reset: service change clears zone + context
  const setSelectedService = useCallback((service: string) => {
    setSelectedServiceState(service);
    setSelectedZoneState(null);
    setSelectedContextState(null);
  }, []);

  // Cascading reset: zone change clears context
  const setSelectedZone = useCallback((zone: string) => {
    setSelectedZoneState(zone);
    setSelectedContextState(null);
  }, []);

  const setSelectedContext = useCallback((context: string) => {
    setSelectedContextState(context);
  }, []);

  const isFormComplete = selectedService !== null && selectedZone !== null && selectedContext !== null;

  // Submit subscription creation
  const submitSubscription = useCallback(async () => {
    if (!isFormComplete || isSubmitting) return;

    setIsSubmitting(true);
    setError(null);

    try {
      const body = {
        subscription: {
          queueId,
          serviceName: selectedService,
          serviceType,
          zoneId: selectedZone || null,
          contextId: selectedContext || null,
          messageType,
        },
      };

      const res = await authFetch(
        `/${domainId}/${solutionId}/subscriptions/subscription`,
        {
          method: 'POST',
          body: JSON.stringify(body),
        },
        { 'X-Run-As-Environment': 'broker-services' }
      );

      if (!res) {
        setError('Request could not be completed');
        setIsSubmitting(false);
        return;
      }

      if (!res.ok) {
        let errorMsg = `Subscription creation failed (${res.status})`;
        try {
          const errData = await res.json();
          if (errData.message || errData.error) {
            errorMsg = errData.message || errData.error;
          }
        } catch {
          // Use default error message
        }
        setError(errorMsg);
        setIsSubmitting(false);
        return;
      }

      // On success: add to active subscriptions and reset selections
      const newSub: ActiveSubscription = {
        serviceName: selectedService!,
        zoneId: selectedZone || null,
        contextId: selectedContext || null,
        messageType,
      };
      setActiveSubscriptions((prev) => [...prev, newSub]);
      setSelectedServiceState(null);
      setSelectedZoneState(null);
      setSelectedContextState(null);
      setIsSubmitting(false);
    } catch {
      setError('Request could not be completed');
      setIsSubmitting(false);
    }
  }, [
    isFormComplete,
    isSubmitting,
    queueId,
    selectedService,
    serviceType,
    selectedZone,
    selectedContext,
    messageType,
    authFetch,
    domainId,
    solutionId,
  ]);

  // Remove subscription from local active list
  const onSubscriptionDeleted = useCallback((sub: ActiveSubscription) => {
    setActiveSubscriptions((prev) =>
      prev.filter(
        (s) =>
          !(
            s.serviceName === sub.serviceName &&
            s.zoneId === sub.zoneId &&
            s.contextId === sub.contextId &&
            s.messageType === sub.messageType
          )
      )
    );
  }, []);

  return {
    loading,
    error,
    messageType,
    serviceNames,
    zoneIds,
    contextIds,
    selectedService,
    selectedZone,
    selectedContext,
    serviceType,
    isFormComplete,
    isSubmitting,
    setMessageType,
    setSelectedService,
    setSelectedZone,
    setSelectedContext,
    setServiceType,
    submitSubscription,
    onSubscriptionDeleted,
  };
}
