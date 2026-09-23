package com.gachiga.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>모듈 경계 검사.</b> 이 테스트가 "각자 올려도 빌드가 안 깨지는" 구조를 지탱한다 (PRD §9.3·§9.4).
 *
 * <p>리뷰 없이 main 에 직접 push 하는 방식이라, 남의 패키지에 의존하는 코드를 사람이 막아 줄 수 없다.
 * 대신 여기서 막는다. 규칙을 어기면 {@code ./gradlew build} 가 실패하므로 올리기 전에 본인이 알게 된다.
 *
 * <p>규칙은 두 가지다.
 *
 * <ol>
 *   <li><b>다른 도메인 패키지를 import 하지 않는다.</b> 허용되는 것은 {@code contract}·{@code common}·
 *       {@code config} 뿐이다. 다른 모듈의 기능이 필요하면 {@code contract} 의 port 를 주입받는다
 *   <li><b>엔티티는 다른 모듈의 엔티티를 JPA 관계로 참조하지 않는다.</b> {@code Long userId} 처럼
 *       ID 만 저장한다 (PRD §7.2). 그래야 상대 엔티티가 아직 없어도 내 모듈이 컴파일된다
 * </ol>
 *
 * <p><b>규칙에 걸렸다면</b> 우회할 방법을 찾지 말고 {@code contract} 에 port 가 있는지 보라.
 * 없으면 단톡방에 {@code [계약 변경 요청]} 을 보낸다 (CLAUDE.md §3).
 */
class ArchitectureTest {

    private static final String ROOT = "com.gachiga";

    /** 모듈 1개 = 사람 1명 (PRD §9.2) */
    private static final String[] DOMAIN_MODULES = {
        "ride", "matching", "fare", "stats", "auth", "user", "realtime", "route"
    };

    /**
     * 도메인 모듈이 import 해도 되는 패키지. CLAUDE.md §4·PRD §9.3 이 정한 대로 두 개뿐이다.
     *
     * <p>{@code config} 는 여기 없다. 스프링이 알아서 읽어 가는 설정 모음이지 라이브러리가 아니다 —
     * 도메인 코드가 직접 import 할 일이 없다.
     */
    private static final String[] SHARED_PACKAGES = {"contract", "common"};

    private final JavaClasses classes =
            new ClassFileImporter()
                    // 테스트 클래스는 검사 대상이 아니다
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(ROOT);

    @Test
    @DisplayName("도메인 모듈은 다른 도메인 모듈을 import 하지 않는다")
    void domainModulesDoNotDependOnEachOther() {
        for (String module : DOMAIN_MODULES) {
            String[] forbidden = forbiddenPackagesFor(module);

            ArchRule rule =
                    noClasses()
                            .that()
                            .resideInAPackage(ROOT + "." + module + "..")
                            .should()
                            .dependOnClassesThat()
                            .resideInAnyPackage(forbidden)
                            .because(
                                    module
                                            + " 모듈이 다른 모듈의 클래스를 직접 쓰고 있다. "
                                            + "허용되는 것은 contract 와 common 뿐이다. 다른 모듈의 기능이 "
                                            + "필요하면 contract 의 port 를 주입받고, 필요한 port 가 없으면 "
                                            + "단톡방에 [계약 변경 요청]을 보낸다 (CLAUDE.md §3·§4)")
                            // fare·stats 처럼 아직 클래스가 없는 모듈이 있다. 그때는 검사할 것이 없을 뿐 위반은 아니다
                            .allowEmptyShould(true);

            rule.check(classes);
        }
    }

    @Test
    @DisplayName("contract 는 어떤 도메인 모듈도 import 하지 않는다 (의존 방향은 도메인 → 계약)")
    void contractDoesNotDependOnDomainModules() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ROOT + ".contract..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(allModulePackages())
                        .because(
                                "contract 가 특정 모듈을 알게 되면 계약이 그 모듈에 묶여 동결의 의미가 사라진다");

