import type { SVGProps } from 'react';

const paths = {
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  back: 'M19 12H5m6-6-6 6 6 6',
  chevron: 'm9 5 7 7-7 7',
  check: 'm5 12 4 4L19 6',
  close: 'm6 6 12 12M18 6 6 18',
  menu: 'M4 6h16M4 12h16M4 18h16',
  user: 'M20 21v-2a7 7 0 0 0-14 0v2M15 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  people:
    'M16 21v-2a5 5 0 0 0-10 0v2M14 7a3 3 0 1 1-6 0 3 3 0 0 1 6 0M17 4a3 3 0 0 1 0 6m2 5a5 5 0 0 1 3 4v2',
  chat: 'M21 11a8 8 0 0 1-8 8H7l-5 3V11a9 9 0 0 1 19 0ZM7 10h10M7 14h6',
  ticket: 'M4 3h16v6a3 3 0 0 0 0 6v6H4v-6a3 3 0 0 0 0-6V3Zm8 3v2m0 3v2m0 3v2',
  history: 'M3 11a9 9 0 1 1 2 7M3 4v7h7m2-5v6l4 2',
  lock: 'M6 10h12v11H6V10Zm2 0V6a4 4 0 0 1 8 0v4m-4 4v3',
  mail: 'M3 5h18v14H3V5Zm0 1 9 7 9-7',
  pin: 'M19 10c0 5-7 12-7 12S5 15 5 10a7 7 0 1 1 14 0Zm-5 0a2 2 0 1 1-4 0 2 2 0 0 1 4 0',
  clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9-5v5l3 2',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  info: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 11v6m0-10v.01',
  send: 'm22 2-7 20-4-9L2 9l20-7Zm0 0L11 13',
  refresh: 'M3 10a9 9 0 0 1 15-6l3 3M21 2v5h-5M21 14a9 9 0 0 1-15 6l-3-3m0 5v-5h5',
  map: 'm9 3 6 3 6-3v18l-6 3-6-3-6 3V6l6-3Zm0 0v18m6-15v18',
} as const;

export type IconName = keyof typeof paths;

export function Icon({ name, ...props }: SVGProps<SVGSVGElement> & { name: IconName }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
