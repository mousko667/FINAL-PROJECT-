import { cn } from '@/lib/utils'

interface SkeletonProps {
  className?: string
}

export function Skeleton({ className }: SkeletonProps) {
  return <div className={cn('skeleton', className)} />
}

export function SkeletonCard() {
  return (
    <div className="bg-white rounded-xl border p-5 flex items-start gap-4">
      <Skeleton className="w-12 h-12 rounded-xl shrink-0" />
      <div className="flex-1 space-y-2">
        <Skeleton className="h-3.5 w-24" />
        <Skeleton className="h-7 w-16" />
      </div>
    </div>
  )
}

export function SkeletonTable({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <div className="border-b px-4 py-3 bg-gray-50 flex gap-4">
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} className="h-3.5 flex-1" />
        ))}
      </div>
      <div className="divide-y">
        {Array.from({ length: rows }).map((_, r) => (
          <div key={r} className="flex gap-4 px-4 py-3.5 items-center">
            {Array.from({ length: cols }).map((_, c) => (
              <Skeleton key={c} className={cn('h-3.5 flex-1', c === 0 && 'w-28 flex-none')} />
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

export function SkeletonDashboard() {
  return (
    <div className="space-y-6 page-enter">
      <Skeleton className="h-8 w-48" />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {[0, 1, 2, 3].map(i => <SkeletonCard key={i} />)}
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border p-5 space-y-3">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-[220px] w-full rounded-lg" />
        </div>
        <div className="bg-white rounded-xl border p-5 space-y-3">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-[220px] w-full rounded-lg" />
        </div>
      </div>
    </div>
  )
}
