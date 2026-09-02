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
import com.cptnfizzbin.keycard.errors.PolicyException;

import static org.junit.Assert.*;

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
}
