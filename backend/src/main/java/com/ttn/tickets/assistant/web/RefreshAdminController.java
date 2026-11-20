package com.ttn.tickets.assistant.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ttn.tickets.assistant.refresh.KnowledgeRebuildService;
import com.ttn.tickets.assistant.refresh.RefreshStatusService;
import com.ttn.tickets.assistant.web.dto.RefreshStatusResponse;

@RestController
@RequestMapping("/api/ai/refresh")
public class RefreshAdminController {

    private final RefreshStatusService refreshStatusService;
    private final KnowledgeRebuildService knowledgeRebuildService;

    public RefreshAdminController(
            RefreshStatusService refreshStatusService, KnowledgeRebuildService knowledgeRebuildService) {
        this.refreshStatusService = refreshStatusService;
        this.knowledgeRebuildService = knowledgeRebuildService;
    }

    @GetMapping("/status")
    public RefreshStatusResponse status() {
        return refreshStatusService.status();
    }

    @PostMapping("/rebuild")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void rebuild() {
        knowledgeRebuildService.enqueueRebuildForAllTickets();
    }
}
