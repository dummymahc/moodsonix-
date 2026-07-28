-- =============================================================================
-- Moodsonix · Supabase Initial Schema
-- Run this script in the Supabase SQL Editor (Dashboard → SQL Editor → New Query)
-- Every table has Row Level Security (RLS) enabled so users can only access
-- their own rows, unless a row is explicitly made public.
-- =============================================================================

-- ─── 0. Extensions ────────────────────────────────────────────────────────────
-- pgcrypto provides gen_random_uuid(); already enabled on every Supabase project
-- by default — listed here for documentation purposes only.
-- CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── 1. Profiles ──────────────────────────────────────────────────────────────
-- One row per auth.users record. Created automatically via trigger (see below).
CREATE TABLE IF NOT EXISTS public.profiles (
    id            UUID        REFERENCES auth.users (id) ON DELETE CASCADE PRIMARY KEY,
    username      TEXT        UNIQUE,
    display_name  TEXT,
    avatar_url    TEXT,
    bio           TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Anyone can read public profile info; only the owner can modify their own row.
CREATE POLICY "profiles_select_all"
    ON public.profiles FOR SELECT USING (TRUE);
CREATE POLICY "profiles_insert_own"
    ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "profiles_update_own"
    ON public.profiles FOR UPDATE USING (auth.uid() = id);
CREATE POLICY "profiles_delete_own"
    ON public.profiles FOR DELETE USING (auth.uid() = id);

-- Auto-create a profile row whenever a new user signs up
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
BEGIN
  INSERT INTO public.profiles (id, display_name, avatar_url)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'full_name', NEW.email),
    NEW.raw_user_meta_data->>'avatar_url'
  )
  ON CONFLICT (id) DO NOTHING;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- ─── 2. Favorites ─────────────────────────────────────────────────────────────
-- Stores songs the user has liked / marked as favourite.
CREATE TABLE IF NOT EXISTS public.favorites (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    song_id         TEXT        NOT NULL,   -- YouTube video/song ID
    song_title      TEXT        NOT NULL,
    song_artist     TEXT,
    song_thumbnail  TEXT,
    song_duration   INTEGER,               -- seconds
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, song_id)
);

ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;

CREATE POLICY "favorites_own"
    ON public.favorites USING (auth.uid() = user_id);

-- Index for fast per-user lookups
CREATE INDEX IF NOT EXISTS idx_favorites_user_id ON public.favorites (user_id);