        rule.check(classes);
    }

    @Test
    @DisplayName("common 은 어떤 도메인 모듈도 import 하지 않는다")
    void commonDoesNotDependOnDomainModules() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage(ROOT + ".common..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(allModulePackages())
                        .because("common 은 모두가 쓰는 바닥이므로 위쪽을 알아서는 안 된다");

        rule.check(classes);
    }

    @Test
    @DisplayName("엔티티는 다른 엔티티를 JPA 관계로 참조하지 않는다 — ID 만 저장한다 (PRD §7.2)")
    void entitiesReferenceOtherEntitiesByIdOnly() {
        ArchRule rule =
                fields()
                        .that()
                        .areDeclaredInClassesThat()
                        .areAnnotatedWith(Entity.class)
                        .should()
                        .notBeAnnotatedWith(ManyToOne.class)
                        .andShould()
                        .notBeAnnotatedWith(OneToMany.class)
                        .andShould()
                        .notBeAnnotatedWith(ManyToMany.class)
                        .andShould()
                        .notBeAnnotatedWith(OneToOne.class)
                        .because(
                                "엔티티 사이를 JPA 관계로 묶으면 상대 모듈이 아직 엔티티를 안 만들었을 때 "
                                        + "내 코드가 컴파일되지 않는다. Long userId 처럼 ID 만 저장하고 "
                                        + "사용자 정보가 필요하면 contract 의 UserPort 를 쓴다 (PRD §7.2)")
                        // 아직 엔티티가 하나도 없다. 생기기 전까지 빈 결과를 허용한다
                        .allowEmptyShould(true);

        rule.check(classes);
    }

    /**
     * {@code module} 이 import 하면 안 되는 패키지 — 다른 도메인 모듈 전부와 {@code config}.
     */
    private String[] forbiddenPackagesFor(String module) {
        return java.util.stream.Stream.concat(
                        java.util.Arrays.stream(DOMAIN_MODULES)
                                .filter(other -> !other.equals(module))
                                .map(other -> ROOT + "." + other + ".."),
                        java.util.stream.Stream.of(ROOT + ".config.."))
                .toArray(String[]::new);
    }

    /** 도메인 모듈 전체의 패키지 경로 */
    private String[] allModulePackages() {
        return java.util.Arrays.stream(DOMAIN_MODULES)
                .map(module -> ROOT + "." + module + "..")
                .toArray(String[]::new);
    }

    /**
     * 계정 port 는 auth·user 만 쓴다.
     *
     * <p>{@code contract} 는 전원에게 열려 있어서, 계정 생성·비밀번호 검증 port 를 두면 어느 모듈이든 부를 수 있다.
     * 가입·로그인은 auth 의 일이고 구현은 user 의 일이므로, 그 둘 밖에서 이 타입들을 건드리면 여기서 막는다.
     * {@code config}·{@code common} 도 대상이다 — 도메인이 아니라도 계정을 만들 이유는 없다.
     *
     * <p>{@code contract.user} 에 계정 관련 타입을 추가하면 아래 정규식도 같이 고친다.
     */
    @Test
    @DisplayName("계정 port(UserAccountPort·NewAccount·AccountProfile·CanonicalEmail)는 auth·user 밖에서 쓰지 않는다")
    void accountPortIsOnlyForAuthAndUser() {
        java.util.List<String> others =
                new java.util.ArrayList<>(
                        java.util.Arrays.stream(DOMAIN_MODULES)
                                .filter(m -> !m.equals("auth") && !m.equals("user"))
                                .map(m -> ROOT + "." + m + "..")
                                .toList());
        others.add(ROOT + ".config..");
        others.add(ROOT + ".common..");

        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage(others.toArray(String[]::new))
                        .should()
                        .dependOnClassesThat()
                        .haveNameMatching(
                                ROOT
                                        + "\\.contract\\.user\\."
                                        + "(UserAccountPort|NewAccount|AccountProfile|CanonicalEmail)")
                        .because(
                                "계정 생성·비밀번호 검증은 auth 가 부르고 user 가 구현한다. 다른 모듈은 "
                                        + "UserPort(읽기)만 쓴다 (PRD §14.1)")
                        .allowEmptyShould(true);

        rule.check(classes);
    }

    /**
     * 공용 패키지 목록은 규칙 자체에는 쓰이지 않지만, 무엇이 허용되는지 코드로 남겨 둔다.
     * 새 공용 패키지를 만들 일이 생기면 여기와 위 규칙을 함께 고쳐야 한다.
     */
    @Test
    @DisplayName("허용된 공용 패키지(contract·common)가 실제로 존재한다")
    void sharedPackagesExist() {
        for (String shared : SHARED_PACKAGES) {
            boolean exists =
                    classes.stream()
                            .anyMatch(c -> c.getPackageName().startsWith(ROOT + "." + shared));
            org.assertj.core.api.Assertions.assertThat(exists)
                    .as("공용 패키지 %s 가 존재해야 한다", shared)
                    .isTrue();
        }
    }
}
