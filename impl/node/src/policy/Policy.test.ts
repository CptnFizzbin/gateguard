import { describe, test, expect } from "vitest";
import { Policy } from "./Policy";

describe("Policy deny precedence", () => {
  test("a matching deny rule overrides a matching allow rule", () => {
    const policy = Policy.from({
      version: 1,
      rules: {
        allow: [["Delete", "Article"]],
        deny: [["Delete", "Article"]],
      },
    });

    expect(policy.can("Delete" as any, "Article" as any)).toBe(false);
    expect(policy.cannot("Delete" as any, "Article" as any)).toBe(true);
  });

  test("a conditional deny rule only overrides allow when its condition matches", () => {
    const policy = Policy.from({
      version: 1,
      rules: {
        allow: [["Delete", "Article"]],
        deny: [["Delete", "Article", { status: "archived" }]],
      },
    });

    expect(policy.can("Delete" as any, { __name: "Article", status: "archived" } as any)).toBe(false);
    expect(policy.can("Delete" as any, { __name: "Article", status: "published" } as any)).toBe(true);
  });

  test("append() cannot bypass a base policy's deny rule", () => {
    const base = Policy.from({
      version: 1,
      rules: { allow: [], deny: [["Delete", "User"]] },
    });

    // Merging in a policy that would otherwise allow the action...
    const merged = base.append({
      version: 1,
      rules: { allow: [["Delete", "User"]], deny: [] },
    });

    // ...still doesn't bypass the base policy's deny.
    expect(merged.can("Delete" as any, "User" as any)).toBe(false);
  });
});
