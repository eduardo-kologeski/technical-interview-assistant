package com.kologeski.interviewassistant;

import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresqlTestDatabase {
    private PostgresqlTestDatabase() { }

    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE users CASCADE");
        jdbc.update("INSERT INTO companies(id,name,slug,created_at,updated_at) VALUES " +
                "('00000000-0000-0000-0000-000000000001','Ambiente legado','ambiente-legado',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON CONFLICT DO NOTHING");
        jdbc.update("UPDATE installation_state SET setup_completed=FALSE, completed_at=NULL WHERE id=1");
    }
}
