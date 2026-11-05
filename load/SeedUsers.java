import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCrypt;

/** 격리된 로컬 DB용 SQL을 생성한다. 비밀번호 원문은 출력하지 않는다. */
class SeedUsers {
    public static void main(String[] args) {
        int count = integer("VUS", 200, 1000);
        int first = integer("FIRST_USER_NUMBER", 11, 999000);
        String hash = System.getenv("LOAD_TEST_PASSWORD_HASH");
        String password = System.getenv("LOAD_TEST_PASSWORD");
        if (hash == null || hash.isBlank()) {
            if (password == null || password.isBlank()
                    || password.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("LOAD_TEST_PASSWORD에 1~72바이트 비밀번호를 지정하세요.");
            }
            hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        } else if (!hash.matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalArgumentException("LOAD_TEST_PASSWORD_HASH가 BCrypt 형식이 아닙니다.");
        } else if (password != null && !BCrypt.checkpw(password, hash)) {
            throw new IllegalArgumentException("LOAD_TEST_PASSWORD와 해시가 일치하지 않습니다.");
        }

        System.out.println("-- 격리된 로컬 부하 테스트 DB 전용. 생성된 해시 파일은 커밋하지 않는다.");
        System.out.println("SET NAMES utf8mb4;\nSTART TRANSACTION;");
        for (int i = 0; i < count; i++) {
            String number = String.format("%03d", first + i);
            String email = "load" + number + "@jnu.ac.kr";
            String gender = i % 2 == 0 ? "M" : "F";
            // PK는 DB에 맡긴다. 이메일이 있는 경우에만 아래 UPDATE로 재실행 비밀번호를 맞춘다.
            System.out.printf("INSERT INTO users (created_at, department, email, gender, grade, nickname, "
                            + "password_hash, report_count, status, suspended_until, verified_at) "
                            + "SELECT NOW(6), NULL, '%s', '%s', NULL, 'k6-load-%s', '%s', 0, 'ACTIVE', NULL, NOW(6) "
                            + "WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = '%s');%n",
                    email, gender, number, hash, email);
            System.out.printf("UPDATE users SET password_hash = '%s', status = 'ACTIVE', "
                            + "report_count = 0, suspended_until = NULL WHERE email = '%s';%n", hash, email);
        }
        System.out.println("COMMIT;");
    }

    private static int integer(String name, int fallback, int max) {
        String raw = System.getenv(name);
        int value = raw == null ? fallback : Integer.parseInt(raw);
        if (value < 1 || value > max) throw new IllegalArgumentException(name + " 범위를 확인하세요.");
        return value;
    }
}
