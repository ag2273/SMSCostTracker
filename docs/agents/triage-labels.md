# Triage labels

Five canonical triage roles, each mapped 1:1 to a GitHub label of the same name. An open issue carries at most one of them.

| Role | Label | Meaning |
|---|---|---|
| Needs triage | `needs-triage` | New and not evaluated yet. A maintainer decides what it is and whether to do it. |
| Needs info | `needs-info` | Waiting on the reporter or owner for details, samples or a decision. |
| Ready for agent | `ready-for-agent` | Fully specified with acceptance criteria; an agent can implement it without further input. |
| Ready for human | `ready-for-human` | Needs a person: a product decision, device testing, account or settings changes, or credentials. |
| Won't fix | `wontfix` | Will not be worked on. Close with a comment explaining why. |

## Type labels

Independent of the triage role:

- `bug`: something is wrong.
- `enhancement`: a new feature or improvement. An `enhancement` without a triage label is a future idea that is not planned yet.

## Moving between roles

- Replace the previous triage label instead of adding a second one.
- When an agent hits a blocker it cannot resolve, it moves the issue to `needs-info` or `ready-for-human` and comments what it needs.
