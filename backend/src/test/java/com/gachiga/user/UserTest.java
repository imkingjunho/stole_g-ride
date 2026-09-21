package com.gachiga.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.gachiga.contract.user.Gender;
import com.gachiga.contract.user.UserStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link User} 생성값 검증. DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7).
 */
class UserTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 21, 0);

    private User verifiedUser() {
        return User.create(
                "gachiga@jnu.ac.kr", "{bcrypt}hash", "후문호랑이", Gender.M, "컴퓨터공학과", 3, NOW);
    }

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("ACTIVE 상태·신고 0건으로 시작한다")
        void startsActive() {
            User user = verifiedUser();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getReportCount()).isZero();
            assertThat(user.getSuspendedUntil()).isNull();
        }

        @Test
        @DisplayName("인증 시각과 가입 시각이 생성 시각과 같다")
        void verifiedAtEqualsNow() {
            User user = verifiedUser();

            assertThat(user.getVerifiedAt()).isEqualTo(NOW);
            assertThat(user.getCreatedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("학과·학년은 선택 입력이라 없어도 된다")
        void departmentAndGradeAreOptional() {
            User user =
                    User.create("noopt@jnu.ac.kr", "{bcrypt}hash", "정문부엉이", Gender.F, null, null, NOW);

            assertThat(user.getDepartment()).isNull();
            assertThat(user.getGrade()).isNull();
        }

        @Test
        @DisplayName("입력한 값을 그대로 보관한다")
        void keepsGivenValues() {
            User user = verifiedUser();

            assertThat(user.getEmail()).isEqualTo("gachiga@jnu.ac.kr");
            assertThat(user.getNickname()).isEqualTo("후문호랑이");
            assertThat(user.getGender()).isEqualTo(Gender.M);
            assertThat(user.getDepartment()).isEqualTo("컴퓨터공학과");
            assertThat(user.getGrade()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("프로필 수정")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임·학과·학년을 바꾼다")
        void changesFields() {
            User user = verifiedUser();

            user.updateProfile("용봉동다람쥐", "전자공학과", 4);

            assertThat(user.getNickname()).isEqualTo("용봉동다람쥐");
            assertThat(user.getDepartment()).isEqualTo("전자공학과");
            assertThat(user.getGrade()).isEqualTo(4);
        }

        @Test
        @DisplayName("성별은 바뀌지 않는다 (FR-03)")
        void genderStaysTheSame() {
            User user = verifiedUser();

            user.updateProfile("용봉동다람쥐", "전자공학과", 4);

            assertThat(user.getGender()).isEqualTo(Gender.M);
        }

        @Test
        @DisplayName("학과·학년에 null 을 주면 지워진다")
        void clearsOptionalFields() {
            User user = verifiedUser();

            user.updateProfile("용봉동다람쥐", null, null);

            assertThat(user.getDepartment()).isNull();
            assertThat(user.getGrade()).isNull();
        }
    }

    @Nested
    @DisplayName("신고 반영 (T3-1)")
    class ApplyReport {

        @Test
        @DisplayName("1·2회는 카운트만 올리고 상태는 그대로다")
        void countsWithoutSuspending() {
            User user = verifiedUser();

            user.applyReport(NOW);
            user.applyReport(NOW);

            assertThat(user.getReportCount()).isEqualTo(2);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getSuspendedUntil()).isNull();
        }

        @Test
        @DisplayName("3회가 되면 그 시점부터 7일 정지된다 (E-05)")
        void suspendsOnThirdReport() {
            User user = verifiedUser();

            user.applyReport(NOW);
            user.applyReport(NOW);
            user.applyReport(NOW);

            assertThat(user.getReportCount()).isEqualTo(3);
            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            assertThat(user.getSuspendedUntil()).isEqualTo(NOW.plusDays(7));
        }

        @Test
        @DisplayName("정지 중에 또 신고를 받으면 정지 기간이 그 시점부터 다시 7일로 늘어난다")
        void extendsSuspensionOnFurtherReports() {
            User user = verifiedUser();
            user.applyReport(NOW);
            user.applyReport(NOW);
            user.applyReport(NOW);
            LocalDateTime later = NOW.plusDays(3);

            user.applyReport(later);

            assertThat(user.getReportCount()).isEqualTo(4);
            assertThat(user.getSuspendedUntil()).isEqualTo(later.plusDays(7));
        }
    }
}
