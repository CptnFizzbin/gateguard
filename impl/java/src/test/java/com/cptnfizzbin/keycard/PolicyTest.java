package com.cptnfizzbin.keycard;

import org.junit.Test;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.builder.PolicyBuilder;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.subject.SubjectFactory;
import com.cptnfizzbin.keycard.conditions.Conditions;
import com.cptnfizzbin.keycard.errors.PolicyException;

import static org.junit.Assert.*;

public class PolicyTest {
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
