import type React from 'react';

interface PlaceholderModalProps {
  destination: string | null;
  onClose: () => void;
}

export const PlaceholderModal: React.FC<PlaceholderModalProps> = ({ destination, onClose }) => {
  if (!destination) return null;

  const getDetails = (dest: string) => {
    switch (dest) {
      case 'My QR Passes':
        return {
          title: 'Individual QR Passes',
          description:
            'Each booked player is issued a cryptographically secure, unpredictable QR code. Facility staff scan this code within 10 minutes of slot commencement to verify attendance and avoid credit forfeiture.',
          status: 'Feature in progress · Connected to upcoming bookings',
        };
      case 'Equipment':
        return {
          title: 'Equipment Rental & Requests',
          description:
            'Students can request racquets and tournament shuttlecocks from the sports desk prior to their scheduled slot, subject to inventory availability.',
          status: 'Planned integration · Backend inventory service required',
        };
      case 'Tournaments':
        return {
          title: 'Campus Tournaments & League Matches',
          description:
            'University sports administrators schedule tournaments that reserve and block specific courts across the four facilities.',
          status: 'Admin feature placeholder · View-only',
        };
      case 'Rules & Queue':
        return {
          title: 'Fair Queue & Booking Regulations',
          description:
            'Morning queue opens at 9:00 PM previous day (booking opens 9:05 PM). 60-second turn window per user. Students receive 6 credits weekly. Deduct 1 credit per booking. Zero credits reduces priority. 10-minute check-in grace period.',
          status: 'Policy documentation',
        };
      default:
        return {
          title: dest,
          description: 'This section is planned per project specifications.',
          status: 'Placeholder',
        };
    }
  };

  const details = getDetails(destination);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-xs">
      <div className="w-full max-w-md rounded-xl bg-white p-4.5 sm:p-5 shadow-lg border border-slate-200/80">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
          <h3 className="text-sm font-bold text-slate-900">{details.title}</h3>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded focus-visible:outline-hidden focus-visible:ring-1 focus-visible:ring-slate-400"
            aria-label="Close dialog"
          >
            ✕
          </button>
        </div>

        <div className="my-3 space-y-2.5 text-xs text-slate-600 leading-relaxed">
          <p>{details.description}</p>
          <div className="rounded-lg bg-slate-50 border border-slate-200/60 p-2 text-slate-500 text-[11px]">
            <span className="font-semibold text-slate-700">Notice: </span>
            {details.status}
          </div>
        </div>

        <div className="flex justify-end pt-1">
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-8.5 items-center justify-center rounded-lg bg-slate-900 px-4 text-xs font-semibold text-white hover:bg-slate-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-900"
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    </div>
  );
};
