package com.ttn.tickets.assistant.retrieval;

public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException(String message) {
        super(message);
    }
}