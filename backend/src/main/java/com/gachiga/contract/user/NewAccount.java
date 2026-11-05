package com.gachiga.contract.user;

/**
 * 가입 정보 — {@link UserAccountPort#register} 의 입력. {@code auth} 가 인증 코드를 확인한 뒤 만든다.
 *
 * <p>실명·전화번호는 없다 (FR-03, CLAUDE.md §8).
 *
 * <p><b>{@code toString} 은 비밀번호를 가린다.</b> 레코드 기본 {@code toString} 은 모든 필드를 찍어서, 누가 이
 * 객체를 로그에 남기면 평문 비밀번호가 그대로 나간다.
 *
 * @param email       웹메일 주소. 생성자가 {@link CanonicalEmail#of} 로 정식 형태로 맞춘다. null 아님
 * @param rawPassword 평문 비밀번호. 받는 쪽이 해시한다. null 아님
 * @param nickname    닉네임. 전체에서 유일해야 한다. null 아님
 * @param gender      성별. 가입 후 바꿀 수 없다(동성 매칭의 근거). null 아님
 * @param department  학과. 선택이라 null 일 수 있다
 * @param grade       학년. 선택이라 null 일 수 있다
 */
public record NewAccount(
        String email,
        String rawPassword,
        String nickname,
        Gender gender,
        String department,
        Integer grade) {

    /** 이메일을 정식 형태로 맞춘다. 부르는 쪽이 잊어도 저장 형태가 어긋나지 않게 하려는 것이다 */
    public NewAccount {
        email = CanonicalEmail.of(email);
    }

    @Override
    public String toString() {
        return "NewAccount[email="
                + email
                + ", rawPassword=***, nickname="
                + nickname
                + ", gender="
                + gender
                + ", department="
                + department
                + ", grade="
                + grade
                + "]";
    }
}
