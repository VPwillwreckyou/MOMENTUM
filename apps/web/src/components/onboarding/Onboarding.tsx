import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronRight,
  Circle,
  Compass,
  Layers,
  LockKeyhole,
  Moon,
  Plus,
  Settings2,
  Sun,
  Target,
} from 'lucide-react';
import { useMomentum } from '../../state/MomentumContext';
import { Button } from '../ui/Button';
import { Input } from '../ui/Fields';
import { LogoMark } from '../ui/Logo';
import { ACCENT_PRESETS } from '../../lib/defaults';
import { toDateInput } from '../../lib/dates';

const STEPS = 4;

export function Onboarding() {
  const navigate = useNavigate();
  const { settings, updateSettings, finishOnboarding, createGoal, toast } = useMomentum();
  const [step, setStep] = useState(0);
  const [name, setName] = useState(settings.displayName);
  const [goalTitle, setGoalTitle] = useState('');
  const [deadline, setDeadline] = useState('');

  const complete = (createFirstGoal: boolean) => {
    updateSettings({ displayName: name.trim() });
    finishOnboarding();
    if (createFirstGoal && goalTitle.trim()) {
      const goal = createGoal({
        title: goalTitle.trim(),
        deadline: deadline || null,
        description: '',
      });
      toast('Your first goal is live. Add the first move when you are ready.', 'success');
      navigate(`/goals/${goal.id}`);
    } else {
      navigate('/');
    }
  };

  const skip = () => {
    updateSettings({ displayName: name.trim() });
    finishOnboarding();
    navigate('/');
  };

  const next = () => {
    if (step === STEPS - 1) {
      complete(true);
      return;
    }
    setStep((current) => Math.min(STEPS - 1, current + 1));
  };

  return (
    <div className="onboarding">
      <div className="onboarding__ambient" aria-hidden="true">
        <span className="onboarding__beam" />
        <span className="onboarding__grid" />
      </div>

      <aside className="onboarding__rail">
        <div className="onboarding__brand">
          <LogoMark size={34} />
          <span>MOMENTUM</span>
        </div>
        <div className="onboarding__rail-copy">
          <span className="eyebrow">Personal command center</span>
          <h2>Ambition is common.<br />Momentum is designed.</h2>
          <p>A short setup. You can change every part of it later.</p>
        </div>
        <div className="onboarding__steps">
          {Array.from({ length: STEPS }, (_, index) => (
            <button
              type="button"
              key={index}
              className={`onboarding__step${index === step ? ' is-active' : ''}${index < step ? ' is-complete' : ''}`}
              onClick={() => setStep(index)}
              aria-label={`Go to setup step ${index + 1}`}
              aria-current={index === step ? 'step' : undefined}
            >
              <span>{index < step ? <Check size={11} strokeWidth={3} /> : String(index + 1).padStart(2, '0')}</span>
              <i />
            </button>
          ))}
        </div>
        <div className="onboarding__rail-footer">
          <LockKeyhole size={13} />
          <span>Local-first · no trackers · your data stays yours</span>
        </div>
      </aside>

      <main className="onboarding__main">
        <div className="onboarding__mobile-top">
          <div className="onboarding__mobile-brand">
            <LogoMark size={27} />
            <span>MOMENTUM</span>
          </div>
          <button type="button" className="onboarding__skip" onClick={skip}>
            Skip setup
          </button>
        </div>

        <div className="onboarding__panel" key={step}>
          {step === 0 ? (
            <div className="onboarding-slide onboarding-slide--welcome">
              <span className="onboarding-slide__icon"><Compass size={22} /></span>
              <span className="eyebrow">Welcome to MOMENTUM</span>
              <h1>Turn what you want<br />into what you do.</h1>
              <p className="onboarding-slide__lead">
                Describe an ambition. MOMENTUM helps you shape it into milestones, tasks and a visible trajectory — without
                turning your life into a generic to-do list.
              </p>
              <div className="onboarding-promises">
                <div><Target size={17} /><span><strong>Direction</strong>Goals with real structure.</span></div>
                <div><Layers size={17} /><span><strong>Clarity</strong>Only the next move, surfaced.</span></div>
                <div><Target size={17} /><span><strong>Progress</strong>Milestones, tasks, and clear next steps.</span></div>
              </div>
              <div className="onboarding-actions">
                <Button variant="primary" size="lg" trailingIcon={<ArrowRight size={16} />} onClick={next}>
                  Begin setup
                </Button>
                <Button variant="ghost" onClick={skip}>Skip for now</Button>
              </div>
            </div>
          ) : null}

          {step === 1 ? (
            <div className="onboarding-slide">
              <span className="onboarding-slide__icon"><Settings2 size={22} /></span>
              <span className="eyebrow">Personalize the surface</span>
              <h1>Make it yours<br />before you begin.</h1>
              <p className="onboarding-slide__lead">Choose a name for the workspace and an accent that feels like momentum.</p>

              <div className="onboarding-field">
                <Input
                  label="What should MOMENTUM call you?"
                  value={name}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="Your name or a callsign"
                  maxLength={80}
                  autoFocus
                />
              </div>

              <div className="onboarding-choice-block">
                <span className="field__label">Theme</span>
                <div className="onboarding-theme">
                  <button
                    type="button"
                    className={`onboarding-theme__option onboarding-theme__option--dark${settings.themeMode === 'dark' ? ' is-active' : ''}`}
                    onClick={() => updateSettings({ themeMode: 'dark' })}
                  >
                    <span><Moon size={14} /> Dark</span>
                    <i />
                  </button>
                  <button
                    type="button"
                    className={`onboarding-theme__option onboarding-theme__option--light${settings.themeMode === 'light' ? ' is-active' : ''}`}
                    onClick={() => updateSettings({ themeMode: 'light' })}
                  >
                    <span><Sun size={14} /> Light</span>
                    <i />
                  </button>
                </div>
              </div>

              <div className="onboarding-choice-block">
                <span className="field__label">Accent signal</span>
                <div className="onboarding-accents">
                  {ACCENT_PRESETS.map((preset) => (
                    <button
                      key={preset.value}
                      type="button"
                      className={`onboarding-accent${settings.accent === preset.value ? ' is-active' : ''}`}
                      style={{ '--swatch': preset.value } as React.CSSProperties}
                      onClick={() => updateSettings({ accent: preset.value })}
                      aria-label={preset.name}
                      title={preset.name}
                    >
                      {settings.accent === preset.value ? <Check size={15} /> : null}
                    </button>
                  ))}
                </div>
              </div>

              <div className="onboarding-choice-block">
                <span className="field__label">Custom colors — pick exactly what you want</span>
                <div className="onboarding-gradient-picker">
                  <label className="gradient-picker-slot">
                    <span
                      className="gradient-picker-swatch"
                      style={{ background: settings.accent }}
                      aria-hidden="true"
                    />
                    <input
                      type="color"
                      value={/^#[0-9a-fA-F]{6}$/.test(settings.accent) ? settings.accent : '#3b82f6'}
                      onChange={(event) => updateSettings({ accent: event.target.value })}
                      aria-label="Pick the primary color"
                    />
                    <span className="gradient-picker-meta">
                      <strong>Primary color</strong>
                      <code>{settings.accent}</code>
                    </span>
                  </label>
                  <label className="gradient-picker-slot">
                    <span
                      className="gradient-picker-swatch"
                      style={{ background: settings.accentSecondary }}
                      aria-hidden="true"
                    />
                    <input
                      type="color"
                      value={/^#[0-9a-fA-F]{6}$/.test(settings.accentSecondary ?? '') ? settings.accentSecondary : '#8b5cf6'}
                      onChange={(event) => updateSettings({ accentSecondary: event.target.value })}
                      aria-label="Pick the secondary color"
                    />
                    <span className="gradient-picker-meta">
                      <strong>Secondary color</strong>
                      <code>{settings.accentSecondary}</code>
                    </span>
                  </label>
                  <div
                    className="gradient-picker-preview"
                    style={{ background: `linear-gradient(135deg, ${settings.accent}, ${settings.accentSecondary})` }}
                    role="img"
                    aria-label="Preview of your primary and secondary colors blended together"
                  />
                </div>
                <p className="onboarding-choice-hint">Both colors flow together through the whole app — buttons, rings, charts and highlights. Change them any time in Settings.</p>
              </div>

              <div className="onboarding-actions">
                <Button variant="ghost" icon={<ArrowLeft size={15} />} onClick={() => setStep(0)}>Back</Button>
                <Button variant="primary" size="lg" trailingIcon={<ArrowRight size={16} />} onClick={next}>Continue</Button>
              </div>
            </div>
          ) : null}

          {step === 2 ? (
            <div className="onboarding-slide">
              <span className="onboarding-slide__icon"><Layers size={22} /></span>
              <span className="eyebrow">The method</span>
              <h1>Structure, without<br />the clutter.</h1>
              <p className="onboarding-slide__lead">
                A goal is not a checkbox. It is a direction with a story. MOMENTUM keeps the layers separate so the day-to-day
                stays simple.
              </p>

              <div className="onboarding-method">
                <div className="method-row">
                  <span className="method-row__index">01</span>
                  <div className="method-row__visual method-row__visual--goal">
                    <span />
                    <i />
                  </div>
                  <div>
                    <strong>Goal</strong>
                    <p>The outcome and why it matters. One horizon, no forced template.</p>
                  </div>
                </div>
                <div className="method-connector" />
                <div className="method-row">
                  <span className="method-row__index">02</span>
                  <div className="method-row__visual method-row__visual--milestone">
                    <span /><span /><span />
                  </div>
                  <div>
                    <strong>Milestones</strong>
                    <p>Checkpoints that mark a change in reality — proof you are getting somewhere.</p>
                  </div>
                </div>
                <div className="method-connector" />
                <div className="method-row">
                  <span className="method-row__index">03</span>
                  <div className="method-row__visual method-row__visual--task">
                    <Circle size={14} /><Circle size={14} /><Check size={14} />
                  </div>
                  <div>
                    <strong>Tasks & subtasks</strong>
                    <p>Concrete moves, small enough to start now. Progress updates the moment you finish.</p>
                  </div>
                </div>
              </div>

              <div className="onboarding-note">
                <span className="onboarding-note__dot" />
                <span>Deadlines are optional. Recurring goals are supported. Financial tracking exists only where you turn it on.</span>
              </div>

              <div className="onboarding-actions">
                <Button variant="ghost" icon={<ArrowLeft size={15} />} onClick={() => setStep(1)}>Back</Button>
                <Button variant="primary" size="lg" trailingIcon={<ArrowRight size={16} />} onClick={next}>Continue</Button>
              </div>
            </div>
          ) : null}

          {step === 3 ? (
            <div className="onboarding-slide">
              <span className="onboarding-slide__icon"><Target size={22} /></span>
              <span className="eyebrow">The first direction</span>
              <h1>Give the workspace<br />one thing to move.</h1>
              <p className="onboarding-slide__lead">
                Optional, but a direction beats a blank page. Start with a title, then add milestones and tasks.
              </p>

              <div className="onboarding-first-goal">
                <Input
                  label="One goal you want to move forward"
                  value={goalTitle}
                  onChange={(event) => setGoalTitle(event.target.value)}
                  placeholder="e.g. Ship the first version of my portfolio"
                  maxLength={120}
                  autoFocus
                />
                <Input
                  label="A deadline, if one is real"
                  type="date"
                  min={toDateInput()}
                  value={deadline}
                  onChange={(event) => setDeadline(event.target.value)}
                  optional
                />
              </div>

              <div className="onboarding-recap">
                <span className="eyebrow">Your workspace</span>
                <div><span>Theme</span><strong>{settings.themeMode === 'light' ? 'Light' : 'Dark'}</strong></div>
                <div><span>Colors</span><span className="recap-accent" style={{ background: `linear-gradient(135deg, ${settings.accent}, ${settings.accentSecondary})` }} /></div>
                <div><span>Storage</span><strong>This device</strong></div>
              </div>

              <div className="onboarding-actions">
                <Button variant="ghost" icon={<ArrowLeft size={15} />} onClick={() => setStep(3)}>Back</Button>
                <div className="onboarding-actions__right">
                  <Button variant="ghost" onClick={() => complete(false)}>Enter empty workspace</Button>
                  <Button
                    variant="primary"
                    size="lg"
                    icon={goalTitle.trim() ? <Plus size={16} /> : <ChevronRight size={16} />}
                    onClick={() => complete(true)}
                  >
                    {goalTitle.trim() ? 'Create goal & enter' : 'Enter MOMENTUM'}
                  </Button>
                </div>
              </div>
            </div>
          ) : null}
        </div>

        <div className="onboarding__footer">
          <span>{String(step + 1).padStart(2, '0')} / {String(STEPS).padStart(2, '0')}</span>
          <div className="onboarding__progress"><span style={{ width: `${((step + 1) / STEPS) * 100}%` }} /></div>
          <span>Local-first productivity</span>
        </div>
      </main>
    </div>
  );
}
