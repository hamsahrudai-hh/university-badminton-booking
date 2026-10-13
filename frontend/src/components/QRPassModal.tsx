import type React from 'react';
import { QRCodeSVG } from 'qrcode.react';
import type { UpcomingBooking } from '../types/booking';

interface QRPassModalProps {
  booking: UpcomingBooking | null;
  onClose: () => void;
  onOpenStaffScanner?: (token: string) => void;
}

export const QRPassModal: React.FC<QRPassModalProps> = ({
  booking,
  onClose,
  onOpenStaffScanner,
}) => {
  if (!booking) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4 backdrop-blur-xs">
      <div className="w-full max-w-lg rounded-xl bg-white p-4.5 sm:p-5 shadow-lg border border-slate-200/80 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
          <div>
            <h3 className="text-sm sm:text-base font-bold text-slate-900">Player QR Check-in Passes</h3>
            <p className="text-[11px] text-slate-500">{booking.courtName} · {booking.slot.label}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-400 hover:text-slate-600 p-1 rounded focus-visible:outline-hidden focus-visible:ring-1 focus-visible:ring-slate-400 cursor-pointer"
            aria-label="Close passes modal"
          >
            ✕
          </button>
        </div>

        {/* Policy Alert */}
        <div className="my-3 rounded-lg border border-amber-200/80 bg-amber-50/70 p-2.5 text-xs text-amber-900">
          <p className="font-semibold text-[11px]">Attendance Verification Rule:</p>
          <p className="text-[10px] mt-0.5 leading-normal">
            Facility staff must scan each player&apos;s individual QR code within 10 minutes of slot start time. Unscanned passes forfeit all remaining weekly credits for that player.
          </p>
        </div>

        {/* Individual Player Passes */}
        <div className="space-y-3">
          {booking.players.map((player) => (
            <div
              key={player.studentId}
              className="rounded-lg border border-slate-200 p-3 flex flex-col sm:flex-row items-center gap-3.5 bg-slate-50/50"
            >
              {/* Real Scannable QR Code */}
              <div className="flex shrink-0 items-center justify-center rounded-lg bg-white p-2 border border-slate-200 shadow-2xs">
                <QRCodeSVG
                  value={player.qrToken}
                  size={88}
                  level="M"
                  marginSize={1}
                  fgColor="#0f172a"
                  bgColor="#ffffff"
                  title={`Check-in QR Pass for ${player.name}`}
                />
              </div>

              <div className="space-y-1 text-center sm:text-left flex-1">
                <div className="flex items-center justify-center sm:justify-start gap-1.5">
                  <span className="text-xs sm:text-sm font-bold text-slate-900">{player.name}</span>
                  <span className="rounded bg-emerald-100 px-1.5 py-0.2 text-[9px] font-bold text-emerald-800">
                    Spot {player.spotNumber}
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 font-mono">{player.admissionNumber}</p>
                <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2 pt-0.5">
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[9px] font-semibold ${
                      player.checkedIn
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    <span
                      className={`h-1.5 w-1.5 rounded-full ${
                        player.checkedIn ? 'bg-emerald-600' : 'bg-amber-600'
                      }`}
                    />
                    {player.checkedIn ? 'Checked In' : 'Awaiting On-Court Scan'}
                  </span>
                  <span className="font-mono text-[9px] text-slate-400 select-all" title="Raw QR Token Value">
                    {player.qrToken}
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-slate-100 pt-3">
          {onOpenStaffScanner && (
            <div className="flex items-center gap-1.5">
              <span className="text-[10px] text-slate-400">Staff verification:</span>
              <button
                type="button"
                onClick={() => onOpenStaffScanner(booking.players[0]?.qrToken || '')}
                className="text-[11px] font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 px-2 py-1 rounded transition cursor-pointer"
              >
                Open Staff Check-in Scanner ↗
              </button>
            </div>
          )}
          <button
            type="button"
            onClick={onClose}
            className="inline-flex h-8.5 items-center justify-center rounded-lg bg-slate-900 px-4 text-xs font-semibold text-white hover:bg-slate-800 transition focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-slate-900 cursor-pointer ml-auto"
          >
            Close Pass Preview
          </button>
        </div>
      </div>
    </div>
  );
};
