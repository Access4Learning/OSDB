import { useParams } from 'react-router-dom';

interface AdminScope {
  domainId: string | undefined;
  solutionId: string | undefined;
  isValid: boolean;
}

export function useAdminScope(): AdminScope {
  const { domainId, solutionId } = useParams<{ domainId: string; solutionId: string }>();
  const isValid = Boolean(domainId && solutionId);
  return { domainId, solutionId, isValid };
}
