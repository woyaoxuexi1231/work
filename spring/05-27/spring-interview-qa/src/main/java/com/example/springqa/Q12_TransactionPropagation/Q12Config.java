package com.example.springqa.Q12_TransactionPropagation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class Q12Config {

    @Bean
    public DataSource q12_dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2).setName("q12_propagation").build();
    }

    @Bean
    public PlatformTransactionManager q12_transactionManager(DataSource q12_dataSource) {
        return new DataSourceTransactionManager(q12_dataSource);
    }

    @Bean
    public JdbcTemplate q12_jdbcTemplate(DataSource q12_dataSource) {
        return new JdbcTemplate(q12_dataSource);
    }
}
