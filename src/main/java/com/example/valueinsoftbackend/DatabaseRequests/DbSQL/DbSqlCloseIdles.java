/*
 * Copyright (c) Samir Filifl
 */

package com.example.valueinsoftbackend.DatabaseRequests.DbSQL;

import com.example.valueinsoftbackend.SqlConnection.ConnectionPostgres;
import com.example.valueinsoftbackend.ValueinsoftBackendApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.Connection;
import java.sql.PreparedStatement;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "Scheduling.enable" , matchIfMissing = true)
public  class  DbSqlCloseIdles {

    static public boolean terminate() {
        try {

            Connection conn = ConnectionPostgres.getConnection();
            PreparedStatement stmt = conn.prepareStatement("" +
                    "WITH inactive_connections AS (\n" +
                    "    SELECT\n" +
                    "        pid\n" +
                    "    FROM \n" +
                    "        pg_stat_activity\n" +
                    "    WHERE\n" +
                    "        -- Exclude the thread owned connection (ie no auto-kill)\n" +
                    "        pid <> pg_backend_pid( )\n" +
                    "    AND\n" +
                    "        -- Exclude known applications connections\n" +
                    "        application_name !~ '(?:psql)|(?:pgAdmin.+)'\n" +
                    "    AND\n" +
                    "\n" +
                    "        -- Include inactive connections only\n" +
                    "        state in ('idle', 'idle in transaction', 'idle in transaction (aborted)', 'disabled') \n" +
                    "    AND\n" +
                    "        -- Include old connections (found with the state_change field)\n" +
                    "        current_timestamp - state_change > interval '9 seconds' \n" +
                    ")\n" +
                    "SELECT\n" +
                    "    pg_terminate_backend(pid)\n" +
                    "FROM\n" +
                    "    inactive_connections ;\n");

            boolean i = stmt.execute();
            System.out.println("Termination Idles :"+i);
            stmt.close();
            conn.close();
        } catch (Exception e) {
            System.out.println("idle terminate error "+e.getMessage());
            return false;
        }
        return true;
    }
}