-- ─── 3. Playlists ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.playlists (
    id             UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    name           TEXT        NOT NULL,
    description    TEXT,
    thumbnail_url  TEXT,
    is_public      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.playlists ENABLE ROW LEVEL SECURITY;

-- Owners can do anything; others can only read public playlists.
CREATE POLICY "playlists_owner_all"
    ON public.playlists USING (auth.uid() = user_id);
CREATE POLICY "playlists_public_select"
    ON public.playlists FOR SELECT USING (is_public = TRUE);

CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON public.playlists (user_id);

-- ─── 4. Playlist Songs ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.playlist_songs (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    playlist_id     UUID        NOT NULL REFERENCES public.playlists (id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    song_id         TEXT        NOT NULL,
    song_title      TEXT        NOT NULL,
    song_artist     TEXT,
    song_thumbnail  TEXT,
    song_duration   INTEGER,
    position        INTEGER     NOT NULL DEFAULT 0,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.playlist_songs ENABLE ROW LEVEL SECURITY;

-- Users can manage songs in their own playlists; can read songs in public playlists.
CREATE POLICY "playlist_songs_owner_all"
    ON public.playlist_songs USING (auth.uid() = user_id);
CREATE POLICY "playlist_songs_public_select"
    ON public.playlist_songs FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM public.playlists p
            WHERE p.id = playlist_id AND p.is_public = TRUE
        )
    );

CREATE INDEX IF NOT EXISTS idx_playlist_songs_playlist_id ON public.playlist_songs (playlist_id);
CREATE INDEX IF NOT EXISTS idx_playlist_songs_user_id     ON public.playlist_songs (user_id);

-- ─── 5. Recently Played ───────────────────────────────────────────────────────
-- Keeps a rolling log of tracks the user has listened to.
CREATE TABLE IF NOT EXISTS public.recently_played (
    id              UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    song_id         TEXT        NOT NULL,
    song_title      TEXT        NOT NULL,
    song_artist     TEXT,
    song_thumbnail  TEXT,
    song_duration   INTEGER,
    played_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.recently_played ENABLE ROW LEVEL SECURITY;

CREATE POLICY "recently_played_own"
    ON public.recently_played USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_recently_played_user_id  ON public.recently_played (user_id);
CREATE INDEX IF NOT EXISTS idx_recently_played_played_at ON public.recently_played (played_at DESC);

-- ─── 6. AI Chat History ───────────────────────────────────────────────────────
-- Persists AI mood-playlist / chat conversations.
CREATE TABLE IF NOT EXISTS public.ai_chat_history (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    session_id  TEXT,                     -- groups messages into one conversation
    role        TEXT        NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.ai_chat_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY "ai_chat_history_own"
    ON public.ai_chat_history USING (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_ai_chat_user_session
    ON public.ai_chat_history (user_id, session_id, created_at);

-- ─── 7. App Settings Sync ─────────────────────────────────────────────────────
-- One row per user; stores a JSON blob of synced preferences.
CREATE TABLE IF NOT EXISTS public.app_settings (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE UNIQUE,
    settings_json JSONB       NOT NULL DEFAULT '{}',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "app_settings_own"
    ON public.app_settings USING (auth.uid() = user_id);

-- ─── 8. Direct Messages ───────────────────────────────────────────────────────
-- Stores one-to-one messages. Realtime subscriptions power the live feed.
CREATE TABLE IF NOT EXISTS public.messages (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    sender_id    UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    receiver_id  UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    content      TEXT        NOT NULL,
    is_read      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

-- A user can read messages they sent or received; they can send messages as
-- themselves; they can mark incoming messages as read.
CREATE POLICY "messages_select"
    ON public.messages FOR SELECT
    USING (auth.uid() = sender_id OR auth.uid() = receiver_id);
CREATE POLICY "messages_insert"
    ON public.messages FOR INSERT
    WITH CHECK (auth.uid() = sender_id);
CREATE POLICY "messages_update_read"
    ON public.messages FOR UPDATE
    USING (auth.uid() = receiver_id);       -- only receiver updates is_read

CREATE INDEX IF NOT EXISTS idx_messages_sender_receiver
    ON public.messages (sender_id, receiver_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_receiver_unread
    ON public.messages (receiver_id, is_read) WHERE is_read = FALSE;

-- ─── 9. Conversations ─────────────────────────────────────────────────────────
-- One row per unique pair of participants. Keeps track of the last activity so
-- conversation lists can be sorted without scanning the messages table.
CREATE TABLE IF NOT EXISTS public.conversations (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    participant1_id  UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    participant2_id  UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    last_message_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Always store the lower UUID first to keep the pair unique regardless of
    -- which user "started" the conversation.
    CONSTRAINT conversations_ordered_pair
        CHECK (participant1_id < participant2_id),
    UNIQUE (participant1_id, participant2_id)
);

ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

CREATE POLICY "conversations_participants"
    ON public.conversations FOR SELECT
    USING (auth.uid() = participant1_id OR auth.uid() = participant2_id);
CREATE POLICY "conversations_insert"
    ON public.conversations FOR INSERT
    WITH CHECK (auth.uid() = participant1_id OR auth.uid() = participant2_id);
CREATE POLICY "conversations_update"
    ON public.conversations FOR UPDATE
    USING (auth.uid() = participant1_id OR auth.uid() = participant2_id);

CREATE INDEX IF NOT EXISTS idx_conversations_p1 ON public.conversations (participant1_id);
CREATE INDEX IF NOT EXISTS idx_conversations_p2 ON public.conversations (participant2_id);

-- Auto-update last_message_at when a new message is inserted
CREATE OR REPLACE FUNCTION public.update_conversation_last_message()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER SET search_path = public
AS $$
DECLARE
    p1 UUID;
    p2 UUID;
BEGIN
    -- Ensure participant1_id is always the smaller UUID
    p1 := LEAST(NEW.sender_id, NEW.receiver_id);
    p2 := GREATEST(NEW.sender_id, NEW.receiver_id);

    INSERT INTO public.conversations (participant1_id, participant2_id, last_message_at)
    VALUES (p1, p2, NEW.created_at)
    ON CONFLICT (participant1_id, participant2_id)
    DO UPDATE SET last_message_at = EXCLUDED.last_message_at;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_message_inserted ON public.messages;
CREATE TRIGGER on_message_inserted
    AFTER INSERT ON public.messages
    FOR EACH ROW EXECUTE FUNCTION public.update_conversation_last_message();

-- ─── 10. Enable Realtime on messaging tables ──────────────────────────────────
-- Go to Supabase Dashboard → Database → Replication and add these tables,
-- OR run the statements below (only available in some Supabase versions).
-- ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
-- ALTER PUBLICATION supabase_realtime ADD TABLE public.conversations;

-- =============================================================================
-- DONE — run in Supabase SQL Editor to bootstrap your Moodsonix backend.
-- =============================================================================
