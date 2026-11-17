package org.gu.fms.desktop.platform.db.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptSplitterTest {
    @Test
    void splitSimpleStatementsAndDropsComments() {
        List<String> s = SqlScriptSplitter.split("""
            -- commentaire ; avec point-virgule
            CREATE TABLE a (id INTEGER);
            /* bloc ; */
            INSERT INTO a VALUES (1);
            -- fin
        """);

        assertEquals(2, s.size());
        assertTrue(s.get(0).endsWith("CREATE TABLE a (id INTEGER)"));
        assertTrue(s.get(1).endsWith("INSERT INTO a VALUES (1)"));
    }

    @Test
    void keepsSemicolonsInsideStringsAndIdentifiers() {
        List<String> s = SqlScriptSplitter.split("INSERT INTO t VALUES ('a;b', 'l''x;y'); SELECT \"c;d\" FROM t;");
        assertEquals(List.of("INSERT INTO t VALUES ('a;b', 'l''x;y')", "SELECT \"c;d\" FROM t"), s);
    }

    @Test
    void keepsTriggerBodyTogether() {
        List<String> s = SqlScriptSplitter.split("""
                CREATE TABLE t (end_date TEXT);
                CREATE TRIGGER trg BEFORE UPDATE ON t
                BEGIN
                    SELECT CASE WHEN NEW.end_date IS NULL THEN RAISE(ABORT, 'x;y') END;
                    SELECT 1;
                END;
                CREATE INDEX i ON t (end_date);
                """);
        assertEquals(3, s.size());
        assertTrue(s.get(1).startsWith("CREATE TRIGGER trg"));
        assertTrue(s.get(1).endsWith("END"));
        assertTrue(s.get(2).startsWith("CREATE INDEX"));
    }

    @Test
    void lastStatementWithoutSemicolon() {
        assertEquals(List.of("SELECT 1", "SELECT 2"), SqlScriptSplitter.split("SELECT 1;\nSELECT 2"));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(IllegalArgumentException.class, () -> SqlScriptSplitter.split("SELECT 'abc;"));
    }
}
