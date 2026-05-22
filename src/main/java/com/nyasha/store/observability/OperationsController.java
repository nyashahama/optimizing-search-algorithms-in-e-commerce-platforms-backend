package com.nyasha.store.observability;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops")
public class OperationsController {

    private final OperationalStatusService operationalStatusService;

    public OperationsController(OperationalStatusService operationalStatusService) {
        this.operationalStatusService = operationalStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<OperationalStatusSnapshot> status() {
        return ResponseEntity.ok(operationalStatusService.getStatus());
    }
}
