package com.ttn.tickets.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.jpa.domain.Specification;

import com.ttn.tickets.domain.Comment;
import com.ttn.tickets.domain.Ticket;
import com.ttn.tickets.domain.TicketStatus;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

public final class TicketSearchSpecifications {

    private static final Pattern CANONICAL_ID = Pattern.compile("(?i)^TKT-([1-9][0-9]{0,17})$");

    private TicketSearchSpecifications() {
    }

    public static Specification<Ticket> matching(String keyword, TicketStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            String trimmed = keyword == null ? "" : keyword.trim();
            if (!trimmed.isEmpty()) {
                predicates.add(keywordMatch(root, query, criteriaBuilder, trimmed));
            }
            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate keywordMatch(Root<Ticket> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder,
            String keyword) {
        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        List<Predicate> matches = new ArrayList<>();
        matches.add(like(criteriaBuilder, root.get("title"), pattern));
        matches.add(like(criteriaBuilder, root.get("description"), pattern));
        matches.add(like(criteriaBuilder, root.get("category").as(String.class), pattern));
        matches.add(like(criteriaBuilder, root.get("resolutionNotes"), pattern));
        matches.add(like(criteriaBuilder, root.get("assignee"), pattern));
        matches.add(commentMatch(root, query, criteriaBuilder, pattern));
        Matcher matcher = CANONICAL_ID.matcher(keyword);
        if (matcher.matches()) {
            matches.add(criteriaBuilder.equal(root.get("id"), Long.parseLong(matcher.group(1))));
        }
        return criteriaBuilder.or(matches.toArray(Predicate[]::new));
    }

    private static Predicate like(CriteriaBuilder criteriaBuilder, Expression<String> expression, String pattern) {
        return criteriaBuilder.like(criteriaBuilder.lower(expression), pattern, '\\');
    }

    private static Predicate commentMatch(Root<Ticket> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder,
            String pattern) {
        Subquery<Integer> comments = query.subquery(Integer.class);
        Root<Comment> comment = comments.from(Comment.class);
        comments.select(criteriaBuilder.literal(1));
        comments.where(
                criteriaBuilder.equal(comment.get("ticket"), root),
                like(criteriaBuilder, comment.get("text"), pattern));
        return criteriaBuilder.exists(comments);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
