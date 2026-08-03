shader_type spatial;
render_mode cull_front, unshaded, shadows_disabled, depth_draw_opaque;

// Contour par « coque inversée » : on redessine le maillage légèrement gonflé
// le long des normales, en n'affichant que les faces arrière. Ce qui dépasse
// forme le trait. Une seule passe supplémentaire, compatible MultiMesh, et
// aucun post-process — donc utilisable sur téléphone d'entrée de gamme.

uniform float width : hint_range(0.0, 0.2) = 0.035;
uniform vec3 outline_color : source_color = vec3(0.10, 0.11, 0.15);
uniform float distance_fade_start = 45.0;
uniform float distance_fade_end = 75.0;

varying float v_fade;

void vertex() {
	// L'épaisseur suit la distance à la caméra : sans ça le trait devient un
	// pâté quand on dézoome sur tout le village.
	vec3 world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	vec3 cam_pos = (INV_VIEW_MATRIX * vec4(0.0, 0.0, 0.0, 1.0)).xyz;
	float dist = length(world_pos - cam_pos);
	v_fade = 1.0 - smoothstep(distance_fade_start, distance_fade_end, dist);
	VERTEX += NORMAL * width * v_fade;
}

void fragment() {
	ALBEDO = outline_color;
	// Au-delà de la distance de fondu la coque ne dépasse plus : le trait
	// disparaît de lui-même, sans transparence ni tri supplémentaire.
	ALPHA = 1.0;
}
