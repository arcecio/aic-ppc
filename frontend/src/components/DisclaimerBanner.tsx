import { AlertTriangle } from 'lucide-react';

/**
 * Prominent advisory disclaimer required on every screen (SOW 2.2.9): results are
 * advisory only, do not constitute Formal Plan Check approval, and final
 * determinations are made by City staff. Also serves the AI-interaction
 * notification (SOW 4.2.3).
 */
export function DisclaimerBanner() {
  return (
    <div
      role="note"
      className="flex items-start gap-2 border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-900"
    >
      <AlertTriangle className="mt-0.5 h-4 w-4 flex-shrink-0" aria-hidden="true" />
      <p>
        <strong>Advisory only.</strong> This AI-assisted tool does not perform Formal Plan Check and does
        not issue permits. Final determinations are made by City of Los Angeles staff. Prefer to speak with
        staff? Contact LADBS Development Services.
      </p>
    </div>
  );
}
