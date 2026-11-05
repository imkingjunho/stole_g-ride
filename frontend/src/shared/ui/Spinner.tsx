export function Spinner({ label = '불러오는 중' }: { label?: string }) {
  return (
    <span role="status" className="inline-flex items-center">
      <span
        aria-hidden="true"
        className="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}
