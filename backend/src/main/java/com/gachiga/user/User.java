package com.gachiga.user;

import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 한 명 (PRD §7.1 {@code users}).
 *
 * <p>실명·전화번호는 담지 않는다 (CLAUDE.md §8). 다른 모듈에는 {@code contract.user.UserPort}로만
 * 노출한다.
 *
 * <p>{@link Gender}·{@link UserStatus}는 이미 {@code contract.user}에 있는 것을 그대로 쓴다 — 다른
 * 모듈로 넘기는 값과 여기 저장하는 값이 같은 의미이므로 중복 정의하지 않는다.
 *
 * <p>성별은 가입 후 바꿀 수 없다 (동성 매칭의 근거, FR-04). setter 없이 {@link #create}로만 값이
 * 정해진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
            @UniqueConstraint(name = "uk_users_nickname", columnNames = "nickname")
        })
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code @jnu.ac.kr} 웹메일 (FR-01) */
    @Column(nullable = false, length = 100)
    private String email;

    /** BCrypt 해시. 평문은 어디에도 저장하지 않는다 */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 1)
    private Gender gender;

    /** 선택 입력 */
    @Column(length = 50)
    private String department;

    /** 선택 입력 */
    private Integer grade;

    /** 웹메일 인증이 끝난 시각. 가입 완료(T1-3) 시점에 함께 기록한다 */
    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    /** 누적 신고 횟수. 3회부터 {@code SUSPENDED} (E-05, Phase 3) */
    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** {@code status}가 {@code SUSPENDED}일 때만 값이 있다 */
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 가입을 완료한다 (FR-01·T1-3). 상태는 {@code ACTIVE}로 시작한다.
     *
     * <p>시각을 인자로 받는 이유는 테스트에서 "지금"을 고정할 수 있게 하기 위해서다.
     *
     * @param now 가입 완료 시각. {@code verifiedAt}·{@code createdAt}에 그대로 쓰인다
     */
    public static User create(
            String email,
            String passwordHash,
            String nickname,
            Gender gender,
            String department,
            Integer grade,
            LocalDateTime now) {

        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.nickname = nickname;
        user.gender = gender;
        user.department = department;
        user.grade = grade;
        user.verifiedAt = now;
        user.reportCount = 0;
        user.status = UserStatus.ACTIVE;
        user.createdAt = now;
        return user;
    }

    /**
     * 닉네임·학과·학년을 바꾼다 (T2-5, FR-03).
     *
     * <p>성별은 여기 없다 — 가입 후 바꿀 수 없다. 학과·학년은 선택 입력이라 {@code null} 을 주면
     * 지워진다.
     */
    public void updateProfile(String nickname, String department, Integer grade) {
        this.nickname = nickname;
        this.department = department;
        this.grade = grade;
    }
}
