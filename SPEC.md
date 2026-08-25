KeyCard Spec
============

- Strongly inspired by CASL.js
- TypeSafe, Cross-Language
- Define server-side, resolve client-side

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
rules:
  allow:
    - [Create, Article] # Allow to create any article
    - [Update, Article, { owner_id: 1 }] # Allowed to update articles they own
  deny:
    - [Delete, Article, { status: { $not: "archived" } }] # Not allowed to delete archived articles
```

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
  | FieldCondition = { [key]: Condition } | { $field: [key, Condition] } //=> Condition(TSubject[key])

Policy
------

- Methods:
  - new<TActions, TSubjects>(PolicyDef, customConditions?) => Policy
  - def() => PolicyDefinition
  - can(TAction, TSubjectName | TSubject) => boolean
  - cannot(TAction, TSubjectName | TSubject) => boolean
  - require(TAction, TSubjectName | TSubject) => void throws PolicyError
  - append(PolicyDef) => Policy