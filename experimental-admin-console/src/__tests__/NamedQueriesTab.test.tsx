import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import NamedQueriesTab from '../components/NamedQueriesTab';

// Mock authFetch
const mockAuthFetch = vi.fn();

// Helper to create mock response
const mockResponse = (status: number, body?: any): Response => {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(typeof body === 'string' ? body : JSON.stringify(body)),
  } as Response;
};

// Sample template data for tests
const sampleTemplates = {
  xQueryResults: {
    xquery: [
      { id: 'StudentGPA', type: 'SINGULAR', status: 'APPROVED', description: 'Returns student GPA' },
      { id: 'AttendanceSummary', type: 'FORMULA', status: 'PENDING', description: 'A very long description that exceeds eighty characters in length to test the truncation functionality properly' },
    ]
  }
};

const sampleDetail = {
  xquery: {
    id: 'StudentGPA',
    type: 'SINGULAR',
    status: 'APPROVED',
    qualifier: 'default',
    description: 'Returns student GPA',
    script: 'SELECT gpa FROM students WHERE id = :studentId',
    parameters: [
      { name: 'studentId', type: 'string', defaultValue: null, description: 'The student identifier', required: true }
    ],
    // An absolute URI, per the schema's format: uri. 'application/json' was a media type, which the
    // broker now rejects on create -- a fixture the real API would never return.
    returnType: 'http://www.sifassociation.org/datamodel/na/3.3:students'
  }
};

const sampleDetailNoParams = {
  xquery: {
    id: 'StudentGPA',
    type: 'SINGULAR',
    status: 'APPROVED',
    qualifier: 'default',
    description: 'Returns student GPA',
    script: 'SELECT gpa FROM students WHERE id = :studentId',
    parameters: [],
    // An absolute URI, per the schema's format: uri. 'application/json' was a media type, which the
    // broker now rejects on create -- a fixture the real API would never return.
    returnType: 'http://www.sifassociation.org/datamodel/na/3.3:students'
  }
};

const defaultProps = {
  domainId: 'test-domain',
  solutionId: 'test-solution',
  authFetch: mockAuthFetch,
};

