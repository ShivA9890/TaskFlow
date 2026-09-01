import type { ReactNode } from 'react'

export default function AuthShell({
  title,
  intro,
  children,
  footer,
}: {
  title: string
  intro: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1fr_460px]">
      <aside className="hidden flex-col justify-between border-r border-line bg-surface p-10 lg:flex">
        <span className="font-mono text-sm font-medium tracking-tightest">
          task<span className="text-accent">flow</span>
        </span>
        <div>
          <p className="max-w-sm text-2xl font-medium leading-tight tracking-tightest">
            Work moves left to right. Everything else is noise.
          </p>
          <div className="mt-8 flex gap-1" aria-hidden="true">
            {['To do', 'In progress', 'In review', 'Completed'].map((name, i) => (
              <div key={name} className="flex-1">
                <div className="h-px bg-line">
                  <div className="h-px bg-accent" style={{ width: `${100 - i * 28}%` }} />
                </div>
                <span className="label mt-2 block">{name}</span>
              </div>
            ))}
          </div>
        </div>
        <p className="label">Microservices demo workspace</p>
      </aside>

      <section className="flex items-center justify-center p-6">
        <div className="w-full max-w-sm">
          <h1 className="text-xl font-medium tracking-tightest">{title}</h1>
          <p className="mt-1 text-sm text-slate">{intro}</p>
          <div className="mt-6 space-y-4">{children}</div>
          {footer && <div className="mt-6 text-sm text-slate">{footer}</div>}
        </div>
      </section>
    </div>
  )
}
