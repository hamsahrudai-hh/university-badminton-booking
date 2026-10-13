import React, { useState, useEffect } from 'react';
import type { QueueEntryResponseDto } from '../../api/client';

interface QueueCountdownBannerProps {
  queueEntry: QueueEntryResponseDto | null;
  onExpired: () => void;
}

export const QueueCountdownBanner: React.FC<QueueCountdownBannerProps> = ({
  queueEntry,
  onExpired,
}) => {
  const [secondsRemaining, setSecondsRemaining] = useState<number>(() => {
    if (!queueEntry) return 0;
    if (queueEntry.expiresAt) {
      const diff = Math.round((new Date(queueEntry.expiresAt).getTime() - Date.now()) / 1000);
      return Math.max(0, diff);
    }
    return Math.max(0, queueEntry.remainingSeconds ?? 60);
  });

  useEffect(() => {
    if (!queueEntry || queueEntry.status !== 'ADMITTED') return;

    const interval = window.setInterval(() => {
      let currentLeft = 0;
      if (queueEntry.expiresAt) {
        currentLeft = Math.max(0, Math.round((new Date(queueEntry.expiresAt).getTime() - Date.now()) / 1000));
      } else {
        setSecondsRemaining((prev) => {
          const nextVal = prev - 1;
          if (nextVal <= 0) {
            onExpired();
            return 0;
          }
          return nextVal;
        });
        return;
      }

      setSecondsRemaining(currentLeft);
      if (currentLeft <= 0) {
        window.clearInterval(interval);
        onExpired();
      }
    }, 1000);

    return () => window.clearInterval(interval);
  }, [queueEntry, onExpired]);

  if (!queueEntry || queueEntry.status !== 'ADMITTED') return null;

  const isUrgent = secondsRemaining <= 15;

  return (
    <div
      className={`sticky top-0 z-40 border-b px-4 py-2.5 shadow-xs transition-colors duration-200 ${
        isUrgent
          ? 'bg-rose-50 border-rose-200 text-rose-900 animate-pulse'
          : 'bg-emerald-50 border-emerald-200 text-emerald-950'
      }`}
    >
      <div className="mx-auto max-w-4xl flex items-center justify-between text-xs">
        <div className="flex items-center gap-2">
          <span className="flex h-5 w-5 items-center justify-center rounded-full bg-emerald-600 text-white font-bold text-[10px]">
            ⏱️
          </span>
          <span className="font-semibold">
            Active 60-Second Booking Opportunity ({queueEntry.priorityTier || 'STANDARD'} PRIORITY)
          </span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-slate-600">Time remaining:</span>
          <span
            className={`font-black text-sm tabular-nums px-2 py-0.5 rounded ${
              isUrgent ? 'bg-rose-200 text-rose-900' : 'bg-emerald-200 text-emerald-900'
            }`}
          >
            {secondsRemaining}s
          </span>
        </div>
      </div>
    </div>
  );
};
