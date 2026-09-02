import { describe, test, expect } from "vitest";
import { Policy } from "./Policy";
import { PolicyLoadException, PolicyVersionException } from "../errors";

describe("Policy: last-rule-wins evaluation (SPEC_V1-0-0.md §6)", () => {
  test("a later-declared deny rule overrides an earlier allow for the same action/subject", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["allow", "Delete", "Article"],
        ["deny", "Delete", "Article"],
      ],
    });

    expect(policy.can("Delete", "Article")).toBe(false);
    expect(policy.cannot("Delete", "Article")).toBe(true);
  });

  test("a conditional deny rule only overrides allow when its condition matches", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["allow", "Delete", "Article"],
        ["deny", "Delete", "Article", { status: "archived" }],
      ],
    });

    const archived = { __name: "Article", status: "archived" };
    const published = { __name: "Article", status: "published" };

    expect(policy.can("Delete", archived)).toBe(false);
    expect(policy.can("Delete", published)).toBe(true);
  });

  test("a later allow reopens what an earlier deny closed", () => {
    const policy = Policy.from({
      version: "1.0.0",
      rules: [
        ["deny", "Delete", "User"],
        ["allow", "Delete", "User"],
      ],
    });

    expect(policy.can("Delete", "User")).toBe(true);
  });

  test("an empty rule list denies everything (EC-1)", () => {
    const policy = Policy.from({ version: "1.0.0", rules: [] });

    expect(policy.can("Read", "Article")).toBe(false);
  });
});

describe("Policy: construction-time validation", () => {
  test("throws PolicyVersionException for an unsupported MAJOR version", () => {
    expect(() => Policy.from({ version: "2.0.0", rules: [] })).toThrow(PolicyVersionException);
  });

  test("throws PolicyVersionException for a MINOR newer than what's supported", () => {
    expect(() => Policy.from({ version: "1.99.0", rules: [] })).toThrow(PolicyVersionException);
  });

  test("ignores PATCH when deciding compatibility", () => {
    expect(() => Policy.from({ version: "1.0.99", rules: [] })).not.toThrow();
  });

  test("throws PolicyLoadException for a malformed rule tuple (EC-10)", () => {
    expect(() =>
      Policy.from({ version: "1.0.0", rules: [["allow", "Read"] as any] })
    ).toThrow(PolicyLoadException);
  });

  test("throws PolicyLoadException for an effect that isn't allow/deny (EC-10)", () => {
    expect(() =>
      Policy.from({ version: "1.0.0", rules: [["maybe", "Read", "Article"] as any] })
    ).toThrow(PolicyLoadException);
  });

  test("throws PolicyLoadException for a rule wildcarded on both sides carrying a condition (EC-6)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        rules: [["allow", "_ANY_", "_ANY_", { owner_id: 1 }]],
      })
    ).toThrow(PolicyLoadException);
  });

  test("throws PolicyLoadException when a rule's action isn't covered by a declared meta.actions catalog (EC-8)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        meta: { actions: ["Read"] },
        rules: [["allow", "Write", "Article"]],
      })
    ).toThrow(PolicyLoadException);
  });

  test("throws PolicyLoadException when a rule uses a custom operator outside a declared meta.customOperators catalog (EC-13)", () => {
    expect(() =>
      Policy.from({
        version: "1.0.0",
        meta: { customOperators: ["$hasRole"] },
        rules: [["allow", "Read", "Article", { $isAdmin: true }]],
      })
    ).toThrow(PolicyLoadException);
  });
});
