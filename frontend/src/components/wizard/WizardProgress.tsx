import type React from 'react';
import type { WizardStep } from '../../types/booking';

interface WizardProgressProps {
  currentStep: WizardStep;
  onCancel: () => void;
}

const STEPS = [
  { step: 1, label: 'Time Slot' },
  { step: 2, label: 'Court' },
  { step: 3, label: 'Group & Spots' },
  { step: 4, label: 'Review' },
];

export const WizardProgress: React.FC<WizardProgressProps> = ({ currentStep, onCancel }) => {
  return (
    <div className="border-b border-slate-200/80 bg-white/95 px-4 py-2.5 sm:px-6">
      <div className="mx-auto flex max-w-4xl items-center justify-between">
        {/* Step Indicator */}
        <div className="flex items-center gap-1.5 sm:gap-4">
          {STEPS.map((s, index) => {
            const isCompleted = currentStep > s.step;
            const isCurrent = currentStep === s.step;

            return (
              <div key={s.step} className="flex items-center gap-1.5 sm:gap-2">
                <div className="flex items-center gap-1.5">
                  <div
                    className={`flex h-5.5 w-5.5 items-center justify-center rounded-full text-[10px] font-bold transition ${
                      isCompleted
                        ? 'bg-emerald-700 text-white'
                        : isCurrent
                        ? 'bg-slate-900 text-white shadow-2xs'
                        : 'bg-slate-100 text-slate-400'
                    }`}
                  >
                    {isCompleted ? (
                      <svg className="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="3">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                      </svg>
                    ) : (
                      s.step
                    )}
                  </div>
                  <span
                    className={`hidden sm:inline text-xs font-semibold ${
                      isCurrent
                        ? 'text-slate-900'
                        : isCompleted
                        ? 'text-slate-700'
                        : 'text-slate-400'
                    }`}
                  >
                    {s.label}
                  </span>
                </div>

                {index < STEPS.length - 1 && (
                  <div
                    className={`h-0.5 w-4 sm:w-8 ${
                      currentStep > s.step ? 'bg-emerald-600' : 'bg-slate-200'
                    }`}
                  />
                )}
              </div>
            );
          })}
        </div>

        {/* Exit Wizard Button */}
        <button
          type="button"
          onClick={onCancel}
          className="text-xs font-medium text-slate-500 hover:text-slate-800 transition focus-visible:outline-hidden focus-visible:ring-1 focus-visible:ring-slate-400 rounded px-1.5 py-0.5"
        >
          Exit wizard
        </button>
      </div>
    </div>
  );
};
