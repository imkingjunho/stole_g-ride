import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseCsv, summarizeSurvey, surveyColumns } from './summarize-beta.mjs';
const csv = (...rows) => surveyColumns.join(',') + '\r\n' + rows.join('\r\n');
const response = (id, mode = 'REAL', overall = '4') =>
  `${id},${mode},2026-11-11,4,5,3,,${overall},`;
test('BOM·인용된 쉼표·따옴표·줄바꿈 CSV를 읽는다', () => {
  assert.deepEqual(parseCsv('\uFEFFa,b\r\n1,"쉼표,\n""인용"""\r\n'), [
    ['a', 'b'],
    ['1', '쉼표,\n"인용"'],
  ]);
  assert.throws(() => parseCsv('a,"닫히지 않음'), /닫히지/);
});
test('응답이 없으면 평균은 null이고 목표는 충족되지 않는다', () => {
  const result = summarizeSurvey(csv());
  assert.equal(result.actualParticipantsWithOverallScore, 0);
  assert.equal(result.ratings.overall_satisfaction.mean, null);
  assert.deepEqual(result.targets, { participants30: false, overallAtLeast4: false });
});
test('목 점수와 전체 만족도 없는 응답은 실제 전체 만족도 분모에서 제외한다', () => {
  const result = summarizeSurvey(
    csv(response('P001', 'MOCK', '5'), response('P001'), response('P002', 'REAL', '')),
  );
  assert.equal(result.excludedMockRows, 1);
  assert.equal(result.rowsMissingOverall, 1);
  assert.equal(result.actualParticipantsWithOverallScore, 1);
  assert.equal(result.ratings.overall_satisfaction.mean, 4);
  assert.equal(result.ratings.request_ease.count, 2);
  assert.equal(result.ratings.error_recovery.mean, null);
});
test('실제 응답 중복은 조용히 평균내지 않고 거절한다', () => {
  assert.throws(() => summarizeSurvey(csv(response('P001'), response('P001'))), /중복/);
});
test('잘못된 점수·날짜·기록 모드·이메일 형태 ID를 거절한다', () => {
  for (const row of [
    response('P001', 'REAL', '6'),
    response('P001').replace('2026-11-11', '2026-02-30'),
    response('P001', 'TEST'),
    response('mail@jnu.ac.kr'),
  ])
    assert.throws(() => summarizeSurvey(csv(row)));
});
test('30개의 유효한 실제 기록으로 만족도 목표를 계산하며 서버 통합 완료를 선언하지 않는다', () => {
  const rows = Array.from({ length: 30 }, (_, i) => response(`P${String(i + 1).padStart(3, '0')}`));
  const result = summarizeSurvey(csv(...rows));
  assert.deepEqual(result.targets, { participants30: true, overallAtLeast4: true });
  assert.equal(result.serverIntegrationVerified, 'not_checked_by_this_script');
});
test('4점 미만을 반올림해서 목표 달성으로 표시하지 않는다', () => {
  const result = summarizeSurvey(csv(response('P001', 'REAL', '4'), response('P002', 'REAL', '3')));
  assert.equal(result.ratings.overall_satisfaction.mean, 3.5);
  assert.equal(result.targets.overallAtLeast4, false);
});
