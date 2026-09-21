package com.gachiga.realtime.chat.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link ContactMasker} 검증 (FR-23). DB 없이 도는 순수 로직 테스트다 (CLAUDE.md §7). */
class ContactMaskerTest {

    @Nested
    @DisplayName("휴대폰 번호")
    class MobilePhone {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "010-1234-5678",
                    "010.1234.5678",
                    "010 1234 5678",
                    "01012345678",
                    "011-123-4567",
                    "016-1234-5678"
                })
        @DisplayName("여러 표기 변형을 전부 가린다")
        void masksVariants(String phone) {
            assertThat(ContactMasker.mask(phone)).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("지역번호 전화")
    class Landline {

        @Test
        @DisplayName("서울(02)")
        void masksSeoul() {
            assertThat(ContactMasker.mask("02-123-4567")).isEqualTo("***");
        }

        @Test
        @DisplayName("경기(031)")
        void masksGyeonggi() {
            assertThat(ContactMasker.mask("031-1234-5678")).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("이메일")
    class Email {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "test@example.com",
                    "hong.gildong123@jnu.ac.kr",
                    "a_b-c@sub.domain.co.kr"
                })
        @DisplayName("여러 형태를 전부 가린다")
        void masksVariants(String email) {
            assertThat(ContactMasker.mask(email)).isEqualTo("***");
        }
    }

    @Nested
    @DisplayName("카카오톡 아이디")
    class KakaoId {

        @Test
        @DisplayName("\"카톡 아이디 ...\" 형태")
        void masksWithLabel() {
            assertThat(ContactMasker.mask("카톡 아이디 abc1234")).isEqualTo("카톡 ***");
        }

        @Test
        @DisplayName("\"카카오톡: ...\" 형태")
        void masksWithColon() {
            assertThat(ContactMasker.mask("카카오톡: hello_world")).isEqualTo("카카오톡 ***");
        }

        @Test
        @DisplayName("\"카톡 id:...\" 형태")
        void masksWithIdLabel() {
            assertThat(ContactMasker.mask("카톡 id:myid123")).isEqualTo("카톡 ***");
        }
    }

    @Nested
    @DisplayName("문장 속에 섞여 있을 때")
    class WithinSentence {

        @Test
        @DisplayName("전화번호만 가리고 나머지 문장은 남긴다")
        void masksPhoneKeepsRest() {
            assertThat(ContactMasker.mask("연락처는 010-1234-5678 입니다"))
                    .isEqualTo("연락처는 *** 입니다");
        }

        @Test
        @DisplayName("이메일만 가리고 나머지 문장은 남긴다")
        void masksEmailKeepsRest() {
            assertThat(ContactMasker.mask("이메일로 보내주세요 test@example.com 감사합니다"))
                    .isEqualTo("이메일로 보내주세요 *** 감사합니다");
        }

        @Test
        @DisplayName("전화번호·이메일이 함께 있으면 둘 다 가린다")
        void masksBothWhenMixed() {
            assertThat(ContactMasker.mask("번호 010-1234-5678이랑 이메일 test@example.com 둘 다요"))
                    .isEqualTo("번호 ***이랑 이메일 *** 둘 다요");
        }
    }

    @Nested
    @DisplayName("연락처가 아닌 텍스트")
    class NotAContact {

        @Test
        @DisplayName("평범한 문장은 그대로 둔다")
        void keepsPlainSentence() {
            assertThat(ContactMasker.mask("안녕하세요 반갑습니다")).isEqualTo("안녕하세요 반갑습니다");
        }

        @Test
        @DisplayName("퀵 메시지 문구는 그대로 둔다")
        void keepsQuickMessage() {
            assertThat(ContactMasker.mask("도착했어요")).isEqualTo("도착했어요");
        }

        @Test
        @DisplayName("날짜처럼 생긴 숫자는 전화번호로 보지 않는다")
        void doesNotMatchDates() {
            assertThat(ContactMasker.mask("2024-01-01")).isEqualTo("2024-01-01");
        }
    }

    @Test
    @DisplayName("null 은 null 그대로")
    void nullStaysNull() {
        assertThat(ContactMasker.mask(null)).isNull();
    }
}
