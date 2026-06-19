package com.oct.invoicesystem.domain.ocr.service;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for the structured XML invoice parser (B8). */
class InvoiceXmlParserTest {

    private final InvoiceXmlParser parser = new InvoiceXmlParser();

    @Test
    void parsesSingleInvoiceFields_andLineItems() {
        String xml = """
                <invoice>
                  <number>FAC-2026-00099</number>
                  <date>2026-06-18</date>
                  <totalAmount>450000.00</totalAmount>
                  <supplierTaxId>NIF123</supplierTaxId>
                  <poReference>PO-2026-12</poReference>
                  <lineItems>
                    <item><description>Screen</description><quantity>2</quantity><unitPrice>175000</unitPrice></item>
                    <item><description>Keyboard</description><quantity>1</quantity><unitPrice>100000</unitPrice></item>
                  </lineItems>
                </invoice>
                """;
        OcrExtractionResult r = parser.parse(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(r.getInvoiceNumber()).isEqualTo("FAC-2026-00099");
        assertThat(r.getInvoiceDate()).isEqualTo("2026-06-18");
        assertThat(r.getTotalAmount()).isEqualByComparingTo(new BigDecimal("450000.00"));
        assertThat(r.getSupplierId()).isEqualTo("NIF123");
        assertThat(r.getPoReference()).isEqualTo("PO-2026-12");
        assertThat(r.getLineItems()).hasSize(2);
        assertThat(r.getLineItems().get(0).getDescription()).isEqualTo("Screen");
    }

    @Test
    void parseMany_returnsOneResultPerInvoiceElement() {
        String xml = """
                <invoices>
                  <invoice><number>A1</number><date>2026-01-01</date><totalAmount>1000</totalAmount><supplierTaxId>T1</supplierTaxId></invoice>
                  <invoice><number>A2</number><date>2026-02-02</date><totalAmount>2000</totalAmount><supplierTaxId>T2</supplierTaxId></invoice>
                </invoices>
                """;
        List<OcrExtractionResult> results = parser.parseMany(xml.getBytes(StandardCharsets.UTF_8));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(OcrExtractionResult::getInvoiceNumber).containsExactly("A1", "A2");
        assertThat(results.get(1).getTotalAmount()).isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void parse_isXxeSafe_doesNotResolveExternalEntities() {
        // A DOCTYPE/ENTITY-bearing document must not blow up or expand entities (XXE guard).
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE invoice [ <!ENTITY x "EXPANDED"> ]>
                <invoice><number>&x;</number><date>2026-03-03</date><totalAmount>500</totalAmount></invoice>
                """;
        OcrExtractionResult r = parser.parse(xml.getBytes(StandardCharsets.UTF_8));
        // disallow-doctype-decl makes parsing fail safely → empty result, never "EXPANDED".
        assertThat(r.getInvoiceNumber()).isNotEqualTo("EXPANDED");
    }
}
