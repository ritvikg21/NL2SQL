package com.example.nlsql.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * SchemaService:
 * - reads database metadata (tables, columns, PKs, FKs)
 * - provides a structured DatabaseSchema DTO (getDatabaseSchema)
 * - builds a compact, prompt-friendly schema string (getPromptDescription) and caches it
 *
 * Important:
 * - The prompt string is annotated with @Cacheable("schema") to store in Redis.
 * - We limit tables/columns and truncate to avoid giant prompts.
 */
@Service
public class SchemaService {

    private final DataSource dataSource;
    private static final int MAX_TABLES = 100;        // safety: don't list an enormous schema
    private static final int MAX_PROMPT_CHARS = 8000; // tune according to LLM context budget

    public SchemaService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Reads DB metadata and returns a structured DTO of tables/columns/keys.
     * Useful for an API or frontend schema viewer.
     */
    public DatabaseSchema getDatabaseSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schemaPattern = null;
            try { schemaPattern = conn.getSchema(); } catch (Throwable ignored) {}

            List<Table> tables = new ArrayList<>();
            try (ResultSet rsTables = md.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
                int count = 0;
                while (rsTables.next() && count < MAX_TABLES) {
                    String tableName = rsTables.getString("TABLE_NAME");
                    Table t = new Table();
                    t.name = tableName;
                    t.columns = readColumns(md, catalog, schemaPattern, tableName);
                    t.primaryKeys = readPrimaryKeys(md, catalog, schemaPattern, tableName);
                    t.foreignKeys = readForeignKeys(md, catalog, schemaPattern, tableName);
                    tables.add(t);
                    count++;
                }
            }
            DatabaseSchema schema = new DatabaseSchema();
            schema.tables = tables;
            return schema;
        }
    }

    /**
     * Build a compact prompt-friendly schema description.
     * Cached in Redis via @Cacheable("schema"). with some random value as schem,a chnges it chnges
     */
    @Cacheable(value = "schema", key = "'v1'")
    public String getPromptDescription() {
		try {
			DatabaseSchema schema = getDatabaseSchema();
            StringBuilder sb = new StringBuilder();
            sb.append("Database schema summary:\n");
            int tableCount = 0;
            for (Table t : schema.tables) {
                if (tableCount++ >= MAX_TABLES) break;
                sb.append("\nTable ").append(t.name).append(":\n");
                int displayedCols = 0;
                // show up to 40 columns per table to avoid long prompts
                for (Column c : t.columns) {
                    if (displayedCols++ > 40) { sb.append("- ... (more columns)\n"); break; }
                    String pkMark = t.primaryKeys.contains(c.name) ? " [PK]" : "";
                    String nullable = c.nullable ? "" : " NOT NULL";
                    sb.append("- ").append(c.name)
                      .append(" (").append(c.type)
                      .append(c.size > 0 ? ":" + c.size : "").append(")")
                      .append(pkMark).append(nullable).append("\n");
                }
                if (!t.foreignKeys.isEmpty()) {
                    sb.append("Foreign keys:\n");
                    for (ForeignKey fk : t.foreignKeys) {
                        sb.append("  - ").append(fk.fkColumn).append(" -> ")
                          .append(fk.pkTable).append(".").append(fk.pkColumn).append("\n");
                    }
                }
                // If description gets too long, truncate gracefully
                if (sb.length() > MAX_PROMPT_CHARS) {
                    sb.setLength(MAX_PROMPT_CHARS - 50);
                    sb.append("\n... (schema description truncated)\n");
                    break;
                }
            }
            return sb.toString();
        } catch (SQLException e) {
            // Return a safe fallback string (do not throw to calling LLM prompt building)
            return "Schema unavailable: failed to read DB metadata.";
        }
    }

    // === Helpers to read columns / keys ===
    private List<Column> readColumns(DatabaseMetaData md, String catalog, String schemaPattern, String tableName) throws SQLException {
        List<Column> cols = new ArrayList<>();
        try (ResultSet rs = md.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (rs.next()) {
                Column c = new Column();
                c.name = rs.getString("COLUMN_NAME");
                c.type = rs.getString("TYPE_NAME");
                c.size = rs.getInt("COLUMN_SIZE");
                c.nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                cols.add(c);
            }
        }
        return cols;
    }

    private List<String> readPrimaryKeys(DatabaseMetaData md, String catalog, String schemaPattern, String tableName) throws SQLException {
        List<String> pks = new ArrayList<>();
        try (ResultSet rs = md.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            while (rs.next()) pks.add(rs.getString("COLUMN_NAME"));
        }
        return pks;
    }

    private List<ForeignKey> readForeignKeys(DatabaseMetaData md, String catalog, String schemaPattern, String tableName) throws SQLException {
        List<ForeignKey> fks = new ArrayList<>();
        try (ResultSet rs = md.getImportedKeys(catalog, schemaPattern, tableName)) {
            while (rs.next()) {
                ForeignKey fk = new ForeignKey();
                fk.fkColumn = rs.getString("FKCOLUMN_NAME");
                fk.pkTable = rs.getString("PKTABLE_NAME");
                fk.pkColumn = rs.getString("PKCOLUMN_NAME");
                fks.add(fk);
            }
        }
        return fks;
    }

    // === DTOs used by this service ===
    public static class DatabaseSchema { public List<Table> tables = Collections.emptyList(); }

    public static class Table {
        public String name;
        public List<Column> columns = Collections.emptyList();
        public List<String> primaryKeys = Collections.emptyList();
        public List<ForeignKey> foreignKeys = Collections.emptyList();
    }

    public static class Column { public String name; public String type; public int size; public boolean nullable; }

    public static class ForeignKey { public String fkColumn; public String pkTable; public String pkColumn; }
}
