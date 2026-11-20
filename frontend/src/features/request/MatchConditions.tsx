import type { components } from '@/generated/api';
type Command = components['schemas']['CreateRideRequest'];
interface Props {
  wait: Command['maxWaitMin'];
  sameGender: boolean;
  detour: Command['maxDetourRatio'];
  onWait: (v: Command['maxWaitMin']) => void;
  onGender: (v: boolean) => void;
  onDetour: (v: Command['maxDetourRatio']) => void;
}
function Choices<T extends number | string>({
  label,
  values,
  selected,
  display,
  onChange,
}: {
  label: string;
  values: readonly T[];
  selected: T;
  display: (v: T) => string;
  onChange: (v: T) => void;
}) {
  return (
    <fieldset className="trip-choices">
      <legend>{label}</legend>
      <div>
        {values.map((v) => (
          <label key={v}>
            <input
              type="radio"
              name={label}
              value={v}
              checked={v === selected}
              onChange={() => onChange(v)}
            />
            <span>{display(v)}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
export function MatchConditions({ wait, sameGender, detour, onWait, onGender, onDetour }: Props) {
  return (
    <div className="trip-conditions">
      <Choices
        label="최대 대기 시간"
        values={[5, 10, 15, 20] as const}
        selected={wait}
        display={(n) => n + '분'}
        onChange={onWait}
      />
      <Choices
        label="동승 조건"
        values={['성별 무관', '동성만 매칭']}
        selected={sameGender ? '동성만 매칭' : '성별 무관'}
        display={(s) => s}
        onChange={(s) => onGender(s === '동성만 매칭')}
      />
      <Choices
        label="최대 허용 우회율"
        values={[0.1, 0.2, 0.3] as const}
        selected={detour}
        display={(n) => Math.round(n * 100) + '%'}
        onChange={onDetour}
      />
      <p className="text-xs text-slate-600">
        혼자 10km 가는 길이라면 최대 {10 + 10 * detour}km까지 허용해요.
      </p>
    </div>
  );
}
