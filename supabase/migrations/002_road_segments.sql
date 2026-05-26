CREATE EXTENSION IF NOT EXISTS postgis;
CREATE TABLE road_segments (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    geom geometry(LineString, 4326) NOT NULL, maxspeed smallint,
    source text, updated_at timestamptz DEFAULT now()
);
CREATE INDEX idx_road_geom ON road_segments USING GIST (geom);
ALTER TABLE road_segments ENABLE ROW LEVEL SECURITY;
CREATE POLICY road_segments_public_select ON road_segments FOR SELECT TO public USING (true);
