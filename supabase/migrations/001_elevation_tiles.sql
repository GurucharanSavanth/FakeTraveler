CREATE TABLE elevation_tiles (
    z smallint NOT NULL, x integer NOT NULL, y integer NOT NULL,
    raster_bytes bytea NOT NULL, updated_at timestamptz DEFAULT now(),
    PRIMARY KEY (z, x, y)
);
CREATE INDEX idx_elevation_tiles_brin ON elevation_tiles USING BRIN (z, x, y);
ALTER TABLE elevation_tiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY elevation_tiles_public_select ON elevation_tiles FOR SELECT TO public USING (true);
