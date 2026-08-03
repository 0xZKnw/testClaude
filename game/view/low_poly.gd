class_name LowPoly
extends RefCounted
## Constructeur de maillages low poly par code.
##
## Le jeu n'embarque AUCUN asset 3D externe : chaque bâtiment, unité et décor
## est assemblé ici à partir de boîtes, troncs de pyramide, toits et cylindres,
## avec la couleur écrite dans les sommets. Conséquences :
##   * dépôt léger, aucune licence d'asset à gérer
##   * un seul material pour tout (docs/07 §1)
##   * ajouter un bâtiment = écrire une fonction, pas ouvrir Blender
##
## Repère : Y vers le haut, une case de grille = 1.0 unité de monde.

var verts := PackedVector3Array()
var normals := PackedVector3Array()
var colors := PackedColorArray()

## Occlusion ambiante cuite dans les sommets : les parties basses des volumes
## sont assombries. C'est ce qui empêche un bâtiment low poly de ressembler à
## un empilement de cubes en plastique, et ça ne coûte rien à l'exécution.
var ao_strength := 1.0
var ao_height := 1.5      # hauteur à laquelle l'assombrissement s'annule
var ao_floor := 0.58      # facteur au niveau du sol


func clear() -> void:
	verts.clear()
	normals.clear()
	colors.clear()


func _ao(y: float) -> float:
	if ao_strength <= 0.0:
		return 1.0
	var k := clampf(y / ao_height, 0.0, 1.0)
	return lerpf(lerpf(1.0, ao_floor, ao_strength), 1.0, k)


func _tri(a: Vector3, b: Vector3, c: Vector3, col: Color) -> void:
	## Godot considère comme face avant un triangle enroulé dans le sens
	## horaire vu de face. La normale est donc (c-a)×(b-a) et NON (b-a)×(c-a) :
	## avec l'autre sens, les faces restent visibles (le culling ne dépend que
	## de l'enroulement) mais toutes les normales pointent vers l'intérieur, et
	## la scène n'est plus éclairée que par la lumière ambiante.
	var n := (c - a).cross(b - a)
	if n.length_squared() < 0.0000001:
		return
	n = n.normalized()
	# Les couleurs sont écrites en sRGB dans la palette, mais Godot interprète
	# les couleurs de sommets comme du LINÉAIRE. Sans cette conversion, les
	# teintes ressortent bien plus claires et saturées que celles choisies —
	# c'est ce qui donnait au jeu son aspect « fluo », et pourquoi assombrir la
	# palette ne changeait presque rien.
	var lin := col.srgb_to_linear()
	verts.push_back(a); verts.push_back(b); verts.push_back(c)
	normals.push_back(n); normals.push_back(n); normals.push_back(n)
	colors.push_back(lin * _ao(a.y))
	colors.push_back(lin * _ao(b.y))
	colors.push_back(lin * _ao(c.y))


func _quad(a: Vector3, b: Vector3, c: Vector3, d: Vector3, col: Color) -> void:
	_tri(a, b, c, col)
	_tri(a, c, d, col)


func add_frustum(center: Vector3, bottom: Vector2, top: Vector2, height: float,
		col: Color, top_col_delta: float = 0.08) -> void:
	## Volume principal des bâtiments : une boîte dont le haut peut être plus
	## étroit. Le très léger fruit donne tout de suite un aspect "construit".
	var y0 := center.y
	var y1 := center.y + height
	var bx := bottom.x * 0.5
	var bz := bottom.y * 0.5
	var tx := top.x * 0.5
	var tz := top.y * 0.5

	var b0 := Vector3(center.x - bx, y0, center.z - bz)
	var b1 := Vector3(center.x + bx, y0, center.z - bz)
	var b2 := Vector3(center.x + bx, y0, center.z + bz)
	var b3 := Vector3(center.x - bx, y0, center.z + bz)
	var t0 := Vector3(center.x - tx, y1, center.z - tz)
	var t1 := Vector3(center.x + tx, y1, center.z - tz)
	var t2 := Vector3(center.x + tx, y1, center.z + tz)
	var t3 := Vector3(center.x - tx, y1, center.z + tz)

	var side := col
	var side_dark := col.lerp(Color.BLACK, 0.14)
	_quad(b0, b1, t1, t0, side)                     # -Z
	_quad(b2, b3, t3, t2, side)                     # +Z
	_quad(b1, b2, t2, t1, side_dark)                # +X
	_quad(b3, b0, t0, t3, side_dark)                # -X
	_quad(t0, t1, t2, t3, col.lerp(Color.WHITE, top_col_delta))
	_quad(b3, b2, b1, b0, col.lerp(Color.BLACK, 0.35))


func add_box(base_center: Vector3, size: Vector3, col: Color) -> void:
	## ATTENTION : la boîte est ancrée par sa BASE (comme add_frustum), pas par
	## son centre. Tout le code de formes suppose ce repère.
	add_frustum(base_center, Vector2(size.x, size.z), Vector2(size.x, size.z), size.y, col, 0.06)


