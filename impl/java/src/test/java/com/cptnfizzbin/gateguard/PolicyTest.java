package com.cptnfizzbin.gateguard;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.Test;

import com.cptnfizzbin.gateguard.policy.Policy;
import com.cptnfizzbin.gateguard.builder.PolicyBuilder;
import com.cptnfizzbin.gateguard.policy.PolicyDefinition;
import com.cptnfizzbin.gateguard.action.Action;
import com.cptnfizzbin.gateguard.action.ActionFactory;
import com.cptnfizzbin.gateguard.subject.SubjectDef;
import com.cptnfizzbin.gateguard.subject.SubjectFactory;
import com.cptnfizzbin.gateguard.conditions.Conditions;
import com.cptnfizzbin.gateguard.errors.PolicyException;

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
    public void testAppendCannotBypassABaseDenyRule() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        // A base policy that only denies deletion...
        PolicyDefinition base = new PolicyBuilder()
            .deny(delete, article)
            .buildDef();

        // ...merged with a policy that would otherwise allow it.
        PolicyDefinition moreAllow = new PolicyBuilder()
            .allow(delete, article)
            .buildDef();

        Policy policy = new Policy(base).append(moreAllow);

        assertFalse(policy.can(delete, article));
    }

    @Test
    public void testAppendPolicies() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> create = ActionFactory.create("Create");
        Action<String> read = ActionFactory.create("Read");

        PolicyDefinition def1 = new PolicyBuilder()
            .allow(create, article)
            .buildDef();

        PolicyDefinition def2 = new PolicyBuilder()
            .allow(read, article)
            .buildDef();

        Policy policy = new Policy(def1).append(def2);

        assertTrue(policy.can(create, article));
        assertTrue(policy.can(read, article));
    }
}
