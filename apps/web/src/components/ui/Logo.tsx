export function LogoMark({ size = 34 }: { size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      aria-hidden="true"
      className="logo-mark"
    >
      <rect width="48" height="48" rx="12" fill="var(--logo-bg, #0b1018)" />
      <path
        d="M7 39L17 22L24 33"
        fill="none"
        stroke="var(--accent)"
        strokeWidth="4.6"
        strokeLinecap="square"
        strokeLinejoin="miter"
      />
      <path
        d="M20 39L31 21"
        fill="none"
        stroke="#f2f6ff"
        strokeWidth="4.6"
        strokeLinecap="square"
      />
      <polygon points="28.5,39 33,39 40.5,17.2 36.2,16" fill="var(--accent)" />
      <polygon points="35,17.5 42.8,8.2 44.4,20.7" fill="var(--accent)" />
    </svg>
  );
}

export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="logo">
      <LogoMark />
      {!compact ? (
        <span className="logo__text">
          MOMENTUM<span className="logo__dot">.</span>
        </span>
      ) : null}
    </span>
  );
}
