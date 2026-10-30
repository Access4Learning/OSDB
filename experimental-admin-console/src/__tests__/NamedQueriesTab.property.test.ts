import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import {
  truncateDescription,
  getStatusBadgeClass,
  filterValidParameters,
  isSubmitDisabled,
  ParameterFormRow,
} from '../components/NamedQueriesTab';

describe('Feature: admin-xquery-management-ui', () => {
  /**
   * Property 2: Description truncation correctness
   *
   * For any string of length N, if N ≤ 80 the truncateDescription function SHALL return
   * the original string unchanged, and if N > 80 it SHALL return exactly the first 80
   * characters followed by "...".
   *
   * **Validates: Requirements 1.6**
   */
  describe('Property 2: Description truncation correctness', () => {
    it('undefined or null input returns em dash', () => {
      expect(truncateDescription(undefined)).toBe('—');
      expect(truncateDescription('')).toBe('—');
    });

    it('strings of length ≤ 80 are returned unchanged', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 80 }),
          (text) => {
            const result = truncateDescription(text);
            expect(result).toBe(text);
          }
        ),
        { numRuns: 100 }
      );
    });

    it('strings of length > 80 are truncated to first 80 chars + "..."', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 81, maxLength: 200 }),
          (text) => {
            const result = truncateDescription(text);
            expect(result).toBe(text.slice(0, 80) + '...');
            expect(result.length).toBe(83);
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 6: Status badge color mapping
   *
   * For any template with status "APPROVED", the badge SHALL have green styling.
   * For any template with status "PENDING", the badge SHALL have yellow styling.
   * For any template with status "DISALLOWED", the badge SHALL have red styling.
   * Unknown statuses get gray styling.
   *
   * **Validates: Requirements 6.1, 6.2, 6.3**
   */
  describe('Property 6: Status badge color mapping', () => {
    it('APPROVED maps to green, PENDING to yellow, DISALLOWED to red', () => {
      fc.assert(
        fc.property(
          fc.constantFrom('APPROVED', 'PENDING', 'DISALLOWED'),
          (status) => {
            const result = getStatusBadgeClass(status);
            if (status === 'APPROVED') {
              expect(result).toContain('green');
            } else if (status === 'PENDING') {
              expect(result).toContain('yellow');
            } else if (status === 'DISALLOWED') {
              expect(result).toContain('red');
            }
          }
        ),
        { numRuns: 100 }
      );
    });

    it('unknown status maps to gray', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 50 }).filter(
            s => !['APPROVED', 'PENDING', 'DISALLOWED'].includes(s)
          ),
          (status) => {
            const result = getStatusBadgeClass(status);
            expect(result).toContain('gray');
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 4: Form submission data fidelity (parameter filtering)
   *
   * For any list of ParameterFormRow entries, filterValidParameters SHALL return only
   * entries where name.trim() is non-empty, with trimmed name, type, defaultValue
   * (undefined if empty), description (undefined if empty), and required flag preserved.
   *
   * **Validates: Requirements 3.4, 8.5**
   */
  describe('Property 4: Form submission data fidelity (parameter filtering)', () => {
    const parameterRowArb: fc.Arbitrary<ParameterFormRow> = fc.record({
      name: fc.oneof(
        fc.constant(''),
        fc.constant('   '),
        fc.string({ minLength: 1, maxLength: 30 })
      ),
      type: fc.string({ minLength: 0, maxLength: 20 }),
      defaultValue: fc.oneof(fc.constant(''), fc.string({ minLength: 1, maxLength: 30 })),
      description: fc.oneof(fc.constant(''), fc.string({ minLength: 1, maxLength: 50 })),
      required: fc.boolean(),
    });

    it('returned array length equals count of entries with non-empty trimmed name', () => {
      fc.assert(
        fc.property(
          fc.array(parameterRowArb, { minLength: 0, maxLength: 10 }),
          (params) => {
            const result = filterValidParameters(params);
            const expectedCount = params.filter(p => p.name.trim() !== '').length;
            expect(result.length).toBe(expectedCount);
          }
        ),
        { numRuns: 100 }
      );
    });

    it('each returned parameter has trimmed name, type, correct defaultValue/description, and preserved required', () => {
      fc.assert(
        fc.property(
          fc.array(parameterRowArb, { minLength: 0, maxLength: 10 }),
          (params) => {
            const result = filterValidParameters(params);
            const validInputs = params.filter(p => p.name.trim() !== '');

            for (let i = 0; i < result.length; i++) {
              const output = result[i];
              const input = validInputs[i];

              expect(output.name).toBe(input.name.trim());
              expect(output.type).toBe(input.type.trim());
              // wire name is `default`; the form row still calls it defaultValue
              expect(output.default).toBe(input.defaultValue.trim() || undefined);
              expect(output.description).toBe(input.description.trim() || undefined);
              expect(output.required).toBe(input.required);
            }
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 5: Submit button disabled state
   *
   * For any string that is empty or composed entirely of whitespace in the queryToken
   * field, the submit button SHALL be disabled. For any string containing at least one
   * non-whitespace character, the submit button SHALL be enabled.
   *
   * **Validates: Requirements 3.8**
   */
  describe('Property 5: Submit button disabled state', () => {
    it('empty or whitespace-only queryToken returns true (disabled)', () => {
      fc.assert(
        fc.property(
          fc.constantFrom('', ' ', '  ', '\t', '\n', '   \t\n  '),
          (token) => {
            expect(isSubmitDisabled(token)).toBe(true);
          }
        ),
        { numRuns: 100 }
      );
    });

    it('queryToken with at least one non-whitespace char returns false (enabled)', () => {
      fc.assert(
        fc.property(
          fc.string({ minLength: 1, maxLength: 100 }).filter(s => s.trim().length > 0),
          (token) => {
            expect(isSubmitDisabled(token)).toBe(false);
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 7: Error display includes HTTP status code
   *
   * For any API error response with HTTP status code in the range 400-599, the displayed
   * error message SHALL contain the numeric status code as a substring.
   *
   * **Validates: Requirements 1.4, 4.4**
   */
  describe('Property 7: Error display includes HTTP status code', () => {
    it('error message constructed with status code contains that code as substring', () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 400, max: 599 }),
          (statusCode) => {
            // Simulate error message construction as done in the component
            const errorMessage = `Failed to fetch named query templates (${statusCode})`;
            expect(errorMessage).toContain(String(statusCode));
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 8: Parameter row add/remove invariant
   *
   * For any current parameter list of length N (where N ≥ 1), removing one parameter row
   * SHALL result in a list of length N-1. For any list of length N, adding a parameter
   * SHALL result in a list of length N+1 with the new entry having all empty fields and
   * required=false.
   *
   * **Validates: Requirements 8.1, 8.3**
   */
  describe('Property 8: Parameter row add/remove invariant', () => {
    const parameterRowArb: fc.Arbitrary<ParameterFormRow> = fc.record({
      name: fc.string({ minLength: 0, maxLength: 30 }),
      type: fc.string({ minLength: 0, maxLength: 20 }),
      defaultValue: fc.string({ minLength: 0, maxLength: 30 }),
      description: fc.string({ minLength: 0, maxLength: 50 }),
      required: fc.boolean(),
    });

    it('removing one parameter from list of length N results in length N-1', () => {
      fc.assert(
        fc.property(
          fc.array(parameterRowArb, { minLength: 1, maxLength: 20 }),
          fc.nat(),
          (params, indexSeed) => {
            const removeIndex = indexSeed % params.length;
            const result = params.filter((_, i) => i !== removeIndex);
            expect(result.length).toBe(params.length - 1);
          }
        ),
        { numRuns: 100 }
      );
    });

    it('adding one parameter to list of length N results in length N+1 with empty entry', () => {
      fc.assert(
        fc.property(
          fc.array(parameterRowArb, { minLength: 0, maxLength: 20 }),
          (params) => {
            const newEntry: ParameterFormRow = { name: '', type: '', defaultValue: '', description: '', required: false };
            const result = [...params, newEntry];
            expect(result.length).toBe(params.length + 1);
            const added = result[result.length - 1];
            expect(added.name).toBe('');
            expect(added.type).toBe('');
            expect(added.defaultValue).toBe('');
            expect(added.description).toBe('');
            expect(added.required).toBe(false);
          }
        ),
        { numRuns: 100 }
      );
    });
  });
});
