KeyCard Spec
============

- Strongly inspired by CASL.js
- TypeSafe, Cross-Language
- Define server-side, resolve client-side

> This is an informal overview. For the normative v1 specification —
> exact rule-evaluation semantics, the full condition-operator table, and
> a catalogue of required edge-case behavior — see
> [`docs/spec/v1.md`](docs/spec/v1.md).

Glossary
--------

- Claims - Object(s) that can be used by a builder to create a Policy Definition
  - JWT, { ownerOf: number[] }
- Action - a string that indicates that the user would like to do something to a subject
  - eg: Create, Read, Update, Delete, MarkDone, Archive, etc...
- Subject - the value that the user wants to do something with
  - eg: ToDoItem, Project, etc...
- PolicyBuilder - takes in claims, and produces a Policy or PolicyDefiniton
- Rule - an allowed or denied triplet of action, subject, and conditions
- PolicyDefinition - (PolicyDef) a text based encoding of what permissions the user is allowed/denied
- Policy - an object that can be used to preform checks against/with


Builder
-------

- Methods:
  - allow(action, subject, conditions?) => Builder
  - deny(action, subject, conditions?) => Builder
  - build() => Policy
  - buildDef() => PolicyDef

Policy Definition
-----------------

- created by a builder, or by hand
- DTO that contains all the rules created by the builder
- used to create a useable policy
- JSON encodeable

example:

```yaml
version: 1 # KeyCard policy spec version
actions: [Create, Update, Delete, anyAction]
subjects: [Article, anySubject]
rules:
  - [allow, Create, Article] # Allow to create any article
  - [allow, Update, Article, { owner_id: 1 }] # Allowed to update articles they own
  - [deny, Delete, Article, { status: { $not: "archived" } }] # Not allowed to delete archived articles
```

`rules` is a single, order-significant list: the *last* rule that matches
an action/subject/condition wins (CASL.js-style), not "any deny beats any
allow." See [`docs/spec/v1.md`](docs/spec/v1.md) §6 for the exact
algorithm, and §4/§5 for the `anyAction`/`anySubject` wildcards.

Condition
---------

- a filter to apply to a subject to check if the rule should be applied or not

Condition = 
  | EqCondition = TValue | { $eq: TValue } //=> TSubject == TValue
  | GtCondition = { $gt: TValue } //=> TSubject > TValue
  | GteCondition = { $gte: TValue } //=> TSubject >= TValue
  | LtCondition = { $lt: TValue } //=> TSubject < TValue
  | LteCondition = { $lte: TValue } //=> TSubject <= TValue
  | InCondition = { $in: TValue[] } //=> TValue[].contains(TSubject)
  | HasCondition = { $has: TValue } //=> TSubject[].contains(TValue)
  | RegexCondition = { $rgx: TValue } //=> TValue.match(TSubject)
  | OrCondition = { $or: Condition[] } //=> Condition[].any(TSubject)
  | AndCondition = { $and: Condition[] } //=> Condition[].all(TSubject)
  | NotCondition = { $not: Condition } //=> !Condition(TSubject)
  | FieldCondition = { [key]: Condition } //=> Condition(TSubject[key])
  // Note: an alternate `{ $field: [key, Condition] }` form was sketched
  // here but is reserved/unimplemented as of v1 - see docs/spec/v1.md §7.5.

Policy
------

- Methods:
  - new<TActions, TSubjects>(PolicyDef, customConditions?) => Policy
  - def() => PolicyDefinition
  - can(TAction, TSubjectName | TSubject) => boolean
  - cannot(TAction, TSubjectName | TSubject) => boolean
  - require(TAction, TSubjectName | TSubject) => void throws PolicyError
  - append(PolicyDef) => Policy