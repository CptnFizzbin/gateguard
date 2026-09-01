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

    expect(policy.can("Delete", "Article")).toBe(false);
    expect(policy.cannot("Delete", "Article")).toBe(true);
  });

  test("a conditional deny rule only overrides allow when its condition matches", () => {
    const policy = Policy.from({
      version: 1,
      rules: {
        allow: [["Delete", "Article"]],
        deny: [["Delete", "Article", { status: "archived" }]],
      },
    });

    const archived = { __name: "Article", status: "archived" };
    const published = { __name: "Article", status: "published" };

    expect(policy.can("Delete", archived)).toBe(false);
    expect(policy.can("Delete", published)).toBe(true);
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
    expect(merged.can("Delete", "User")).toBe(false);
  });
});
