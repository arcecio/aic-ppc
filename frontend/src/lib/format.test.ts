import { describe, it, expect } from 'vitest';
import { readinessLabel, readinessClass, severityClass, confidenceClass, bytes } from './format';

describe('format helpers', () => {
  it('maps readiness statuses to human labels', () => {
    expect(readinessLabel('READY_FOR_SUBMISSION')).toBe('Ready for Submission');
    expect(readinessLabel('REQUIRES_ATTENTION')).toBe('Requires Attention');
    expect(readinessLabel('INCOMPLETE')).toBe('Incomplete');
    expect(readinessLabel(null)).toBe('Not Assessed');
    expect(readinessLabel(undefined)).toBe('Not Assessed');
  });

  it('gives distinct badge classes per readiness (not color-only reliance)', () => {
    expect(readinessClass('READY_FOR_SUBMISSION')).toContain('green');
    expect(readinessClass('INCOMPLETE')).toContain('red');
    expect(readinessClass('REQUIRES_ATTENTION')).toContain('amber');
  });

  it('maps severities to badge classes', () => {
    expect(severityClass('BLOCKING')).toContain('red');
    expect(severityClass('WARNING')).toContain('amber');
    expect(severityClass('INFORMATIONAL')).toContain('sky');
  });

  it('maps confidence levels', () => {
    expect(confidenceClass('HIGH')).toContain('green');
    expect(confidenceClass('MEDIUM')).toContain('amber');
    expect(confidenceClass('LOW')).toContain('slate');
  });

  it('formats byte sizes', () => {
    expect(bytes(500)).toBe('500 B');
    expect(bytes(2048)).toBe('2 KB');
    expect(bytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
