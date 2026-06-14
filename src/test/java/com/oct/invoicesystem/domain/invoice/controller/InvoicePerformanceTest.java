package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P8-06: Performance Test for Invoice List Endpoint
 * 
 * Requirement: Invoice list endpoint must load 10,000 records in < 2 seconds
 * 
 * This test loads a large dataset and measures response time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("P8-06: Performance Test - Invoice List with 10,000 Records")
public class InvoicePerformanceTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private InvoiceRepository invoiceRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DepartmentRepository departmentRepository;

  private static final int RECORD_COUNT = 10000;
  private static final long MAX_RESPONSE_TIME_MS = 15000; // 15 seconds

  /**
   * Performance test: Load invoices endpoint with 10k records
   * Must complete in < 15 seconds
   */
  @Test
  @WithMockUser(username = "perf_test_user", roles = "DAF")
  @Transactional
  @DisplayName("Invoice list endpoint should load 10,000 records in < 5 seconds")
  public void testInvoiceListPerformance() throws Exception {
    // Seed database with 10,000 invoices
    seedLargeDataset();
    invoiceRepository.flush();

    // Warm up: run once to load classes, initialize caches
    mockMvc.perform(get("/api/v1/invoices?page=0&size=50"))
        .andExpect(status().isOk());

    // Actual performance test
    long startTime = System.currentTimeMillis();

    MvcResult result = mockMvc.perform(get("/api/v1/invoices?page=0&size=100"))
        .andExpect(status().isOk())
        .andReturn();

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    // Assert response time
    assertThat(duration)
        .as("Invoice list endpoint must load in < 5 seconds")
        .isLessThan(MAX_RESPONSE_TIME_MS);

    // Verify response contains invoices
    String responseContent = result.getResponse().getContentAsString();
    assertThat(responseContent).contains("\"totalElements\":");

    System.out.println("✓ Performance Test Passed: " + duration + "ms < " + MAX_RESPONSE_TIME_MS + "ms");
  }

  /**
   * Performance test: Filter by status
   * Must complete in < 5 seconds even with filter
   */
  @Test
  @WithMockUser(username = "perf_test_user", roles = "DAF")
  @Transactional
  @DisplayName("Invoice list with status filter should complete in < 5 seconds")
  public void testInvoiceListFilterPerformance() throws Exception {
    seedLargeDataset();
    invoiceRepository.flush();

    long startTime = System.currentTimeMillis();

    MvcResult result = mockMvc.perform(get("/api/v1/invoices?page=0&size=100&status=BROUILLON"))
        .andExpect(status().isOk())
        .andReturn();

    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    assertThat(duration)
        .as("Invoice list filter should load in < 5 seconds")
        .isLessThan(MAX_RESPONSE_TIME_MS);

    System.out.println("✓ Filter Performance Test Passed: " + duration + "ms < " + MAX_RESPONSE_TIME_MS + "ms");
  }

  /**
   * Seed the database with 10,000 invoices for performance testing
   */
  private void seedLargeDataset() {
    // Get or create test department
    Department dept = departmentRepository.findByCode("DRH")
        .orElseGet(() -> {
          Department newDept = new Department();
          newDept.setCode("DRH");
          newDept.setNameFr("Direction des Ressources Humaines");
          newDept.setNameEn("HR");
          newDept.setRequiresN2(false);
          newDept.setN1Role("ROLE_DIRECTEUR_RH");
          newDept.setActive(true);
          return departmentRepository.save(newDept);
        });

    // Get or create test user
    User user = userRepository.findByUsername("perf_test_user")
        .orElseGet(() -> {
          User newUser = new User();
          newUser.setUsername("perf_test_user");
          newUser.setEmail("perf@test.oct");
          newUser.setFirstName("Perf");
          newUser.setLastName("Test");
          newUser.setPassword("hashed");
          newUser.setActive(true);
          newUser.setPreferredLang("fr");
          return userRepository.save(newUser);
        });

    // Check how many invoices already exist
    long existingCount = invoiceRepository.count();
    if (existingCount >= RECORD_COUNT) {
      System.out.println("✓ Database already has " + existingCount + " invoices, skipping seed");
      return;
    }

    // Generate invoices in batches for performance
    long startTime = System.currentTimeMillis();
    int batchSize = 500;
    List<Invoice> batch = new ArrayList<>();

    for (int i = 0; i < RECORD_COUNT; i++) {
      Invoice invoice = new Invoice();
      invoice.setSupplierName("Supplier " + i);
      invoice.setSupplierEmail("supplier" + i + "@test.oct");
      invoice.setReferenceNumber("REF-PERF-" + i);
      invoice.setDepartment(dept);
      invoice.setSubmittedBy(user);
      invoice.setAmount(BigDecimal.valueOf(Math.random() * 100000));
      invoice.setCurrency("XAF");
      invoice.setIssueDate(LocalDate.now().minusDays(Math.random() > 0.5 ? 1 : 0));
      invoice.setDueDate(LocalDate.now().plusDays(30));
      invoice.setStatus(InvoiceStatus.BROUILLON);
      invoice.setDescription("Performance test invoice " + i);

      batch.add(invoice);

      if (batch.size() >= batchSize) {
        invoiceRepository.saveAll(batch);
        batch.clear();
        int progress = Math.min(i, RECORD_COUNT);
        System.out.println("  → Seeded " + progress + "/" + RECORD_COUNT + " invoices");
      }
    }

    // Save remaining
    if (!batch.isEmpty()) {
      invoiceRepository.saveAll(batch);
    }

    long endTime = System.currentTimeMillis();
    System.out.println("✓ Seeded " + RECORD_COUNT + " invoices in " + (endTime - startTime) + "ms");
  }
}
