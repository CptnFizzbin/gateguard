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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ConditionsHelperTest {
    @Getter
    @AllArgsConstructor
    static class Article {
        private final int id;
        private final int ownerId;
        private final String status;
        private final int viewCount;
    }

    @Test
    public void testFieldEquality() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, article, Conditions.eq(Article::getOwnerId, 42))
            .build();

        Article data = new Article(1, 42, "published", 100);
        assertTrue(policy.can(update, article.wrap(data)));

        Article other = new Article(1, 99, "published", 100);
        assertFalse(policy.can(update, article.wrap(other)));
    }

    @Test
    public void testFieldNotEqual() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Policy policy = new PolicyBuilder()
            .allow(update, article, Conditions.ne(Article::getStatus, "archived"))
            .build();

        Article published = new Article(1, 1, "published", 100);
        assertTrue(policy.can(update, article.wrap(published)));

        Article archived = new Article(1, 1, "archived", 100);
        assertFalse(policy.can(update, article.wrap(archived)));
    }

    @Test
    public void testNumberComparison() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> delete = ActionFactory.create("Delete");

        Policy policy = new PolicyBuilder()
            .allow(delete, article, Conditions.lt(Article::getViewCount, 1000))
            .build();

        Article lowViews = new Article(1, 1, "published", 500);
        assertTrue(policy.can(delete, article.wrap(lowViews)));

        Article highViews = new Article(1, 1, "published", 5000);
        assertFalse(policy.can(delete, article.wrap(highViews)));
    }

    @Test
    public void testAndCondition() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Map<String, Object> conditions = Conditions.and(
            Conditions.eq(Article::getOwnerId, 1),
            Conditions.ne(Article::getStatus, "archived")
        );

        Policy policy = new PolicyBuilder()
            .allow(update, article, conditions)
            .build();

        Article validArticle = new Article(1, 1, "published", 100);
        assertTrue(policy.can(update, article.wrap(validArticle)));

        Article wrongOwner = new Article(1, 2, "published", 100);
        assertFalse(policy.can(update, article.wrap(wrongOwner)));

        Article archived = new Article(1, 1, "archived", 100);
        assertFalse(policy.can(update, article.wrap(archived)));
    }

    @Test
    public void testOrCondition() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> publish = ActionFactory.create("Publish");

        Map<String, Object> conditions = Conditions.or(
            Conditions.eq(Article::getOwnerId, 1),
            Conditions.eq(Article::getOwnerId, 2)
        );

        Policy policy = new PolicyBuilder()
            .allow(publish, article, conditions)
            .build();

        Article owner1 = new Article(1, 1, "draft", 0);
        assertTrue(policy.can(publish, article.wrap(owner1)));

        Article owner2 = new Article(2, 2, "draft", 0);
        assertTrue(policy.can(publish, article.wrap(owner2)));

        Article owner3 = new Article(3, 3, "draft", 0);
        assertFalse(policy.can(publish, article.wrap(owner3)));
    }

    @Test
    public void testAndCondition2() {
        SubjectDef<Article> article = SubjectFactory.create("Article", Article.class);
        Action<String> update = ActionFactory.create("Update");

        Map<String, Object> conditions = Conditions.and(
            Conditions.eq(Article::getOwnerId, 1),
            Conditions.ne(Article::getStatus, "archived")
        );

        Policy policy = new PolicyBuilder()
            .allow(update, article, conditions)
            .build();

        Article validArticle = new Article(1, 1, "published", 100);
        assertTrue(policy.can(update, article.wrap(validArticle)));
    }
}
