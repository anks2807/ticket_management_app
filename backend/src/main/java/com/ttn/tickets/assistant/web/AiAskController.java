package com.ttn.tickets.assistant.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttn.tickets.assistant.generation.AskService;
import com.ttn.tickets.assistant.web.dto.AskRequest;
import com.ttn.tickets.assistant.web.dto.AskResponse;

@RestController
@RequestMapping("/api/ai")
public class AiAskController {

    private final AskService askService;

    public AiAskController(AskService askService) {
        this.askService = askService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@RequestBody AskRequest request) {
        return askService.ask(request.question());
    }
}
