package com.emphasis.transactionprocessor.controller;

import com.emphasis.transactionprocessor.TransactionProcessorApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = TransactionProcessorApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class TransactionApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @SuppressWarnings("rawtypes")
    void batchLookupSummaryAndHealthWork() {
        String batchJson = """
                [
                  {
                    "transactionId": "TXN-2002",
                    "requestId": "REQ-2",
                    "accountId": "ACC-201",
                    "transactionType": "DEBIT",
                    "amount": 40,
                    "currency": "USD",
                    "sequenceNumber": 2
                  },
                  {
                    "transactionId": "TXN-2001",
                    "requestId": "REQ-1",
                    "accountId": "ACC-201",
                    "transactionType": "CREDIT",
                    "amount": 100,
                    "currency": "USD",
                    "sequenceNumber": 1
                  },
                  {
                    "transactionId": "TXN-2002",
                    "requestId": "REQ-2-DUP",
                    "accountId": "ACC-201",
                    "transactionType": "DEBIT",
                    "amount": 40,
                    "currency": "USD",
                    "sequenceNumber": 2
                  },
                  {
                    "transactionId": "TXN-2003",
                    "requestId": "REQ-3",
                    "accountId": "ACC-201",
                    "transactionType": "CREDIT",
                    "amount": 25,
                    "currency": "USD",
                    "sequenceNumber": 3
                  },
                  {
                    "transactionId": "TXN-2004",
                    "requestId": "REQ-4",
                    "accountId": "ACC-202",
                    "transactionType": "DEBIT",
                    "amount": -10,
                    "currency": "USD",
                    "sequenceNumber": 4
                  },
                  {
                    "transactionId": "TXN-2005",
                    "requestId": "REQ-5",
                    "accountId": "ACC-203",
                    "transactionType": "CREDIT",
                    "amount": 500,
                    "currency": "USD",
                    "sequenceNumber": 5
                  }
                ]
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> batchResponse = restTemplate.postForEntity(
                url("/api/v1/transactions/batch"),
                new HttpEntity<>(batchJson, headers),
                Map.class
        );

        assertThat(batchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = batchResponse.getBody();
        assertThat(body).isNotNull();
        List<?> results = (List<?>) body.get("results");
        assertThat(results).hasSize(6);
        Map<?, ?> summary = (Map<?, ?>) body.get("summary");
        assertThat(summary.get("processed")).isEqualTo(3);
        assertThat(summary.get("duplicate")).isEqualTo(1);
        assertThat(summary.get("failed")).isEqualTo(1);
        assertThat(summary.get("pending")).isEqualTo(1);

        ResponseEntity<Map> lookup = restTemplate.getForEntity(url("/api/v1/transactions/TXN-2001"), Map.class);
        assertThat(lookup.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lookup.getBody()).isNotNull();
        assertThat(lookup.getBody().get("status")).isEqualTo("PROCESSED");
        assertThat(lookup.getBody().get("requestId")).isEqualTo("REQ-1");

        ResponseEntity<Map> missing = restTemplate.getForEntity(url("/api/v1/transactions/DOES-NOT-EXIST"), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().get("error")).isEqualTo("NOT_FOUND");
        assertThat(missing.getBody().get("path")).isEqualTo("/api/v1/transactions/DOES-NOT-EXIST");

        ResponseEntity<Map> summaryResponse = restTemplate.getForEntity(url("/api/v1/transactions/summary"), Map.class);
        assertThat(summaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(summaryResponse.getBody()).isNotNull();
        assertThat(summaryResponse.getBody().get("duplicate")).isEqualTo(1);

        ResponseEntity<Map> single = restTemplate.postForEntity(
                url("/api/v1/transactions"),
                new HttpEntity<>("""
                        {
                          "transactionId": "TXN-S1",
                          "requestId": "REQ-S1",
                          "accountId": "ACC-S",
                          "transactionType": "CREDIT",
                          "amount": 15,
                          "currency": "USD",
                          "sequenceNumber": 1
                        }
                        """, headers),
                Map.class
        );
        assertThat(single.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(single.getBody()).isNotNull();
        assertThat(single.getBody().get("status")).isEqualTo("PROCESSED");

        ResponseEntity<String> badJson = restTemplate.postForEntity(
                url("/api/v1/transactions/batch"),
                new HttpEntity<>("{not-json", headers),
                String.class
        );
        assertThat(badJson.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> health = restTemplate.getForEntity(url("/actuator/health"), Map.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).isNotNull();
        assertThat(health.getBody().get("status")).isEqualTo("UP");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
