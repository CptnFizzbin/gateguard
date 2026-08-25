# KeyCard - TypeScript/Node.js

TypeScript access control library inspired by CASL.js. Provides strongly-typed, composable authorization policies with compile-time safety for Actions and Subjects.

## Features

- **Type-safe Actions & Subjects**: Branded types prevent typos and ensure type-safe refactoring
- **Composable**: Build complex policies from simple rules
- **Flexible conditions**: Support for comparison, pattern matching, and logical operators
- **Cross-language**: PolicyDefinitions serialize to JSON for cross-platform use
- **Extensible**: Custom condition operators support

## Installation

```bash
npm install @keycard/core
```

## Quick Start

```typescript
import { createAction, createSubject, PolicyBuilder, Policy } from '@keycard/core';

// Define your action and subject types
const Actions = {
  Create: createAction("Create"),
  Update: createAction("Update"),
  Delete: createAction("Delete"),
} as const;

const Subjects = {
  Article: createSubject<{ id: number; owner_id: number; status: string }>("Article"),
} as const;

type AppAction = typeof Actions[keyof typeof Actions];
type AppSubject = typeof Subjects[keyof typeof Subjects];

// Build a policy
const policyDef = new PolicyBuilder<readonly AppAction[], readonly AppSubject[]>()
  .allow(Actions.Create, Subjects.Article)
  .allow(Actions.Update, Subjects.Article, { owner_id: 1 })
  .deny(Actions.Delete, Subjects.Article, { status: { $not: "archived" } })
  .buildDef();

// Create and use policy
const policy = new Policy<readonly AppAction[], readonly AppSubject[]>(policyDef);

// Check by subject definition
if (policy.can(Actions.Create, Subjects.Article)) {
  // Create article
}

// Check by subject instance
const article = Subjects.Article.wrap({ id: 1, owner_id: 1, status: "published" });
if (policy.can(Actions.Update, article)) {
  // Update article
}

// Require permission (throws if denied)
policy.require(Actions.Delete, article); // Throws PolicyError if not allowed
```

## Type Safety

KeyCard provides compile-time type safety:
- Actions can only be created with `createAction` 
- Subjects must match their defined shape
- Policy methods only accept valid Action/Subject combinations
- Refactoring actions/subjects updates all policy rules

See [TYPE_SAFETY.md](../TYPE_SAFETY.md) for detailed examples.

## Condition Operators

- `$eq` - Equality
- `$gt` - Greater than
- `$gte` - Greater than or equal
- `$lt` - Less than
- `$lte` - Less than or equal
- `$in` - Value in array
- `$has` - Array contains value
- `$rgx` - Regex match
- `$or` - Logical OR
- `$and` - Logical AND
- `$not` - Logical NOT
- Field conditions - Check nested properties

## API

### createAction<T>(name: T)
Create a typed action.

### createSubject<TSubject>(name: string)
Create a typed subject definition.

### PolicyBuilder<TActions, TSubjects>
- `allow(action, subject, conditions?)` - Allow action
- `deny(action, subject, conditions?)` - Deny action
- `buildDef()` - Create PolicyDefinition
- `build()` - Create Policy instance (coming soon)

### SubjectDef<T>
- `wrap(obj: T)` - Create SubjectRef from object
- `__name` - Subject name

### SubjectRef<T>
- `value` - The wrapped object
- `__name` - Subject name

### Policy<TActions, TSubjects>
- `can(action, subject)` - Check if action is allowed
- `cannot(action, subject)` - Check if action is denied
- `require(action, subject)` - Throw if not allowed
- `append(definition)` - Merge additional policies
- `def()` - Get underlying definition

## Examples

See `src/example.ts` for a complete working example.

## See Also

- [SPEC.md](../SPEC.md) - Complete specification
- [TYPE_SAFETY.md](../TYPE_SAFETY.md) - Type safety deep dive
- [Rust implementation](../rust)