func add_roof(center: Vector3, size: Vector2, height: float, col: Color,
		overhang: float = 0.12) -> void:
	## Toit à deux pans, faîtage sur l'axe X.
	var hx := size.x * 0.5 + overhang
	var hz := size.y * 0.5 + overhang
	# Corniche : une dalle fine qui recouvre le dessus du mur. Sans elle, la
	# face supérieure (éclaircie) affleure sous l'avant-toit et dessine un
	# liseré clair le long de l'arête.
	add_box(Vector3(center.x, center.y - 0.02, center.z),
			Vector3(hx * 2.0, 0.09, hz * 2.0), col.lerp(Color.BLACK, 0.42))
	var y0 := center.y + 0.06
	var y1 := center.y + 0.06 + height
	var a := Vector3(center.x - hx, y0, center.z - hz)
	var b := Vector3(center.x + hx, y0, center.z - hz)
	var c := Vector3(center.x + hx, y0, center.z + hz)
	var d := Vector3(center.x - hx, y0, center.z + hz)
	var r0 := Vector3(center.x - hx, y1, center.z)
	var r1 := Vector3(center.x + hx, y1, center.z)

	_quad(a, b, r1, r0, col)                        # pan -Z
	_quad(c, d, r0, r1, col.lerp(Color.BLACK, 0.12))# pan +Z
	_tri(b, c, r1, col.lerp(Color.BLACK, 0.2))      # pignon +X
	_tri(d, a, r0, col.lerp(Color.BLACK, 0.2))      # pignon -X
	# Fermer le dessous : sans cette face, l'avant-toit laisse voir à travers
	# le volume (les faces arrière sont éliminées) et dessine un liseré clair
	# le long des arêtes.
	_quad(d, c, b, a, col.lerp(Color.BLACK, 0.45))


func add_pyramid(center: Vector3, size: Vector2, height: float, col: Color) -> void:
	var hx := size.x * 0.5
	var hz := size.y * 0.5
	var y0 := center.y
	var apex := Vector3(center.x, center.y + height, center.z)
	var a := Vector3(center.x - hx, y0, center.z - hz)
	var b := Vector3(center.x + hx, y0, center.z - hz)
	var c := Vector3(center.x + hx, y0, center.z + hz)
	var d := Vector3(center.x - hx, y0, center.z + hz)
	_tri(a, b, apex, col)
	_tri(b, c, apex, col.lerp(Color.BLACK, 0.1))
	_tri(c, d, apex, col.lerp(Color.BLACK, 0.16))
	_tri(d, a, apex, col.lerp(Color.BLACK, 0.06))


func add_cylinder(center: Vector3, radius_bottom: float, radius_top: float,
		height: float, sides: int, col: Color) -> void:
	var y0 := center.y
	var y1 := center.y + height
	var side_count := maxi(3, sides)
	for i in range(side_count):
		var a0 := TAU * float(i) / float(side_count)
		var a1 := TAU * float(i + 1) / float(side_count)
		var c0 := Vector3(center.x + cos(a0) * radius_bottom, y0, center.z + sin(a0) * radius_bottom)
		var c1 := Vector3(center.x + cos(a1) * radius_bottom, y0, center.z + sin(a1) * radius_bottom)
		var t0 := Vector3(center.x + cos(a0) * radius_top, y1, center.z + sin(a0) * radius_top)
		var t1 := Vector3(center.x + cos(a1) * radius_top, y1, center.z + sin(a1) * radius_top)
		var shade := col.lerp(Color.BLACK, 0.16 * abs(sin(a0)))
		if radius_top > 0.001:
			_quad(c0, c1, t1, t0, shade)
		else:
			_tri(c0, c1, t0, shade)
	if radius_top > 0.001:
		var top_c := Vector3(center.x, y1, center.z)
		for i in range(side_count):
			var a0 := TAU * float(i) / float(side_count)
			var a1 := TAU * float(i + 1) / float(side_count)
			var t0 := Vector3(center.x + cos(a0) * radius_top, y1, center.z + sin(a0) * radius_top)
			var t1 := Vector3(center.x + cos(a1) * radius_top, y1, center.z + sin(a1) * radius_top)
			_tri(top_c, t0, t1, col.lerp(Color.WHITE, 0.08))


func add_plate(center: Vector3, size: Vector2, thickness: float, col: Color) -> void:
	add_box(Vector3(center.x, center.y, center.z), Vector3(size.x, thickness, size.y), col)


func build() -> ArrayMesh:
	var mesh := ArrayMesh.new()
	if verts.is_empty():
		return mesh
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = verts
	arrays[Mesh.ARRAY_NORMAL] = normals
	arrays[Mesh.ARRAY_COLOR] = colors
	mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
	return mesh


func triangle_count() -> int:
	return verts.size() / 3
