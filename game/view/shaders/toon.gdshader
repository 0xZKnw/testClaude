shader_type spatial;
render_mode cull_back, specular_disabled, diffuse_burley;

// Cel-shading : la lumière est quantifiée en paliers au lieu d'un dégradé
// continu. C'est ce qui fait basculer le rendu de « low poly par défaut » à
// « stylisé ». La couleur vient des sommets — le jeu n'utilise aucune texture.

uniform float bands : hint_range(2.0, 6.0) = 3.0;
uniform float shadow_lift : hint_range(0.0, 1.0) = 0.42;
uniform float rim_strength : hint_range(0.0, 1.0) = 0.28;
uniform vec3 rim_color : source_color = vec3(0.72, 0.86, 1.0);
uniform float warm_shift : hint_range(0.0, 0.5) = 0.14;

varying vec3 v_albedo;
varying vec3 v_normal_world;
varying vec3 v_view_dir;

void vertex() {
	v_albedo = COLOR.rgb;
	v_normal_world = normalize((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz);
	vec3 world_pos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	v_view_dir = normalize(world_pos - (INV_VIEW_MATRIX * vec4(0.0, 0.0, 0.0, 1.0)).xyz);
}

void fragment() {
	ALBEDO = v_albedo;
	ROUGHNESS = 1.0;
	SPECULAR = 0.0;

	// Liseré lumineux sur les silhouettes : détache les volumes du fond sans
	// coûter un post-process.
	float rim = 1.0 - clamp(dot(v_normal_world, -v_view_dir), 0.0, 1.0);
	rim = pow(rim, 3.0);
	EMISSION = rim_color * rim * rim_strength * v_albedo;
}

void light() {
	float ndl = clamp(dot(NORMAL, LIGHT), 0.0, 1.0);

	// Quantification avec un adoucissement très court sur chaque marche :
	// des paliers parfaitement nets scintillent en mouvement sur mobile.
	float scaled = ndl * bands;
	float step_index = floor(scaled);
	float frac_part = scaled - step_index;
	float smoothed = step_index + smoothstep(0.42, 0.58, frac_part);
	float toon = smoothed / bands;

	// On relève les ombres au lieu de les laisser tomber au noir, et on les
	// réchauffe légèrement : c'est ce qui rend une palette saturée agréable.
	toon = mix(shadow_lift, 1.0, toon);
	vec3 tinted = LIGHT_COLOR * mix(vec3(1.0), vec3(1.0 + warm_shift, 1.0, 1.0 - warm_shift * 0.5), 1.0 - toon);

	DIFFUSE_LIGHT += ALBEDO * tinted * toon * ATTENUATION;
}
