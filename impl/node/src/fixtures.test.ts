import * as fs from "fs";
import * as path from "path";
import * as YAML from "yaml";
import { describe, test, beforeAll, expect } from "vitest";
import { Policy } from "./index";

describe("Fixture-based Policy Tests", () => {
  const fixturesDir = path.join(__dirname, "../../../test/fixtures");

  function loadPolicyDef(filename: string): any {
    const filePath = path.join(fixturesDir, "policies", filename);
    const content = fs.readFileSync(filePath, "utf-8");
    const parsed = YAML.parse(content);
    // Transform YAML format to PolicyDefinition format
    return {
      version: parsed.version,
      rules: {
        allow: parsed.allow || [],
        deny: parsed.deny || [],
      },
    };
  }

  function loadObjects(): any {
    const articlesPath = path.join(fixturesDir, "objects", "articles.yaml");
    const commentsPath = path.join(fixturesDir, "objects", "comments.yaml");
    const usersPath = path.join(fixturesDir, "objects", "users.yaml");

    const result: any = {};

    if (fs.existsSync(articlesPath)) {
      const articlesData = YAML.parse(fs.readFileSync(articlesPath, "utf-8")).articles;
      result.articles = {};
      for (const [key, value] of Object.entries(articlesData)) {
        // Add __name property to identify subject type for policy checking
        result.articles[key] = { ...value as any, __name: "Article" };
      }
    }
    if (fs.existsSync(commentsPath)) {
      const commentsData = YAML.parse(fs.readFileSync(commentsPath, "utf-8")).comments;
      result.comments = {};
      for (const [key, value] of Object.entries(commentsData)) {
        result.comments[key] = { ...value as any, __name: "Comment" };
      }
    }
    if (fs.existsSync(usersPath)) {
      const usersData = YAML.parse(fs.readFileSync(usersPath, "utf-8")).users;
      result.users = {};
      for (const [key, value] of Object.entries(usersData)) {
        result.users[key] = { ...value as any, __name: "User" };
      }
    }

    return result;
  }

  describe("Policy 1: Simple", () => {
    let policy: Policy;
    let objects: any;

    beforeAll(() => {
      const policyDef = loadPolicyDef("policy-01-simple.yaml");
      policy = new Policy(policyDef);
      objects = loadObjects();
    });

    test("data loads correctly", () => {
      expect(objects.articles).toBeDefined();
      expect(objects.articles.article_1).toBeDefined();
      expect(objects.articles.article_1.owner_id).toBe(1);
    });

    test("allows Read on Article", () => {
      expect(policy.can("Read" as any, "Article" as any)).toBe(true);
    });

    test("allows Create on Article for owner_id 1", () => {
      const article = objects.articles.article_1;
      expect(policy.can("Create" as any, article)).toBe(true);
    });

    test("denies Create for owner_id 2", () => {
      const article = objects.articles.article_3;
      expect(policy.can("Create" as any, article)).toBe(false);
    });
  });

  describe("Policy 2: Ownership", () => {
    let policy: Policy;
    let objects: any;

    beforeAll(() => {
      const policyDef = loadPolicyDef("policy-02-ownership.yaml");
      policy = new Policy(policyDef);
      objects = loadObjects();
    });

    test("allows Read on Article", () => {
      expect(policy.can("Read" as any, "Article" as any)).toBe(true);
    });

    test("allows Update for owner", () => {
      const article = objects.articles.article_1;
      expect(policy.can("Update" as any, article)).toBe(true);
    });

    test("denies Update for non-owner", () => {
      const article = objects.articles.article_3;
      expect(policy.can("Update" as any, article)).toBe(false);
    });

    test("allows Delete for owner", () => {
      const article = objects.articles.article_1;
      expect(policy.can("Delete" as any, article)).toBe(true);
    });

    test("denies Delete for non-owner", () => {
      const article = objects.articles.article_3;
      expect(policy.can("Delete" as any, article)).toBe(false);
    });
  });

  describe("Policy 3: Complex Conditions", () => {
    let policy: Policy;
    let objects: any;

    beforeAll(() => {
      const policyDef = loadPolicyDef("policy-03-complex-conditions.yaml");
      policy = new Policy(policyDef);
      objects = loadObjects();
    });

    test("allows Read on Article", () => {
      expect(policy.can("Read" as any, "Article" as any)).toBe(true);
    });

    test("allows Update for owner with non-archived status", () => {
      const article = objects.articles.article_1;
      expect(policy.can("Update" as any, article)).toBe(true);
    });

    test("denies Update for archived articles", () => {
      const article = objects.articles.article_3;
      expect(policy.can("Update" as any, article)).toBe(false);
    });

    test("denies Delete on archived articles", () => {
      const article = objects.articles.article_3;
      expect(policy.can("Delete" as any, article)).toBe(false);
    });

    test("allows Delete on non-archived articles", () => {
      const article = objects.articles.article_1;
      expect(policy.can("Delete" as any, article)).toBe(true);
    });
  });

  describe("Policy 4: Multi-Resource", () => {
    let policy: Policy;
    let objects: any;

    beforeAll(() => {
      const policyDef = loadPolicyDef("policy-04-multi-resource.yaml");
      policy = new Policy(policyDef);
      objects = loadObjects();
    });

    test("allows Read on Article", () => {
      expect(policy.can("Read" as any, "Article" as any)).toBe(true);
    });

    test("allows Read on Comment", () => {
      expect(policy.can("Read" as any, "Comment" as any)).toBe(true);
    });

    test("allows Read on User", () => {
      expect(policy.can("Read" as any, "User" as any)).toBe(true);
    });

    test("allows Update Comment for author", () => {
      const comment = objects.comments.comment_1;
      expect(policy.can("Update" as any, comment)).toBe(true);
    });

    test("denies Update Comment for non-author", () => {
      const comment = objects.comments.comment_4;
      expect(policy.can("Update" as any, comment)).toBe(false);
    });

    test("denies Delete User entirely", () => {
      const user = objects.users.user_1;
      expect(policy.can("Delete" as any, user)).toBe(false);
    });

    test("denies Delete any user", () => {
      const user = objects.users.user_3;
      expect(policy.can("Delete" as any, user)).toBe(false);
    });
  });

  describe("Policy 5: Advanced", () => {
    let policy: Policy;
    let objects: any;

    beforeAll(() => {
      const policyDef = loadPolicyDef("policy-05-advanced.yaml");
      policy = new Policy(policyDef);
      objects = loadObjects();
    });

    test("allows Read on Article", () => {
      expect(policy.can("Read" as any, "Article" as any)).toBe(true);
    });

    test("allows Create on Article", () => {
      expect(policy.can("Create" as any, "Article" as any)).toBe(true);
    });

    test("allows Update for owner with draft/published status", () => {
      const article = objects.articles.article_2;
      expect(article.owner_id).toBe(1);
      expect(article.status).toBe("draft");
      expect(policy.can("Update" as any, article)).toBe(true);
    });

    test("allows Delete for owner with low view count", () => {
      const article = objects.articles.article_4;
      expect(article.owner_id).toBe(1);
      expect(article.view_count).toBe(50);
      expect(policy.can("Delete" as any, article)).toBe(true);
    });

    test("denies Delete for published high-view articles", () => {
      const article = objects.articles.article_3;
      expect(article.view_count).toBe(2000);
      expect(article.status).toBe("archived");
      expect(policy.can("Delete" as any, article)).toBe(false);
    });

    test("allows Publish for owner with valid title", () => {
      const article = objects.articles.article_2;
      expect(article.owner_id).toBe(1);
      expect(article.status).toBe("draft");
      expect(article.title).toMatch(/^[A-Z]/);
      expect(policy.can("Publish" as any, article)).toBe(true);
    });

    test("denies Publish for non-owner", () => {
      const article = objects.articles.article_5;
      expect(article.owner_id).toBe(3);
      expect(policy.can("Publish" as any, article)).toBe(false);
    });
  });
});
