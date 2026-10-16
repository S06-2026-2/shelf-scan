import './Logo.css'

interface LogoProps {
  tamanho?: number
  somenteIcone?: boolean
}

export function Logo({ tamanho = 32, somenteIcone = false }: LogoProps) {
  return (
    <span className="logo">
      <svg
        width={tamanho}
        height={tamanho}
        viewBox="0 0 64 64"
        role={somenteIcone ? 'img' : undefined}
        aria-label={somenteIcone ? 'Shelf Scan' : undefined}
        aria-hidden={somenteIcone ? undefined : true}
      >
        <rect width="64" height="64" rx="14" fill="#2B4FBF" />
        <path
          d="M9 19V9h10M45 9h10v10M9 45v10h10M55 45v10H45"
          fill="none"
          stroke="#F2C14E"
          strokeWidth="3.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <rect x="17" y="24" width="8" height="21" rx="1.5" fill="#F4F6F1" />
        <rect x="28" y="16" width="8" height="29" rx="1.5" fill="#C7D5FA" />
        <polygon points="30,16 33.5,16 33.5,27 31.75,24.5 30,27" fill="#F2C14E" />
        <rect
          x="40"
          y="21"
          width="8"
          height="24"
          rx="1.5"
          fill="#F4F6F1"
          transform="rotate(-14 40 45)"
        />
        <rect x="15" y="45" width="34" height="3" rx="1.5" fill="#F4F6F1" />
      </svg>
      {!somenteIcone && (
        <span className="logo-texto" style={{ fontSize: tamanho * 0.62 }}>
          Shelf Scan
        </span>
      )}
    </span>
  )
}
