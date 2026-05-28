package com.example.springqa.Q13_TransactionFailure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class Q13Config {

    @Bean
    public DataSource q13_dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2).setName("q13_failure").build();
    }

    @Bean
    public PlatformTransactionManager q13_transactionManager(DataSource q13_dataSource) {
        return new DataSourceTransactionManager(q13_dataSource);
    }

    @Bean
    public JdbcTemplate q13_jdbcTemplate(DataSource q13_dataSource) {
        JdbcTemplate jt = new JdbcTemplate(q13_dataSource);
        jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
        return jt;
    }
}
