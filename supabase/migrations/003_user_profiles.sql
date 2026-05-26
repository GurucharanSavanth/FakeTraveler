CREATE TABLE user_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    profile_blob jsonb NOT NULL, encrypted boolean DEFAULT true,
    updated_at timestamptz DEFAULT now()
);
CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY user_profiles_owner_select ON user_profiles FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY user_profiles_owner_insert ON user_profiles FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY user_profiles_owner_update ON user_profiles FOR UPDATE TO authenticated USING (auth.uid() = user_id);
CREATE POLICY user_profiles_owner_delete ON user_profiles FOR DELETE TO authenticated USING (auth.uid() = user_id);
