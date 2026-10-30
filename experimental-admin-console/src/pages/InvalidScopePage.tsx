import { Link, useSearchParams } from 'react-router-dom';

type Reason = 'missing' | 'not-found';

const messages: Record<Reason, { title: string; description: string }> = {
  missing: {
    title: 'Incomplete URL',
    description:
      'The URL is missing a required domain or solution segment. Please use a complete URL in the format /admin/{domainId}/{solutionId}/login.',
  },
  'not-found': {
    title: 'Invalid Domain or Solution',
    description:
      'The domain or solution specified in the URL does not exist. Please verify the identifiers and try again.',
  },
};

const fallback = {
  title: 'Invalid Scope',
  description:
    'The URL does not contain a valid domain and solution scope. Please check the URL and try again.',
};

export default function InvalidScopePage() {
  const [searchParams] = useSearchParams();
  const reason = searchParams.get('reason') as Reason | null;

  const content = (reason && messages[reason]) || fallback;

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white rounded-lg shadow-md p-8 w-full max-w-sm text-center">
        <div className="text-red-500 mb-4">
          <svg
            className="w-12 h-12 mx-auto"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
            />
          </svg>
        </div>
        <h1 className="text-xl font-semibold text-gray-800 mb-2">
          {content.title}
        </h1>
        <p className="text-sm text-gray-600 mb-6">{content.description}</p>
        <Link
          to="/login"
          className="inline-block w-full bg-blue-600 text-white rounded py-2 text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          Go to Login
        </Link>
      </div>
    </div>
  );
}
