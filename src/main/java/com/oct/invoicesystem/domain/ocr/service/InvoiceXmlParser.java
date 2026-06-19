package com.oct.invoicesystem.domain.ocr.service;

import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult;
import com.oct.invoicesystem.domain.ocr.dto.OcrExtractionResult.OcrLineItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses structured XML invoices (B8, M3) into the same {@link OcrExtractionResult} the OCR pipeline
 * produces, so the supplier confirmation flow is identical regardless of source.
 *
 * <p>It targets a simple OCT schema but matches elements by <b>local name anywhere</b> in the tree,
 * so it tolerates wrapping/namespaces and minor variations:</p>
 * <pre>{@code
 * <invoice>
 *   <number>FAC-2026-00099</number>
 *   <date>2026-06-18</date>
 *   <totalAmount>450000.00</totalAmount>
 *   <supplierTaxId>NIF123</supplierTaxId>
 *   <poReference>PO-2026-12</poReference>
 *   <lineItems>
 *     <item><description>Screen</description><quantity>2</quantity><unitPrice>175000</unitPrice></item>
 *   </lineItems>
 * </invoice>
 * }</pre>
 *
 * <p>XML is parsed with external entities and DOCTYPE disabled (XXE-safe).</p>
 */
@Slf4j
@Service
public class InvoiceXmlParser {

    public boolean isXml(String mimeType) {
        return "application/xml".equals(mimeType) || "text/xml".equals(mimeType);
    }

    /** Parses a single-invoice XML document into extracted fields. */
    public OcrExtractionResult parse(byte[] xmlBytes) {
        try {
            Document doc = secureBuilder().parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();
            Element root = firstInvoiceElement(doc);
            return toResult(root != null ? root : doc.getDocumentElement(), new String(xmlBytes));
        } catch (Exception e) {
            log.warn("XML invoice parse failed: {}", e.getMessage());
            return OcrExtractionResult.builder()
                    .rawText(new String(xmlBytes))
                    .digitalPdf(false)
                    .lineItems(List.of())
                    .build();
        }
    }

    /**
     * Parses an XML document that may contain several {@code <invoice>} elements (B8 multi-import).
     * Returns one result per invoice; a document with a single invoice (or none) yields one entry.
     */
    public List<OcrExtractionResult> parseMany(byte[] xmlBytes) {
        List<OcrExtractionResult> results = new ArrayList<>();
        try {
            Document doc = secureBuilder().parse(new ByteArrayInputStream(xmlBytes));
            doc.getDocumentElement().normalize();
            NodeList invoices = doc.getElementsByTagNameNS("*", "invoice");
            if (invoices.getLength() == 0) {
                results.add(toResult(doc.getDocumentElement(), new String(xmlBytes)));
            } else {
                for (int i = 0; i < invoices.getLength(); i++) {
                    results.add(toResult((Element) invoices.item(i), null));
                }
            }
        } catch (Exception e) {
            log.warn("XML multi-invoice parse failed: {}", e.getMessage());
        }
        return results;
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder();
    }

    private Element firstInvoiceElement(Document doc) {
        NodeList invoices = doc.getElementsByTagNameNS("*", "invoice");
        return invoices.getLength() > 0 ? (Element) invoices.item(0) : null;
    }

    private OcrExtractionResult toResult(Element scope, String rawText) {
        BigDecimal amount = null;
        String amountText = firstText(scope, "totalAmount", "amount", "total");
        if (amountText != null) {
            try {
                amount = new BigDecimal(amountText.replaceAll("[\\s,](?=\\d{3}\\b)", "").replace(",", "."));
            } catch (NumberFormatException ignored) {
                log.debug("XML amount not parseable: {}", amountText);
            }
        }
        return OcrExtractionResult.builder()
                .invoiceNumber(firstText(scope, "number", "invoiceNumber", "reference"))
                .invoiceDate(firstText(scope, "date", "invoiceDate", "issueDate"))
                .totalAmount(amount)
                .supplierId(firstText(scope, "supplierTaxId", "taxId", "supplierId", "nif"))
                .poReference(firstText(scope, "poReference", "purchaseOrder", "po"))
                .lineItems(extractLineItems(scope))
                .rawText(rawText)
                .digitalPdf(false)
                .build();
    }

    /** First non-blank text content of any descendant element whose local name matches one of {@code names}. */
    private String firstText(Element scope, String... names) {
        for (String name : names) {
            NodeList list = scope.getElementsByTagNameNS("*", name);
            for (int i = 0; i < list.getLength(); i++) {
                String text = list.item(i).getTextContent();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private List<OcrLineItem> extractLineItems(Element scope) {
        List<OcrLineItem> items = new ArrayList<>();
        NodeList nodes = scope.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < nodes.getLength() && items.size() < 100; i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element item = (Element) node;
            items.add(OcrLineItem.builder()
                    .description(firstText(item, "description", "label", "designation"))
                    .quantity(firstText(item, "quantity", "qty", "quantite"))
                    .unitPrice(firstText(item, "unitPrice", "price", "prixUnitaire"))
                    .build());
        }
        return items;
    }
}
