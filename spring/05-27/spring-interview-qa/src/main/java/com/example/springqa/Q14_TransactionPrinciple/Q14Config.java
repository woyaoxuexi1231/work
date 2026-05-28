package com.example.springqa.Q14_TransactionPrinciple;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class Q14Config {

    @Bean
    public DataSource q14_dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2).setName("q14_principle").build();
    }

    @Bean
    public PlatformTransactionManager q14_transactionManager(DataSource q14_dataSource) {
        return new DataSourceTransactionManager(q14_dataSource);
    }

    @Bean
    public JdbcTemplate q14_jdbcTemplate(DataSource q14_dataSource) {
        JdbcTemplate jt = new JdbcTemplate(q14_dataSource);
        jt.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(50))");
        return jt;
    }
}
