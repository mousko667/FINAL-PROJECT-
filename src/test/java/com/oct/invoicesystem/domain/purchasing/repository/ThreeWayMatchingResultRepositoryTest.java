package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ThreeWayMatchingResultRepositoryTest {

    @Autowired ThreeWayMatchingResultRepository repository;

    @Test
    void findLatestPerInvoice_noFilter_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(null, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotNull();
    }

    @Test
    void findLatestPerInvoice_filterByStatus_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(MatchingStatus.MATCHED, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
    }
}
