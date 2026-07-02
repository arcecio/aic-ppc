import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { FindingList } from './FindingList';
import type { Finding } from '../types';

function finding(overrides: Partial<Finding> = {}): Finding {
  return {
    id: 'f1', category: 'ZONING', severity: 'WARNING', title: 'Front setback below minimum',
    description: 'The front yard setback appears below the required 15 ft.', confidence: 80,
    confidenceLevel: 'MEDIUM', source: 'RULE', staffDisposition: 'PENDING',
    applicantFlagged: false, createdAt: '2026-07-01T00:00:00Z',
    codeReference: 'LAMC 12.08-C', ...overrides,
  };
}

describe('FindingList', () => {
  it('renders findings with severity, confidence, and code reference', () => {
    render(<FindingList findings={[finding()]} mode="applicant" />);
    expect(screen.getByText('Front setback below minimum')).toBeInTheDocument();
    expect(screen.getByText('WARNING')).toBeInTheDocument();
    expect(screen.getByText(/Confidence: MEDIUM/)).toBeInTheDocument();
    expect(screen.getByText('LAMC 12.08-C')).toBeInTheDocument();
  });

  it('shows an empty state', () => {
    render(<FindingList findings={[]} mode="applicant" />);
    expect(screen.getByText('No findings identified.')).toBeInTheDocument();
  });

  it('lets an applicant flag a finding as inaccurate', () => {
    const onFlag = vi.fn();
    render(<FindingList findings={[finding()]} mode="applicant" onFlag={onFlag} />);
    fireEvent.click(screen.getByText(/Flag as inaccurate/));
    fireEvent.change(screen.getByLabelText(/why is this inaccurate/i), { target: { value: 'setback is 20 ft' } });
    fireEvent.click(screen.getByText('Send'));
    expect(onFlag).toHaveBeenCalledWith('f1', 'setback is 20 ft');
  });

  it('offers staff accept/modify/reject actions', () => {
    const onReview = vi.fn();
    render(<FindingList findings={[finding()]} mode="staff" onReview={onReview} />);
    fireEvent.click(screen.getByText('Reject'));
    expect(onReview).toHaveBeenCalledWith('f1', 'REJECTED');
  });
});
