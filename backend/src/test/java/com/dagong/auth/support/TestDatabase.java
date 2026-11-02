package com.dagong.auth.support;

import com.dagong.auth.account.AccountStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * DB 테스트용 embedded PostgreSQL입니다. 테스트 클래스의 @BeforeAll에서 한 번 띄워 실제 Flyway 마이그레이션을
 * 적용하고, 테스트마다 {@link #truncateAccounts()}로 비웁니다.
 */
public final class TestDatabase implements AutoCloseable {
    private final EmbeddedPostgres postgres;
    // getPostgresDatabase()는 부를 때마다 새 DataSource를 만듭니다. 트랜잭션이 JdbcTemplate에 적용되도록 하나만 씁니다.
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    private TestDatabase(EmbeddedPostgres postgres) {
        this.postgres = postgres;
        this.dataSource = postgres.getPostgresDatabase();
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public static TestDatabase start() throws IOException {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        Flyway.configure()
                .dataSource(postgres.getPostgresDatabase())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return new TestDatabase(postgres);
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** 운영과 같은 @Transactional 동작(롤백·noRollbackFor)을 확인하도록 Service를 트랜잭션 프록시로 감쌉니다. */
    @SuppressWarnings("unchecked")
    public <T> T transactional(T service) {
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                (TransactionManager) new DataSourceTransactionManager(dataSource),
                new AnnotationTransactionAttributeSource()));
        return (T) proxyFactory.getProxy();
    }

    /** 계정과 계정을 참조하는 표(소셜 연결, 서비스 가입, 권한 등)를 모두 비웁니다. */
    public void truncateAccounts() {
        jdbc.update("TRUNCATE accounts CASCADE");
    }

    public void insertAccount(UUID accountId, AccountStatus status) {
        // 탈퇴 완료 계정은 스키마 제약상 탈퇴 시각이 있어야 합니다.
        jdbc.update(
                """
                INSERT INTO accounts (user_id, user_nm, user_st, del_dt)
                VALUES (?, ?, ?, CASE WHEN ? = 'DELETED' THEN now() END)
                """,
                accountId,
                "맛객",
                status.name(),
                status.name());
    }

    @Override
    public void close() throws IOException {
        postgres.close();
    }
}
