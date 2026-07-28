# Moodsonix · Supabase Integration Setup Guide

This document walks you through every step needed to connect the Moodsonix Android
app to your own Supabase project.

---

## Prerequisites

- A free [Supabase](https://supabase.com) account
- Android Studio (latest stable) or the Replit build environment
- The Moodsonix source code

---

## Step 1 — Create a Supabase Project

1. Go to [https://supabase.com/dashboard](https://supabase.com/dashboard) and sign in.
2. Click **New Project**.
3. Fill in:
   - **Project name**: `moodsonix` (or any name you like)
   - **Database password**: choose a strong password and save it somewhere safe
   - **Region**: pick the region closest to your users
4. Click **Create new project** and wait ~2 minutes for provisioning.

---

## Step 2 — Run the SQL Migration

1. In the Supabase Dashboard, open your project.
2. Go to **SQL Editor** (left sidebar) → **New Query**.
3. Open `supabase/migrations/001_initial_schema.sql` from this repository.
4. Copy the entire file contents and paste them into the SQL editor.
5. Click **Run** (or press Cmd/Ctrl + Enter).
6. You should see a success message — all tables, RLS policies, and triggers are now created.

> **Tip**: If you need to reset (during development), run `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` and then re-run the migration.

---

## Step 3 — Enable Realtime

The messaging system uses Supabase Realtime. You need to enable it for the
`messages` and `conversations` tables:

1. In the Supabase Dashboard, go to **Database** → **Replication**.
2. Find the `supabase_realtime` publication.
3. Click **Source** and add the tables:
   - `public.messages`
   - `public.conversations`
4. Save changes.

---

## Step 4 — Get Your Project URL and Anon Key

1. In the Supabase Dashboard, go to **Project Settings** → **API**.
2. Copy:
   - **Project URL** — looks like `https://abcdefgh.supabase.co`
   - **Project API keys → anon (public)** — a long JWT string

> The `anon` key is safe to embed in the APK because Row Level Security (RLS)
> is enabled on every table — users can only read/write their own rows.

---

## Step 5 — Configure the App

1. Open (or create) `local.properties` in the **project root** (not the `app/` folder).
2. Add these two lines:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
```

Replace the placeholder values with the ones you copied in Step 4.

> `local.properties` is in `.gitignore` — your credentials will never be committed.  
> See `supabase.properties.template` for the expected format.

---

## Step 6 — Build & Run

```bash
# From the project root
bash build-apk.sh assembleUniversalFossDebug
```

Or build from Android Studio as normal.  The app will now connect to your
Supabase project on first launch.

---

## Step 7 — Test the Integration

### Authentication
1. Launch the app and navigate to **Settings → Supabase Login** (or the route `supabase_login`).
2. Tap **Sign up** and create a test account.
3. Verify a new row appears in `auth.users` and `profiles` in the Supabase dashboard.

### Favorites
1. Like a song in the app.
2. Check the Supabase Dashboard → **Table Editor** → `favorites` — your row should appear.

### Messaging
1. Create two test accounts in the app.
2. Open **Messages** (route: `conversations`) from one account.
3. The other user's UUID is their `profiles.id` — paste it as the `partnerId` route argument.
4. Send a message and watch it appear in real time on both devices / tabs.

---

## File Reference

| File | Purpose |
|------|---------|
| `supabase/migrations/001_initial_schema.sql` | Full database schema + RLS |
| `supabase.properties.template` | Template for `local.properties` |
| `app/src/main/kotlin/.../supabase/SupabaseClientProvider.kt` | Singleton Supabase client |
| `app/src/main/kotlin/.../supabase/models/SupabaseModels.kt` | All serializable data models |
| `app/src/main/kotlin/.../supabase/repository/` | One repository class per feature |
| `app/src/main/kotlin/.../di/SupabaseModule.kt` | Hilt DI wiring |
| `app/src/main/kotlin/.../viewmodels/SupabaseAuthViewModel.kt` | Auth + profile state |
| `app/src/main/kotlin/.../viewmodels/SupabaseMessagingViewModel.kt` | Real-time chat state |
| `app/src/main/kotlin/.../ui/screens/supabase/` | Login, Register, Messaging screens |

---

## Tables Created

| Table | Description |
|-------|------------|
| `profiles` | One row per user; auto-created on sign-up via trigger |
| `favorites` | Songs the user has liked |
| `playlists` | User-created playlists |
| `playlist_songs` | Songs inside a playlist |
| `recently_played` | Rolling play history (capped at 200 rows/user) |
| `ai_chat_history` | AI mood-playlist conversation history |
| `app_settings` | Synced preferences JSON blob |
| `messages` | One-to-one direct messages |
| `conversations` | Conversation metadata (last activity timestamp) |

---

## Troubleshooting

| Problem | Solution |
|---------|---------|
| App crashes with `Supabase client is not configured` | Add `SUPABASE_URL` and `SUPABASE_ANON_KEY` to `local.properties` |
| Sign-up succeeds but profile row is missing | Re-run the SQL migration — the trigger `on_auth_user_created` creates it |
| Real-time messages not arriving | Check that `public.messages` is added to the `supabase_realtime` publication (Step 3) |
| RLS error on insert | Make sure you are signed in — all tables require `auth.uid()` to be set |
| Version conflict with Supabase SDK | Update `supabase` version in `gradle/libs.versions.toml` to the latest stable |

---

## SDK Version

The integration uses **supabase-kt BOM `3.1.4`**. If a newer version is available,
update the `supabase` entry in `gradle/libs.versions.toml`:

```toml
supabase = "3.x.x"   # replace with the latest from https://github.com/supabase-community/supabase-kt/releases
```

All three modules (`gotrue-kt`, `postgrest-kt`, `realtime-kt`) are versioned
together by the BOM, so only one version number needs changing.
