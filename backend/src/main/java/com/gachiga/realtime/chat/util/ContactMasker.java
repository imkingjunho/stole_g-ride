package com.gachiga.realtime.chat.util;

import java.util.regex.Pattern;

/**
 * 채팅 메시지에서 연락처로 보이는 부분을 지운다 (FR-23, PRD §2.2).
 *
 * <p>닉네임만으로 매칭이 이뤄지는 서비스라, 채팅 중에 실수로 남긴 전화번호·이메일·카카오톡
 * 아이디까지 그대로 저장·전송되면 개인정보 최소 수집 원칙이 깨진다. 완벽한 탐지는 불가능하다 —
 * 표기 변형이 무한하기 때문이다. 그래도 흔한 형태는 걸러 낸다.
 */
public final class ContactMasker {

    private static final String MASK = "***";

    /** 010-1234-5678, 010.1234.5678, 011-123-4567(옛 번호), 01012345678 같은 휴대폰 번호 */
    private static final Pattern MOBILE_PHONE =
            Pattern.compile("01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

    /** 02-123-4567, 031-1234-5678 같은 지역번호 전화 */
    private static final Pattern LANDLINE_PHONE =
            Pattern.compile("0(2|[3-6][1-4])[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /**
     * "카톡"·"카카오톡" 뒤에 붙는 아이디만 가린다. 두 단어 자체는 평범한 대화에도 나오므로 남겨
     * 두고, 그 뒤에 오는 토큰만 연락처로 본다.
     */
    private static final Pattern KAKAO_ID =
            Pattern.compile(
                    "(카카오톡|카톡)\\s*(아이디|id|ID)?\\s*[:\\-]?\\s*([A-Za-z0-9][A-Za-z0-9_.]{3,19})");

    private ContactMasker() {
        throw new AssertionError("유틸리티 클래스는 인스턴스를 만들지 않는다");
    }

    /**
     * 연락처로 보이는 부분을 {@code ***} 로 바꾼 문자열을 돌려준다.
     *
     * @param content 원본 메시지. {@code null} 이면 그대로 {@code null}
     * @return 마스킹된 문자열. 연락처 패턴이 없으면 원본과 같다
     */
    public static String mask(String content) {
        if (content == null) {
            return null;
        }
        String masked = EMAIL.matcher(content).replaceAll(MASK);
        masked = MOBILE_PHONE.matcher(masked).replaceAll(MASK);
        masked = LANDLINE_PHONE.matcher(masked).replaceAll(MASK);
        masked = KAKAO_ID.matcher(masked).replaceAll(result -> result.group(1) + " " + MASK);
        return masked;
    }
}
