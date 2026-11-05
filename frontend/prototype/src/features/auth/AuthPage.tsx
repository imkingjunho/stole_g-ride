import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Badge, Button, Card, Icon, Input, useToast } from '../../shared/ui';
import { fareExhibit } from '../../shared/preview/fixtures';

type AuthMode = 'login' | 'email' | 'verify' | 'profile';

// 화면 이동만 제공한다. 인증 요청이나 입력값 저장은 하지 않는다.
export function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const notify = useToast();
  const [showPassword, setShowPassword] = useState(false);
  useEffect(() => {
    setShowPassword(false);
  }, [mode]);
  const step = mode === 'email' ? 1 : mode === 'verify' ? 2 : 3;
  const heading = {
    login: '',
    email: '학교 이메일로 시작해요',
    verify: '인증번호를 입력해 주세요',
    profile: '어떻게 불러드릴까요?',
  }[mode];
  const description = {
    login: '',
    email: '전남대학교 학우와 함께하는 첫걸음이에요.',
    verify: '6자리 인증번호 입력 화면의 시안이에요.',
    profile: '동승자에게 보여줄 프로필을 정해 주세요.',
  }[mode];

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mode === 'email') {
      notify('메일 발송 없이 다음 화면을 미리 봅니다.');
      navigate('/signup/verify');
    } else if (mode === 'verify') {
      notify('인증 처리 없이 프로필 화면을 미리 봅니다.');
      navigate('/signup/profile');
    } else {
      notify('계정 처리 없이 매칭 성사 시안으로 이동합니다.');
      navigate('/groups/demo');
    }
  }

  return (
    <div className="page-enter auth-page">
      {mode === 'login' ? (
        <>
          <section className="auth-intro">
            <span className="app-mark">
              <Icon name="car" width="30" height="30" />
            </span>
            <h1>
              전남대 학생끼리
              <br />
              <em>택시비 나눠 내기</em>
            </h1>
            <p>
              <Icon name="shield" width="18" height="18" />
              가치가 · 학교 이메일로 이어지는 동행
            </p>
          </section>
          <Card className="benefit-card">
            <span className="benefit-icon">
              <Icon name="calculator" />
            </span>
            <div>
              <h2>
                먼저 내리면 그만큼만 내요 <Badge>공정 정산</Badge>
              </h2>
              <p>
                함께 탄 구간을 기준으로 나눠요.
                <br />
                혼자 {fareExhibit.solo}원 → 함께 <strong>{fareExhibit.share}원</strong>{' '}
                <small>예시</small>
              </p>
            </div>
          </Card>
        </>
      ) : (
        <>
          <div className="signup-steps" aria-label={`회원가입 화면 ${step}단계`}>
            {['이메일', '인증번호', '프로필'].map((label, index) => (
              <span key={label} className={index + 1 <= step ? 'step-active' : ''}>
                <i>{index + 1 < step ? <Icon name="check" width="14" height="14" /> : index + 1}</i>
                {label}
              </span>
            ))}
          </div>
          <div className="page-heading">
            <h1>{heading}</h1>
            <p>{description}</p>
          </div>
        </>
      )}
      <form className="auth-form" key={mode} onSubmit={submit}>
        {(mode === 'login' || mode === 'email') && (
          <Input
            label="학교 이메일"
            name="email"
            type="email"
            placeholder="example@jnu.ac.kr"
            autoComplete="off"
            required
            pattern=".+@[jJ][nN][uU][.][aA][cC][.][kK][rR]"
            title="@jnu.ac.kr로 끝나는 학교 이메일을 입력해 주세요."
            hint="@jnu.ac.kr 이메일을 입력해 주세요."
          />
        )}
        {(mode === 'login' || mode === 'profile') && (
          <div className="password-field">
            <Input
              label="비밀번호"
              name="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="미리보기용 비밀번호 입력"
              autoComplete="new-password"
              required
            />
            <button
              type="button"
              className="icon-button password-toggle"
              aria-label={showPassword ? '숨기기' : '보기'}
              aria-pressed={showPassword}
              onClick={() => setShowPassword(!showPassword)}
            >
              <Icon name={showPassword ? 'eye' : 'eyeOff'} />
            </button>
          </div>
        )}
        {mode === 'verify' && (
          <>
            <Input
              label="인증번호"
              name="code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              placeholder="000000"
              required
              autoComplete="off"
              title="숫자 6자리를 입력해 주세요."
              hint="시안에서는 임의의 숫자 6자리를 입력할 수 있어요."
              className="code-field"
            />
            <button
              type="button"
              className="text-button resend-button"
              onClick={() => notify('재발송 안내의 시안이에요. 실제 메일은 보내지 않습니다.')}
            >
              인증번호 다시 받기
            </button>
          </>
        )}
        {mode === 'profile' && (
          <>
            <Input
              label="닉네임"
              name="nickname"
              placeholder="예: 후문산책러"
              maxLength={20}
              required
            />
            <fieldset className="gender-field">
              <legend>성별</legend>
              <div className="radio-cards">
                <label>
                  <input type="radio" name="gender" value="F" required />
                  여성
                </label>
                <label>
                  <input type="radio" name="gender" value="M" required />
                  남성
                </label>
              </div>
              <p className="field-hint">등록 후 성별을 변경할 수 없어요.</p>
            </fieldset>
            <details className="optional-fields">
              <summary>
                학과·학년 입력하기 <span>선택</span>
              </summary>
              <Input label="학과" name="department" placeholder="예: 인공지능학부" />
              <label className="select-field">
                학년
                <select name="grade" defaultValue="">
                  <option value="">선택 안 함</option>
                  {[1, 2, 3, 4, 5, 6].map((grade) => (
                    <option key={grade} value={grade}>
                      {grade}학년
                    </option>
                  ))}
                </select>
              </label>
            </details>
          </>
        )}
        <Button
          type="submit"
          className="full-width"
          aria-label={
            mode === 'login'
              ? '로그인 화면 동작 보기'
              : mode === 'profile'
                ? '프로필 완료 화면 보기'
                : '다음 화면 보기'
          }
        >
          {mode === 'login' ? '로그인' : mode === 'profile' ? '프로필 완료' : '다음'}
          <Icon name="arrow" />
        </Button>
      </form>
      {mode === 'login' ? (
        <p className="auth-switch">
          <Link to="/signup">전남대 이메일로 가입하기</Link>
        </p>
      ) : (
        <p className="auth-switch">
          <Link to={mode === 'email' ? '/login' : mode === 'verify' ? '/signup' : '/signup/verify'}>
            이전 화면으로
          </Link>
        </p>
      )}
      <Card className="trust-card">
        <h2>
          <Icon name="shield" />
          학우끼리, 부담을 나누는 이동
        </h2>
        <p>학교 이메일 인증과 구간별 요금 분담으로 함께 가는 캠퍼스를 준비하고 있어요.</p>
        <p className="small muted">
          지금은 화면 시안이에요. 입력 내용은 전송·저장하지 않으며 계정이 생성되지 않아요.
        </p>
      </Card>
    </div>
  );
}
