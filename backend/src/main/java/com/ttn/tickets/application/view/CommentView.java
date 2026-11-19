package com.ttn.tickets.application.view;

import java.time.Instant;

public record CommentView(String author, String text, Instant createdAt) {
}
