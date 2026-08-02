---
name: Supabase Android dependency compatibility
description: Compatibility constraint for the imported Android app's Supabase Kotlin integration.
---

The app's Supabase integration uses the pre-3.0 Kotlin SDK API (`gotrue-kt` and `io.github.jan.supabase.gotrue` imports), so dependency upgrades must preserve that API or include a deliberate source migration. The Maven Central group is `io.github.jan-tennert.supabase`; stale JitPack coordinates can fail with authentication or missing-artifact errors.

**Why:** The imported project referenced an obsolete group and a version-3 module rename, causing both dependency resolution failures and widespread compile errors.

**How to apply:** Before changing Supabase versions, inspect the app's imports and compare them with the SDK release's module names and API. For this source shape, use the 2.x BOM/module family unless migrating the Supabase feature code.