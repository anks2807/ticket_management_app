package com.ttn.tickets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TicketStatusTransitionTest {

    @ParameterizedTest
    @MethodSource("allowedPairs")
    void allowsTheFiveLegalMoves(TicketStatus from, TicketStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("refusedPairs")
    void refusesTheTwentyIllegalMoves(TicketStatus from, TicketStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TicketStatus.class)
    void reportsTheMovesAllowedFromEachStatus(TicketStatus status) {
        assertThat(status.allowedTransitions()).containsExactlyInAnyOrderElementsOf(expectedAllowed(status));
    }

    private static Stream<Arguments> allowedPairs() {
        return Stream.of(
                Arguments.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
                Arguments.of(TicketStatus.OPEN, TicketStatus.CANCELLED),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                Arguments.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
                Arguments.of(TicketStatus.RESOLVED, TicketStatus.CLOSED));
    }

    private static Stream<Arguments> refusedPairs() {
        return Arrays.stream(TicketStatus.values())
                .flatMap(from -> Arrays.stream(TicketStatus.values())
                        .filter(to -> !isAllowed(from, to))
                        .map(to -> Arguments.of(from, to)));
    }

    private static Set<TicketStatus> expectedAllowed(TicketStatus status) {
        return switch (status) {
            case OPEN -> EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED);
            case IN_PROGRESS -> EnumSet.of(TicketStatus.RESOLVED, TicketStatus.CANCELLED);
            case RESOLVED -> EnumSet.of(TicketStatus.CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(TicketStatus.class);
        };
    }

    private static boolean isAllowed(TicketStatus from, TicketStatus to) {
        return expectedAllowed(from).contains(to);
    }
}
