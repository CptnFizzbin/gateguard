package com.cptnfizzbin.keycard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.conditions.Conditions;
import com.cptnfizzbin.keycard.conditions.Operator;
import com.cptnfizzbin.keycard.errors.PolicyException;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

public class PolicyTest {
    @Getter
    @AllArgsConstructor
    static class Article {
        private final int id;
        private final int ownerId;
        private final String status;
    }

    @Test
    public void testCanCheckByDefinition() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> create = ActionFactory.create("Create");

        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .build();

        assertTrue(policy.can(create, article));
    }

    @Test
    public void testCanCheckByReference() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Article data = new Article(1, 42, "published");
        Policy policy = new PolicyBuilder()
            .allow(update, article, Conditions.eq(Article::getOwnerId, 42))
            .build();

        assertTrue(policy.can(update, article.wrap(data)));
    }

    @Test
    public void testCannotCheck() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .build();

        assertTrue(policy.cannot(delete, article));
    }

    @Test
    public void testRequireAllowed() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> create = ActionFactory.create("Create");

        Policy policy = new PolicyBuilder()
            .allow(create, article)
            .build();

        // Should not throw
        policy.require(create, article);
    }

    @Test(expected = PolicyException.class)
    public void testRequireDenied() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .build();

        policy.require(delete, article);
    }

    @Test
    public void testDenyOverridesAllow() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article)
            .deny(delete, article)
            .build();

        assertFalse(policy.can(delete, article));
        assertTrue(policy.cannot(delete, article));
    }

    @Test
    public void testDenyWithConditionOnlyOverridesWhenItMatches() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article)
            .deny(delete, article, Map.of("status", "archived"))
            .build();

        assertFalse(policy.can(delete, article.wrap(new Article(1, 1, "archived"))));
        assertTrue(policy.can(delete, article.wrap(new Article(1, 1, "published"))));
    }

    @Test
    public void testLastRuleWinsReopensWhatAnEarlierDenyClosed() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .deny(delete, article)
            .allow(delete, article)
            .build();

        assertTrue(policy.can(delete, article));
    }

    /**
     * Issue 6: `allow`/`deny`/`can`/`cannot`/`require` all accept every
     * combination of `String`/`Action<?>` (action) and `String`/`SubjectDef<?>`/
     * `SubjectRef<?>` (subject) - not just the `Action<?>`+`SubjectDef<?>`/
     * `SubjectRef<?>` combinations the builder previously supported.
     */
    @Test
    public void everyActionAndSubjectShapeCombinationIsAccepted() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow("Read", "Article")
            .allow("Create", article)
            .allow(update, "Article")
            .allow(update, article, Conditions.eq(Article::getOwnerId, 42))
            .build();

        assertTrue(policy.can("Read", "Article"));
        assertTrue(policy.can("Read", article));
        assertTrue(policy.can(ActionFactory.create("Read"), "Article"));
        assertTrue(policy.can(ActionFactory.create("Read"), article));

        assertTrue(policy.can("Create", article));
        assertTrue(policy.can(update, "Article"));

        Article owned = new Article(1, 42, "published");
        assertTrue(policy.can("Update", article.wrap(owned)));
        assertTrue(policy.can(update, article.wrap(owned)));

        assertFalse(policy.cannot("Read", "Article"));
        policy.require("Read", "Article"); // should not throw
    }

    /**
     * Issue 1: a custom operator supplied to {@code PolicyBuilder} carries
     * through {@code build()} into the constructed {@code Policy} - a
     * builder-produced definition doesn't need its operators re-supplied
     * separately at {@code Policy.from(...)}.
     */
    @Test
    public void builderSuppliedOperatorsCarryThroughToTheBuiltPolicy() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder(List.of(
            Operator.of("$hasRole", (subject, value, ctx) -> "admin".equals(value))
        ))
            .deny(delete, article)
            .allow(delete, article, Map.of("$hasRole", "admin"))
            .build();

        assertTrue(policy.can(delete, article.wrap(new Article(1, 1, "published"))));
    }
}
