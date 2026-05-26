import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface Waypoint {
  lat: number;
  lon: number;
}

interface RequestBody {
  waypoints: Waypoint[];
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  let body: RequestBody;
  try {
    body = await req.json();
  } catch {
    return new Response(
      JSON.stringify({ error: "Invalid JSON body" }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    );
  }

  const { waypoints } = body;

  if (!Array.isArray(waypoints) || waypoints.length < 2 || waypoints.length > 100) {
    return new Response(
      JSON.stringify({ error: "waypoints must be an array of 2-100 points" }),
      { status: 400, headers: { "Content-Type": "application/json" } },
    );
  }

  for (const wp of waypoints) {
    if (
      typeof wp.lat !== "number" || typeof wp.lon !== "number" ||
      wp.lat < -90 || wp.lat > 90 || wp.lon < -180 || wp.lon > 180
    ) {
      return new Response(
        JSON.stringify({ error: "Each waypoint must have valid lat (-90..90) and lon (-180..180)" }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      );
    }
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const supabase = createClient(supabaseUrl, supabaseKey);

  try {
    // Build pgr_dijkstra query across consecutive waypoint pairs
    const features: unknown[] = [];

    for (let i = 0; i < waypoints.length - 1; i++) {
      const src = waypoints[i];
      const dst = waypoints[i + 1];

      const { data, error } = await supabase.rpc("", {}).then(() =>
        supabase.from("road_segments").select("id, geom, maxspeed, source").limit(0)
      ).then(async () => {
        // Use raw SQL via postgrest RPC or direct query for pgr_dijkstra
        const query = `
          WITH nearest_src AS (
            SELECT id FROM road_segments
            ORDER BY geom <-> ST_SetSRID(ST_MakePoint(${dst.lon}, ${dst.lat}), 4326)
            LIMIT 1
          ),
          nearest_dst AS (
            SELECT id FROM road_segments
            ORDER BY geom <-> ST_SetSRID(ST_MakePoint(${src.lon}, ${src.lat}), 4326)
            LIMIT 1
          ),
          route AS (
            SELECT seq, edge, cost
            FROM pgr_dijkstra(
              'SELECT id, source::bigint, target::bigint, ST_Length(geom::geography) AS cost FROM road_segments',
              (SELECT id FROM nearest_src),
              (SELECT id FROM nearest_dst),
              directed := false
            )
          )
          SELECT
            r.seq,
            ST_AsGeoJSON(rs.geom)::json AS geometry,
            rs.maxspeed,
            r.cost
          FROM route r
          JOIN road_segments rs ON rs.id = r.edge
          WHERE r.edge != -1
          ORDER BY r.seq;
        `;

        return await supabase.rpc("exec_sql", { query });
      });

      if (error) {
        console.error("Routing error for segment", i, error);
        return new Response(
          JSON.stringify({ error: "Routing failed", detail: error.message }),
          { status: 500, headers: { "Content-Type": "application/json" } },
        );
      }

      if (data && Array.isArray(data)) {
        for (const row of data) {
          features.push({
            type: "Feature",
            geometry: row.geometry,
            properties: {
              segment: i,
              seq: row.seq,
              maxspeed: row.maxspeed,
              cost: row.cost,
            },
          });
        }
      }
    }

    const geojson = {
      type: "FeatureCollection",
      features,
    };

    return new Response(JSON.stringify(geojson), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    console.error("Unexpected error:", err);

    if (err instanceof Error && err.message.includes("timeout")) {
      return new Response(
        JSON.stringify({ error: "Gateway timeout" }),
        { status: 504, headers: { "Content-Type": "application/json" } },
      );
    }

    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
