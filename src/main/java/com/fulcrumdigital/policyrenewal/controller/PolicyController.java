package com.fulcrumdigital.policyrenewal.controller;

import com.fulcrumdigital.policyrenewal.dto.CreatePolicyRequest;
import com.fulcrumdigital.policyrenewal.dto.PolicyResponse;
import com.fulcrumdigital.policyrenewal.dto.RenewPolicyRequest;
import com.fulcrumdigital.policyrenewal.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        PolicyResponse response = policyService.createPolicy(request);

        // Duplicate submissions get 200 OK (nothing new was created);
        // genuine new policies get 201 Created.
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @PutMapping("/{policyNumber}/renew")
    public ResponseEntity<PolicyResponse> renewPolicy(
            @PathVariable String policyNumber,
            @Valid @RequestBody RenewPolicyRequest request) {

        PolicyResponse response = policyService.renewPolicy(policyNumber, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{policyNumber}")
    public ResponseEntity<PolicyResponse> getPolicy(@PathVariable String policyNumber) {
        PolicyResponse response = policyService.getPolicy(policyNumber);
        return ResponseEntity.ok(response);
    }
}