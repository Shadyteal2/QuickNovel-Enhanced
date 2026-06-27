# Failed Task Log

> Append an entry every time an implementation attempt is abandoned, rolled back, or causes a build failure.
> Purpose: prevent repeating approaches that don't work.

---

## Entry Template

```
### [YYYY-MM-DD] <Short Task Description>

**Goal**: What was being attempted  
**Approach**: What was tried  
**Failure Reason**: Why it failed (build error / wrong arch / API mismatch / etc.)  
**Error Output** (optional):
\`\`\`
paste relevant error
\`\`\`
**Files Affected**: list of files touched before rollback  
**Lesson**: What to avoid / do differently next time  
**Resolution**: Rolled back / Rerouted to <alternative> / Pending
```

---

<!-- Add entries below this line, newest first -->
