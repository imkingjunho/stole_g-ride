import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const surveyColumns = [
  'participant_id',
  'mode',
  'observed_date',
  'request_ease',
  'fare_clarity',
  'chat_ease',
  'error_recovery',
  'overall_satisfaction',
  'comment',
];
const ratings = surveyColumns.slice(3, 8);

/** Excel CSV의 BOM·따옴표·쉼표·줄바꿈을 처리한다. 입력값을 실행하거나 평가하지 않는다. */
export function parseCsv(input) {
  const rows = [];
  let row = [],
    field = '',
    quoted = false,
    closed = false;
  const text = input.replace(/^\uFEFF/, '');
  function pushField() {
    row.push(field);
    field = '';
    closed = false;
  }
  function pushRow() {
    pushField();
    if (row.some((value) => value.trim())) rows.push(row);
    row = [];
  }
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else field += char;
    } else if (char === '"') {
      if (field || closed) throw new Error('CSV 따옴표 형식이 올바르지 않습니다.');
      quoted = true;
    } else if (char === ',') pushField();
    else if (char === '\r' || char === '\n') {
      if (char === '\r' && text[i + 1] === '\n') i++;
      pushRow();
    } else {
      if (closed) throw new Error('CSV 닫는 따옴표 뒤에 문자가 있습니다.');
      field += char;
    }
  }
  if (quoted) throw new Error('CSV 따옴표가 닫히지 않았습니다.');
  if (field || row.length || closed) pushRow();
  return rows;
}
export function summarizeSurvey(input) {
  const [header, ...rows] = parseCsv(input);
  if (!header || header.join(',') !== surveyColumns.join(','))
    throw new Error('설문 CSV 열이 템플릿과 다릅니다.');
  const scores = Object.fromEntries(ratings.map((key) => [key, []]));
  const seen = new Set();
  let mockRows = 0,
    realRows = 0,
    missingOverall = 0;
  rows.forEach((row, index) => {
    const line = index + 2;
    if (row.length !== header.length) throw new Error(`${line}행: 열 개수가 다릅니다.`);
    const record = Object.fromEntries(header.map((key, i) => [key, row[i].trim()]));
    if (!/^P\d{3}$/.test(record.participant_id))
      throw new Error(`${line}행: 참여자는 P001 형식의 익명 번호를 사용합니다.`);
    if (!['MOCK', 'REAL'].includes(record.mode))
      throw new Error(`${line}행: mode는 MOCK 또는 REAL이어야 합니다.`);
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(record.observed_date) ||
      !Number.isFinite(Date.parse(record.observed_date)) ||
      new Date(record.observed_date).toISOString().slice(0, 10) !== record.observed_date
    )
      throw new Error(`${line}행: 관찰 날짜가 올바르지 않습니다.`);
    for (const key of ratings)
      if (record[key] && !/^[1-5]$/.test(record[key]))
        throw new Error(`${line}행: 점수는 1~5 또는 빈칸이어야 합니다.`);
    if (record.mode === 'MOCK') {
      mockRows++;
      return;
    }
    if (seen.has(record.participant_id))
      throw new Error(`${line}행: 실제 참여자 번호가 중복됐습니다.`);
    seen.add(record.participant_id);
    realRows++;
    if (!record.overall_satisfaction) missingOverall++;
    for (const key of ratings) if (record[key]) scores[key].push(Number(record[key]));
  });
  const summary = Object.fromEntries(
    ratings.map((key) => {
      const list = scores[key],
        total = list.reduce((a, b) => a + b, 0);
      return [key, { count: list.length, mean: list.length ? total / list.length : null }];
    }),
  );
  const overall = scores.overall_satisfaction;
  return {
    scope: 'RECORDED_REAL_SURVEY',
    serverIntegrationVerified: 'not_checked_by_this_script',
    actualParticipantsWithOverallScore: overall.length,
    realRows,
    excludedMockRows: mockRows,
    rowsMissingOverall: missingOverall,
    ratings: summary,
    targets: {
      participants30: overall.length >= 30,
      overallAtLeast4:
        overall.length > 0 && overall.reduce((a, b) => a + b, 0) >= 4 * overall.length,
    },
  };
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    let input,
      inputStatus = 'responses_file';
    try {
      input = await readFile(process.argv[2] ?? 'beta/survey-responses.csv', 'utf8');
    } catch (error) {
      if (process.argv[2] || error.code !== 'ENOENT') throw error;
      input = await readFile('beta/survey-responses.example.csv', 'utf8');
      inputStatus = 'empty_template';
    }
    console.log(JSON.stringify({ inputStatus, ...summarizeSurvey(input) }, null, 2));
  } catch (error) {
    console.error(error instanceof Error ? error.message : '설문을 읽지 못했습니다.');
    process.exitCode = 1;
  }
}
