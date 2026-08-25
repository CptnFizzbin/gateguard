package com.cptnfizzbin.keycard;

import org.junit.Test;

import com.cptnfizzbin.keycard.conditions.ConditionResolver;
import com.cptnfizzbin.keycard.conditions.Conditions;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ConditionResolverTest {
    private final ConditionResolver resolver = new ConditionResolver();

    static class Article {
        public final int id;
        public final int ownerId;
        public final String status;

        public Article(int id, int ownerId, String status) {
            this.id = id;
            this.ownerId = ownerId;
            this.status = status;
        }

        public int getId() { return id; }
        public int getOwnerId() { return ownerId; }
        public String getStatus() { return status; }
    }

    @Test
    public void testEqualityCondition() {
        assertTrue(resolver.evaluate(5, Map.of("$eq", 5)));
        assertFalse(resolver.evaluate(5, Map.of("$eq", 3)));
    }

    @Test
    public void testGreaterThanCondition() {
        assertTrue(resolver.evaluate(10, Map.of("$gt", 5)));
        assertFalse(resolver.evaluate(3, Map.of("$gt", 5)));
    }

    @Test
    public void testInCondition() {
        assertTrue(resolver.evaluate(2, Map.of("$in", List.of(1, 2, 3))));
        assertFalse(resolver.evaluate(5, Map.of("$in", List.of(1, 2, 3))));
    }

    @Test
    public void testHasCondition() {
        assertTrue(resolver.evaluate(List.of(1, 2, 3), Map.of("$has", 2)));
        assertFalse(resolver.evaluate(List.of(1, 2, 3), Map.of("$has", 5)));
    }

    @Test
    public void testNotCondition() {
        assertTrue(resolver.evaluate("draft", Map.of("$not", Map.of("$eq", "published"))));
        assertFalse(resolver.evaluate("published", Map.of("$not", Map.of("$eq", "published"))));
    }

    @Test
    public void testFieldCondition() {
        Article article = new Article(1, 42, "published");
        assertTrue(resolver.evaluate(article, Conditions.eq(Article::getOwnerId, 42)));
        assertFalse(resolver.evaluate(article, Conditions.eq(Article::getOwnerId, 99)));
    }

    @Test
    public void testNestedFieldEquality() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertTrue(resolver.evaluate(article, Map.of("owner", Map.of("name", "james"))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", "frank"))));
    }

    @Test
    public void testNestedFieldCondition() {
        Map<String, Object> user = Map.of("name", "james");
        Map<String, Object> article = Map.of("id", 1, "owner", user);

        assertTrue(resolver.evaluate(article, Map.of("owner", Map.of("name", Map.of("$ne", "frank")))));
        assertFalse(resolver.evaluate(article, Map.of("owner", Map.of("name", Map.of("$ne", "james")))));
    }
}
