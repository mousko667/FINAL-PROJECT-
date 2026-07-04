package com.oct.invoicesystem.domain.department.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MAJEUR-9 / PROB-100: the public {@link DepartmentDTO} (returned by GET /departments and /{id},
 * which are only {@code isAuthenticated()} — SUPPLIER and ADMIN included) must never carry the
 * department {@code budget}, a financial figure reserved to DAF / ASSISTANT_COMPTABLE (SoD).
 *
 * <p>This test maps a budget-bearing entity through the real mapper and asserts the resulting DTO,
 * once serialized as the API would serialize it, exposes no {@code budget} field — so re-adding a
 * budget component to the DTO would fail here.</p>
 */
class DepartmentMapperBudgetLeakTest {

    private final DepartmentMapper mapper = Mappers.getMapper(DepartmentMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void toDto_doesNotExposeBudget_evenWhenDepartmentHasOne() throws Exception {
        Department department = Department.builder()
                .code("IT")
                .nameFr("Informatique")
                .nameEn("IT")
                .n1Role("ROLE_VALIDATEUR_N1_INFORMATIQUE")
                .budget(new BigDecimal("1500000.00"))
                .build();

        DepartmentDTO dto = mapper.toDto(department);

        JsonNode json = objectMapper.valueToTree(dto);
        assertThat(json.has("budget"))
                .as("public DepartmentDTO JSON must not contain a budget field (financial leak, SoD)")
                .isFalse();
        // Sanity: the non-financial fields are still mapped and serialized.
        assertThat(json.get("code").asText()).isEqualTo("IT");
    }
}
