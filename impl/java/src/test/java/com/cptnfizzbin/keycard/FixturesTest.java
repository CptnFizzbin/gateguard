package com.cptnfizzbin.keycard;

import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import com.cptnfizzbin.keycard.policy.Policy;
import com.cptnfizzbin.keycard.policy.PolicyDefinition;
import com.cptnfizzbin.keycard.subject.SubjectDef;
import com.cptnfizzbin.keycard.subject.SubjectRef;
import com.cptnfizzbin.keycard.action.Action;
import com.cptnfizzbin.keycard.action.ActionFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;

public class FixturesTest {

    private static final String FIXTURES_DIR = "../../test/fixtures";
    private Yaml yaml = new Yaml();
    private Map<String, Object> objectsArticles;
    private Map<String, Object> objectsComments;
    private Map<String, Object> objectsUsers;

    @Before
    public void setUp() throws IOException {
        objectsArticles = loadObjectsYaml("articles.yaml");
        objectsComments = loadObjectsYaml("comments.yaml");
        objectsUsers = loadObjectsYaml("users.yaml");
    }

    private String loadPolicyYaml(String filename) throws IOException {
        String path = FIXTURES_DIR + "/policies/" + filename;
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadObjectsYaml(String filename) throws IOException {
        String path = FIXTURES_DIR + "/objects/" + filename;
        String content = new String(Files.readAllBytes(Paths.get(path)));
        Map<String, Object> loaded = yaml.load(content);
        
        // Extract the object collection key (e.g., "articles" from articles.yaml)
        // The YAML file has structure: articles: { article_1: {...}, ... }
        // We need to return just the inner map { article_1: {...}, ... }
        for (Object value : loaded.values()) {
            if (value instanceof Map) {
                return (Map<String, Object>) value;
            }
        }
        return loaded;
    }

    @SuppressWarnings("unchecked")
    private PolicyDefinition loadPolicyDef(String filename) throws IOException {
        String content = loadPolicyYaml(filename);
        Map<String, Object> parsed = yaml.load(content);
        
        int version = (int) parsed.getOrDefault("version", 1);
        java.util.List<PolicyDefinition.Rule> allowRules = new java.util.ArrayList<>();
        java.util.List<PolicyDefinition.Rule> denyRules = new java.util.ArrayList<>();
        
        java.util.List<?> allows = (java.util.List<?>) parsed.getOrDefault("allow", java.util.List.of());
        for (Object rule : allows) {
            if (rule instanceof java.util.List) {
                allowRules.add(convertArrayToRule((java.util.List<?>) rule));
            }
        }
        
        java.util.List<?> denies = (java.util.List<?>) parsed.getOrDefault("deny", java.util.List.of());
        for (Object rule : denies) {
            if (rule instanceof java.util.List) {
                denyRules.add(convertArrayToRule((java.util.List<?>) rule));
            }
        }
        
        return new PolicyDefinition(version, allowRules, denyRules);
    }

    @SuppressWarnings("unchecked")
    private Policy createPolicy(String policyFile) throws IOException {
        String content = loadPolicyYaml(policyFile);
        Map<String, Object> parsed = yaml.load(content);
        
        int version = (int) parsed.getOrDefault("version", 1);
        java.util.List<PolicyDefinition.Rule> allowRules = new java.util.ArrayList<>();
        java.util.List<PolicyDefinition.Rule> denyRules = new java.util.ArrayList<>();
        
        java.util.List<?> allows = (java.util.List<?>) parsed.getOrDefault("allow", java.util.List.of());
        for (Object rule : allows) {
            if (rule instanceof java.util.List) {
                allowRules.add(convertArrayToRule((java.util.List<?>) rule));
            }
        }
        
        java.util.List<?> denies = (java.util.List<?>) parsed.getOrDefault("deny", java.util.List.of());
        for (Object rule : denies) {
            if (rule instanceof java.util.List) {
                denyRules.add(convertArrayToRule((java.util.List<?>) rule));
            }
        }
        
        return new Policy(new PolicyDefinition(version, allowRules, denyRules));
    }

    @SuppressWarnings("unchecked")
    private PolicyDefinition.Rule convertArrayToRule(java.util.List<?> ruleArray) {
        String action = (String) ruleArray.get(0);
        String subject = (String) ruleArray.get(1);
        Map<String, Object> conditions = null;
        
        if (ruleArray.size() > 2) {
            conditions = (Map<String, Object>) ruleArray.get(2);
        }
        
        return new PolicyDefinition.Rule(action, subject, conditions);
    }

    private <T> SubjectRef<T> wrapSubject(String subjectName, T obj) {
        SubjectDef<T> def = SubjectDef.create(subjectName, (Class<T>) obj.getClass());
        return def.wrap(obj);
    }

    private Object addSubjectType(Map<String, Object> obj, String typeName) {
        Map<String, Object> result = new java.util.HashMap<>(obj);
        result.put("__name", typeName);
        return result;
    }

    // Policy 1: Simple
    @Test
    public void testPolicy01DataLoadsCorrectly() throws IOException {
        assertNotNull("Articles should be loaded", objectsArticles);
        assertNotNull("Article 1 should be present", objectsArticles.get("article_1"));
        @SuppressWarnings("unchecked")
        Map<String, Object> article1 = (Map<String, Object>) objectsArticles.get("article_1");
        assertEquals("Article 1 owner_id should be 1", 1, article1.get("owner_id"));
    }

    @Test
    public void testPolicy01AllowsRead() throws IOException {
        Policy policy = createPolicy("policy-01-simple.yaml");
        assertTrue("Policy should allow Read on Article", policy.can("Read", "Article"));
    }

    @Test
    public void testPolicy01AllowsCreateForOwner1() throws IOException {
        Policy policy = createPolicy("policy-01-simple.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_1");
        Action<?> createAction = ActionFactory.create("Create");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Create for owner_id 1", policy.can(createAction, subject));
    }

    @Test
    public void testPolicy01DeniesCreateForOwner2() throws IOException {
        Policy policy = createPolicy("policy-01-simple.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> createAction = ActionFactory.create("Create");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Create for owner_id 2", policy.can(createAction, subject));
    }

    // Policy 2: Ownership
    @Test
    public void testPolicy02AllowsRead() throws IOException {
        Policy policy = createPolicy("policy-02-ownership.yaml");
        assertTrue("Policy should allow Read on Article", policy.can("Read", "Article"));
    }

    @Test
    public void testPolicy02AllowsUpdateForOwner() throws IOException {
        Policy policy = createPolicy("policy-02-ownership.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_1");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Update for owner", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy02DeniesUpdateForNonOwner() throws IOException {
        Policy policy = createPolicy("policy-02-ownership.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Update for non-owner", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy02AllowsDeleteForOwner() throws IOException {
        Policy policy = createPolicy("policy-02-ownership.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_1");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Delete for owner", policy.can(deleteAction, subject));
    }

    @Test
    public void testPolicy02DeniesDeleteForNonOwner() throws IOException {
        Policy policy = createPolicy("policy-02-ownership.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Delete for non-owner", policy.can(deleteAction, subject));
    }

    // Policy 3: Complex Conditions
    @Test
    public void testPolicy03AllowsRead() throws IOException {
        Policy policy = createPolicy("policy-03-complex-conditions.yaml");
        assertTrue("Policy should allow Read on Article", policy.can("Read", "Article"));
    }

    @Test
    public void testPolicy03AllowsUpdateForOwnerNonArchived() throws IOException {
        Policy policy = createPolicy("policy-03-complex-conditions.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_1");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Update for owner with non-archived status", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy03DeniesUpdateForArchived() throws IOException {
        Policy policy = createPolicy("policy-03-complex-conditions.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Update for archived articles", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy03DeniesDeleteArchived() throws IOException {
        Policy policy = createPolicy("policy-03-complex-conditions.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Delete on archived articles", policy.can(deleteAction, subject));
    }

    @Test
    public void testPolicy03AllowsDeleteNonArchived() throws IOException {
        Policy policy = createPolicy("policy-03-complex-conditions.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_1");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Delete on non-archived articles", policy.can(deleteAction, subject));
    }

    // Policy 4: Multi-Resource
    @Test
    public void testPolicy04AllowsReadArticle() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        assertTrue("Policy should allow Read on Article", policy.can("Read", "Article"));
    }

    @Test
    public void testPolicy04AllowsReadComment() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        assertTrue("Policy should allow Read on Comment", policy.can("Read", "Comment"));
    }

    @Test
    public void testPolicy04AllowsReadUser() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        assertTrue("Policy should allow Read on User", policy.can("Read", "User"));
    }

    @Test
    public void testPolicy04AllowsUpdateCommentForAuthor() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> comment = (Map<String, Object>) objectsComments.get("comment_1");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Comment", comment);
        
        assertTrue("Policy should allow Update Comment for author", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy04DeniesUpdateCommentForNonAuthor() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> comment = (Map<String, Object>) objectsComments.get("comment_4");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Comment", comment);
        
        assertFalse("Policy should deny Update Comment for non-author", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy04DeniesDeleteUser() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) objectsUsers.get("user_1");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("User", user);
        
        assertFalse("Policy should deny Delete User entirely", policy.can(deleteAction, subject));
    }

    @Test
    public void testPolicy04DeniesDeleteAnyUser() throws IOException {
        Policy policy = createPolicy("policy-04-multi-resource.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) objectsUsers.get("user_3");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("User", user);
        
        assertFalse("Policy should deny Delete any user", policy.can(deleteAction, subject));
    }

    // Policy 5: Advanced
    @Test
    public void testPolicy05AllowsRead() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        assertTrue("Policy should allow Read on Article", policy.can("Read", "Article"));
    }

    @Test
    public void testPolicy05AllowsCreate() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        assertTrue("Policy should allow Create on Article", policy.can("Create", "Article"));
    }

    @Test
    public void testPolicy05AllowsUpdateForOwnerDraft() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_2");
        Action<?> updateAction = ActionFactory.create("Update");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Update for owner with draft/published status", policy.can(updateAction, subject));
    }

    @Test
    public void testPolicy05AllowsDeleteLowViewCount() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_4");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Delete for owner with low view count", policy.can(deleteAction, subject));
    }

    @Test
    public void testPolicy05DeniesDeleteHighViewArchived() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_3");
        Action<?> deleteAction = ActionFactory.create("Delete");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Delete for published high-view articles", policy.can(deleteAction, subject));
    }

    @Test
    public void testPolicy05AllowsPublishForOwnerValidTitle() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_2");
        Action<?> publishAction = ActionFactory.create("Publish");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertTrue("Policy should allow Publish for owner with valid title", policy.can(publishAction, subject));
    }

    @Test
    public void testPolicy05DeniesPublishForNonOwner() throws IOException {
        Policy policy = createPolicy("policy-05-advanced.yaml");
        @SuppressWarnings("unchecked")
        Map<String, Object> article = (Map<String, Object>) objectsArticles.get("article_5");
        Action<?> publishAction = ActionFactory.create("Publish");
        SubjectRef<Map<String, Object>> subject = wrapSubject("Article", article);
        
        assertFalse("Policy should deny Publish for non-owner", policy.can(publishAction, subject));
    }
}
