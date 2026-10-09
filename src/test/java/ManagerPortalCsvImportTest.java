import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ManagerPortalCsvImportTest {

    @Test
    void csvAndSqlSchemaIncludeEmployeeNameColumn() throws IOException {
        String schema = readResource("/database.sql");
        assertTrue(schema.contains("employee_name"), "database.sql should include employee_name in the employees table");

        String csv = readResource("/HR_Analytics_with_Employee_Names.csv");
        assertTrue(csv.contains("EmployeeName"), "CSV header should include EmployeeName");
        assertTrue(csv.contains("Arjun Kumar"), "CSV data should include the updated employee name");
    }

    private String readResource(String path) throws IOException {
        InputStream in = getClass().getResourceAsStream(path);
        assertTrue(in != null, "Resource should exist: " + path);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }
}
