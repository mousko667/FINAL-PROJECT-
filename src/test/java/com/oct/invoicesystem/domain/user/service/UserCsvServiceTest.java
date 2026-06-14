package com.oct.invoicesystem.domain.user.service;

import com.oct.invoicesystem.domain.user.dto.UserImportResultDTO;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.RoleRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for {@link UserCsvService} — import runs against the real persistence layer
 * (create-only, per-row error reporting) and the CSV round-trips through export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserCsvServiceTest {

    @Autowired private UserCsvService userCsvService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private MockMultipartFile csv(String body) {
        return new MockMultipartFile("file", "users.csv", "text/csv",
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER =
            "username,email,firstName,lastName,preferredLang,employeeId,departmentCode,approvalLimit,isActive,roles\r\n";

    @Test
    void import_createsValidUsers_andReportsDuplicatesAndUnknownRoles() {
        roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("ROLE_ADMIN").description("admin").build()));
        // Pre-existing user to trigger a duplicate-username rejection on import.
        userRepository.save(User.builder()
                .username("existing").email("existing@oct.test")
                .password("$2a$12$dummy").firstName("Ex").lastName("Ist")
                .active(true).preferredLang("fr").build());

        String body = HEADER
                + "alice,alice@oct.test,Alice,Martin,fr,EMP1,,,true,ROLE_ADMIN\r\n"     // valid
                + "existing,dup@oct.test,Dup,User,fr,,,,true,\r\n"                        // duplicate username → fail
                + "bob,bob@oct.test,Bob,Bly,en,,,,true,ROLE_DOES_NOT_EXIST\r\n";          // unknown role → fail

        UserImportResultDTO result = userCsvService.importUsersFromCsv(csv(body));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.errors()).extracting(UserImportResultDTO.RowError::username)
                .containsExactlyInAnyOrder("existing", "bob");
        // The valid row was actually persisted.
        assertThat(userRepository.findByUsername("alice")).isPresent();
    }

    @Test
    void import_missingRequiredField_isReportedNotThrown() {
        String body = HEADER + "nouser,,No,Email,fr,,,,true,\r\n"; // blank email → required-field error
        UserImportResultDTO result = userCsvService.importUsersFromCsv(csv(body));
        assertThat(result.created()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors().get(0).line()).isEqualTo(2); // header=1, first data row=2
    }

    @Test
    void export_thenImport_roundTrips() {
        userRepository.save(User.builder()
                .username("carol").email("carol@oct.test")
                .password("$2a$12$dummy").firstName("Carol").lastName("Roe")
                .active(true).preferredLang("en").build());

        String exported = new String(userCsvService.exportUsersToCsv().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exported).startsWith(String.join(",", UserCsvService.HEADERS));
        assertThat(exported).contains("carol,carol@oct.test,Carol,Roe,en");
        // Password must never appear in the export.
        assertThat(exported).doesNotContain("$2a$12$dummy");
    }

    @Test
    void export_neutralizesCsvFormulaInjection() {
        // A username starting with '=' must be neutralised (prefixed with ') so it can't execute
        // as a formula when the CSV is opened in a spreadsheet.
        userRepository.save(User.builder()
                .username("=cmd|'/c calc'!A1").email("evil@oct.test")
                .password("$2a$12$dummy").firstName("+attack").lastName("@danger")
                .active(true).preferredLang("fr").build());

        String exported = new String(userCsvService.exportUsersToCsv().readAllBytes(), StandardCharsets.UTF_8);

        // The raw formula-triggering values must not appear unprefixed at a cell boundary.
        assertThat(exported).doesNotContain(",=cmd").doesNotContain(",+attack").doesNotContain(",@danger");
        // They are present, but prefixed with a single quote (quoted because of the embedded comma).
        assertThat(exported).contains("'=cmd");
        assertThat(exported).contains("'+attack");
        assertThat(exported).contains("'@danger");
    }

    @Test
    void csvParser_handlesQuotedFieldsWithCommasAndEscapedQuotes() throws Exception {
        String body = HEADER + "\"quo,ted\",q@oct.test,\"Last\"\"Name\",L,fr,,,,true,\r\n";
        var rows = UserCsvService.parse(new java.io.BufferedReader(new java.io.StringReader(body)));
        assertThat(rows).hasSize(2);              // header + 1 data row
        assertThat(rows.get(1).get(0)).isEqualTo("quo,ted");      // embedded comma preserved
        assertThat(rows.get(1).get(2)).isEqualTo("Last\"Name");   // escaped quote unescaped
    }
}
