package com.oct.invoicesystem.domain.webhook.service;

import com.oct.invoicesystem.domain.webhook.dto.IntegrationConnectorDTO;
import com.oct.invoicesystem.domain.webhook.model.IntegrationConnector;
import com.oct.invoicesystem.domain.webhook.repository.IntegrationConnectorRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationConnectorServiceTest {

    @Mock private IntegrationConnectorRepository repository;
    @InjectMocks private IntegrationConnectorService service;

    @Test
    void create_rejectsBadType() {
        assertThrows(ValidationException.class, () -> service.create(
                new IntegrationConnectorDTO.Request("SAP link", "SALESFORCE", null, null), null));
    }

    @Test
    void create_acceptsMockAndDefaultsStatusUnknown() {
        when(repository.save(any(IntegrationConnector.class))).thenAnswer(i -> i.getArgument(0));
        var dto = service.create(new IntegrationConnectorDTO.Request("Demo", "mock", null, null), null);
        assertEquals("MOCK", dto.type());
        assertEquals("UNKNOWN", dto.lastStatus());
    }

    @Test
    void testConnection_mockReportsUp() {
        UUID id = UUID.randomUUID();
        IntegrationConnector mock = IntegrationConnector.builder().id(id).name("Demo").type("MOCK").build();
        when(repository.findById(id)).thenReturn(Optional.of(mock));
        when(repository.save(any(IntegrationConnector.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.testConnection(id);

        assertEquals("UP", dto.lastStatus());
    }

    @Test
    void testConnection_rejectsLoopbackEndpoint_ssrfGuard() {
        UUID id = UUID.randomUUID();
        IntegrationConnector erp = IntegrationConnector.builder()
                .id(id).name("ERP").type("ERP").endpoint("http://127.0.0.1:8080/internal").build();
        when(repository.findById(id)).thenReturn(Optional.of(erp));
        when(repository.save(any(IntegrationConnector.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.testConnection(id);

        assertEquals("DOWN", dto.lastStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                dto.lastMessage().toLowerCase().contains("internal") || dto.lastMessage().toLowerCase().contains("non-routable"),
                "should be blocked by SSRF guard, was: " + dto.lastMessage());
    }

    @Test
    void testConnection_realTypeWithoutEndpointIsUnknown() {
        UUID id = UUID.randomUUID();
        IntegrationConnector erp = IntegrationConnector.builder().id(id).name("ERP").type("ERP").build();
        when(repository.findById(id)).thenReturn(Optional.of(erp));
        when(repository.save(any(IntegrationConnector.class))).thenAnswer(i -> i.getArgument(0));

        var dto = service.testConnection(id);

        assertEquals("UNKNOWN", dto.lastStatus());
    }
}