describe('NamedQueriesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Helper to render with a successful list response
  async function renderWithTemplates() {
    mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleTemplates));
    let result: ReturnType<typeof render>;
    await act(async () => {
      result = render(<NamedQueriesTab {...defaultProps} />);
    });
    return result!;
  }

  // Helper to render with empty state
  async function renderEmpty() {
    mockAuthFetch.mockResolvedValueOnce(mockResponse(204));
    let result: ReturnType<typeof render>;
    await act(async () => {
      result = render(<NamedQueriesTab {...defaultProps} />);
    });
    return result!;
  }

  describe('Template list rendering', () => {
    it('renders correct table columns', async () => {
      await renderWithTemplates();

      expect(screen.getByText('Query Token')).toBeInTheDocument();
      expect(screen.getByText('Type')).toBeInTheDocument();
      expect(screen.getByText('Status')).toBeInTheDocument();
      expect(screen.getByText('Description')).toBeInTheDocument();
      expect(screen.getByText('Actions')).toBeInTheDocument();
    });

    it('renders template data in table rows', async () => {
      await renderWithTemplates();

      expect(screen.getByText('StudentGPA')).toBeInTheDocument();
      expect(screen.getByText('SINGULAR')).toBeInTheDocument();
      expect(screen.getByText('APPROVED')).toBeInTheDocument();
      expect(screen.getByText('Returns student GPA')).toBeInTheDocument();
      expect(screen.getByText('AttendanceSummary')).toBeInTheDocument();
      expect(screen.getByText('FORMULA')).toBeInTheDocument();
      expect(screen.getByText('PENDING')).toBeInTheDocument();
    });

    it('truncates descriptions longer than 80 chars', async () => {
      await renderWithTemplates();

      const longDesc = 'A very long description that exceeds eighty characters in length to test the tru...';
      expect(screen.getByText(longDesc)).toBeInTheDocument();
    });
  });

  describe('Detail panel', () => {
    it('shows detail panel when template row is clicked', async () => {
      await renderWithTemplates();

      mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleDetail));

      await act(async () => {
        fireEvent.click(screen.getByText('StudentGPA'));
      });

      await waitFor(() => {
        expect(screen.getByText('Template Detail')).toBeInTheDocument();
      });

      expect(screen.getByText('default')).toBeInTheDocument();
      expect(screen.getByText('http://www.sifassociation.org/datamodel/na/3.3:students')).toBeInTheDocument();
      expect(screen.getByText('studentId')).toBeInTheDocument();
      expect(screen.getByText('The student identifier')).toBeInTheDocument();
    });

    it('shows "No parameters defined" when template has no parameters', async () => {
      await renderWithTemplates();

      mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleDetailNoParams));

      await act(async () => {
        fireEvent.click(screen.getByText('StudentGPA'));
      });

      await waitFor(() => {
        expect(screen.getByText('No parameters defined')).toBeInTheDocument();
      });
    });

    it('renders script in a pre (monospace) element', async () => {
      await renderWithTemplates();

      mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleDetail));

      await act(async () => {
        fireEvent.click(screen.getByText('StudentGPA'));
      });

      await waitFor(() => {
        const scriptEl = screen.getByText('SELECT gpa FROM students WHERE id = :studentId');
        expect(scriptEl.tagName).toBe('PRE');
      });
    });
  });

  describe('Create form', () => {
    it('shows create form when "Create Template" button is clicked', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      expect(screen.getByText('Create Named Query Template')).toBeInTheDocument();
      expect(screen.getByPlaceholderText('e.g. StudentGPA')).toBeInTheDocument();
    });

    it('submit button is disabled when queryToken is empty', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      // Find the submit button within the form (the second "Create Template" text)
      const submitButtons = screen.getAllByText('Create Template');
      const submitBtn = submitButtons[submitButtons.length - 1];
      expect(submitBtn).toBeDisabled();
    });

    it('submit success (201) closes form and adds template to list', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      // Fill in queryToken
      const tokenInput = screen.getByPlaceholderText('e.g. StudentGPA');
      fireEvent.change(tokenInput, { target: { value: 'NewTemplate' } });

      // Mock successful create response
      mockAuthFetch.mockResolvedValueOnce(mockResponse(201));

      // Submit
      const submitButtons = screen.getAllByText('Create Template');
      const submitBtn = submitButtons[submitButtons.length - 1];

      await act(async () => {
        fireEvent.click(submitBtn);
      });

      await waitFor(() => {
        expect(screen.queryByText('Create Named Query Template')).not.toBeInTheDocument();
      });

      // New template appears in list
      expect(screen.getByText('NewTemplate')).toBeInTheDocument();
    });

    it('submit conflict (409) shows duplicate token error', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      const tokenInput = screen.getByPlaceholderText('e.g. StudentGPA');
      fireEvent.change(tokenInput, { target: { value: 'DuplicateToken' } });

      mockAuthFetch.mockResolvedValueOnce(mockResponse(409));

      const submitButtons = screen.getAllByText('Create Template');
      const submitBtn = submitButtons[submitButtons.length - 1];

      await act(async () => {
        fireEvent.click(submitBtn);
      });

      await waitFor(() => {
        const errors = screen.getAllByText('A template with this query token already exists');
        expect(errors.length).toBeGreaterThanOrEqual(1);
      });
    });

    it('submit validation error (400) shows response error message', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      const tokenInput = screen.getByPlaceholderText('e.g. StudentGPA');
      fireEvent.change(tokenInput, { target: { value: 'BadTemplate' } });

      mockAuthFetch.mockResolvedValueOnce(mockResponse(400, 'Invalid script syntax'));

      const submitButtons = screen.getAllByText('Create Template');
      const submitBtn = submitButtons[submitButtons.length - 1];

      await act(async () => {
        fireEvent.click(submitBtn);
      });

      await waitFor(() => {
        const errors = screen.getAllByText('Invalid script syntax');
        expect(errors.length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe('Delete', () => {
    it('delete confirm calls API and removes template from list', async () => {
      await renderWithTemplates();

      vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
      mockAuthFetch.mockResolvedValueOnce(mockResponse(204));

      const deleteButtons = screen.getAllByTitle('Delete');
      await act(async () => {
        fireEvent.click(deleteButtons[0]);
      });

      await waitFor(() => {
        expect(screen.queryByText('StudentGPA')).not.toBeInTheDocument();
      });

      // Verify API was called with DELETE
      expect(mockAuthFetch).toHaveBeenCalledWith(
        '/test-domain/test-solution/xquerys/StudentGPA',
        { method: 'DELETE' },
        { 'X-Run-As-Environment': 'broker-services' }
      );
    });

    it('delete cancel takes no action', async () => {
      await renderWithTemplates();

      vi.spyOn(window, 'confirm').mockReturnValueOnce(false);

      const deleteButtons = screen.getAllByTitle('Delete');
      fireEvent.click(deleteButtons[0]);

      // Template should still be in list
      expect(screen.getByText('StudentGPA')).toBeInTheDocument();
      // authFetch should only have been called once (for the initial list fetch)
      expect(mockAuthFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe('Empty and error states', () => {
    it('shows empty message when API returns 204', async () => {
      await renderEmpty();

      expect(screen.getByText('No named query templates found')).toBeInTheDocument();
    });

    it('shows error message with status code on API error', async () => {
      mockAuthFetch.mockResolvedValueOnce(mockResponse(500));
      await act(async () => {
        render(<NamedQueriesTab {...defaultProps} />);
      });

      await waitFor(() => {
        expect(screen.getByText('Failed to fetch named query templates (500)')).toBeInTheDocument();
      });
    });
  });

  describe('Refresh', () => {
    it('refresh button re-fetches list', async () => {
      await renderWithTemplates();

      // Setup response for refresh
      mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleTemplates));

      await act(async () => {
        fireEvent.click(screen.getByTitle('Refresh'));
      });

      // Should have been called twice: initial fetch + refresh
      expect(mockAuthFetch).toHaveBeenCalledTimes(2);
    });
  });

  describe('API headers', () => {
    it('all API calls include X-Run-As-Environment: broker-services header', async () => {
      await renderWithTemplates();

      // Verify the initial fetch included the header
      expect(mockAuthFetch).toHaveBeenCalledWith(
        '/test-domain/test-solution/xquerys',
        undefined,
        { 'X-Run-As-Environment': 'broker-services' }
      );

      // Verify detail fetch includes header
      mockAuthFetch.mockResolvedValueOnce(mockResponse(200, sampleDetail));
      await act(async () => {
        fireEvent.click(screen.getByText('StudentGPA'));
      });

      await waitFor(() => {
        expect(mockAuthFetch).toHaveBeenCalledWith(
          '/test-domain/test-solution/xquerys/StudentGPA',
          undefined,
          { 'X-Run-As-Environment': 'broker-services' }
        );
      });
    });
  });

  describe('Status badges', () => {
    it('APPROVED status has green color classes', async () => {
      await renderWithTemplates();

      const approvedBadge = screen.getByText('APPROVED');
      expect(approvedBadge.className).toContain('green');
    });

    it('PENDING status has yellow color classes', async () => {
      await renderWithTemplates();

      const pendingBadge = screen.getByText('PENDING');
      expect(pendingBadge.className).toContain('yellow');
    });
  });

  describe('Parameter management', () => {
    it('add parameter appends new row; remove parameter removes row', async () => {
      await renderWithTemplates();

      fireEvent.click(screen.getByText('Create Template'));

      // Add a parameter
      fireEvent.click(screen.getByText('+ Add Parameter'));

      // Should see Name placeholder inputs
      const nameInputs = screen.getAllByPlaceholderText('Name');
      expect(nameInputs).toHaveLength(1);

      // Add another parameter
      fireEvent.click(screen.getByText('+ Add Parameter'));
      expect(screen.getAllByPlaceholderText('Name')).toHaveLength(2);

      // Remove first parameter
      const removeButtons = screen.getAllByTitle('Remove parameter');
      fireEvent.click(removeButtons[0]);
      expect(screen.getAllByPlaceholderText('Name')).toHaveLength(1);
    });
  });
});
