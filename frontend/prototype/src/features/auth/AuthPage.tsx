import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Badge, Button, Card, Icon, Input, useToast } from '../../shared/ui';

type AuthMode = 'login' | 'email' | 'verify' | 'profile';

// 실제 인증 상태를 만들지 않는다. 각 단계는 독립적으로 검토 가능한 화면 시안이다.
export function AuthPage({ mode }: { mode: AuthMode }) {
  const navigate = useNavigate();
  const notify = useToast();
  const [showPassword, setShowPassword] = useState(false);
  useEffect(() => {
    setShowPassword(false);
  }, [mode]);
  const step = mode === 'email' ? 1 : mode === 'verify' ? 2 : 3;
  const heading = {
    login: '반가워요, 함께 가요.',
    email: '학교 이메일로 시작해요.',
    verify: '인증번호를 입력해 주세요.',
    profile: '어떻게 불러드릴까요?',
  }[mode];
  const description = {
    login: '전남대 학생들과 나누는 가벼운 이동.',
    email: '전남대학교 이메일을 사용하는 동승 서비스예요.',
    verify: '6자리 인증번호 입력 화면의 시안이에요.',
    profile: '동승자에게 보여줄 프로필을 정해 주세요.',
  }[mode];
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (mode === 'email') {
      notify('메일 발송 없이 다음 화면을 미리 봅니다.');
      navigate('/signup/verify');
    }
    if (mode === 'verify') {
      notify('인증 처리 없이 프로필 화면을 미리 봅니다.');
      navigate('/signup/profile');
    }
    if (mode === 'profile' || mode === 'login') {
      notify('계정 처리 없이 매칭 성사 시안으로 이동합니다.');
      navigate('/groups/demo');
    }
  }
  return (
    <div className="auth-layout">
      <section className="auth-story">
        <span className="eyebrow">같은 학교, 같은 방향</span>
        <h1>
          가는 길이 같다면,
          <br />
          <em>가치가.</em>
        </h1>
        <p>
          혼자 타기엔 부담됐던 택시.
          <br />
          이제 우리 학교 친구들과 함께 가요.
        </p>
        <div className="journey-art" aria-hidden="true">
          <span className="art-start">전남대</span>
          <span className="art-line" />
          <span className="art-middle">
            <Icon name="people" />
          </span>
          <span className="art-end">
            <Icon name="pin" />
            목적지
          </span>
          <span className="art-note">함께라서 더 가벼운 이동</span>
        </div>
        <div className="auth-benefits">
          <span>
            <Icon name="mail" />
            학교 이메일 인증
          </span>
          <span>
            <Icon name="ticket" />
            구간별 요금 분담
          </span>
          <span>
            <Icon name="chat" />
            동승자 전용 대화
          </span>
        </div>
      </section>
      <Card className="auth-card">
        {mode !== 'login' && (
          <div className="signup-steps" aria-label={`회원가입 화면 ${step}단계`}>
            {['이메일', '인증번호', '프로필'].map((label, index) => (
              <span key={label} className={index + 1 <= step ? 'step-active' : ''}>
                <i>{index + 1 < step ? <Icon name="check" /> : index + 1}</i>
                {label}
              </span>
            ))}
          </div>
        )}
        <Badge tone="neutral">{mode === 'login' ? 'WE-MEET' : '처음 오셨나요?'}</Badge>
        <h2>{heading}</h2>
        <p className="muted auth-description">{description}</p>
        <form key={mode} onSubmit={submit}>
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
                placeholder="미리보기용 임의의 값을 입력하세요"
                autoComplete="new-password"
                required
              />
              <button
                type="button"
                className="text-button password-toggle"
                aria-pressed={showPassword}
                onClick={() => setShowPassword(!showPassword)}
              >
                {showPassword ? '숨기기' : '보기'}
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
                onClick={() =>
                  notify('인증번호 재발송 안내가 표시되는 자리예요. 실제 메일은 보내지 않습니다.')
                }
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
          <Button type="submit" className="full-width">
            {mode === 'login'
              ? '로그인 화면 동작 보기'
              : mode === 'profile'
                ? '프로필 완료 화면 보기'
                : '다음 화면 보기'}
            <Icon name="arrow" />
          </Button>
        </form>
        {mode === 'login' ? (
          <p className="auth-switch">
            아직 계정이 없나요? <Link to="/signup">회원가입 화면 보기</Link>
          </p>
        ) : (
          <p className="auth-switch">
            <Link
              to={mode === 'email' ? '/login' : mode === 'verify' ? '/signup' : '/signup/verify'}
            >
              이전 화면으로
            </Link>
          </p>
        )}
        <div className="auth-preview-note">
          <Icon name="info" />
          <span>
            화면 확인용입니다. 입력 내용은 전송·저장하지 않으며 실제 계정을 생성하지 않아요.
          </span>
        </div>
      </Card>
    </div>
  );
}
