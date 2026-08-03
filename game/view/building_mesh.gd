class_name BuildingMesh
extends RefCounted
## Fabrique de maillages : une fonction par silhouette.
##
## Trois paliers visuels seulement (niveaux 1-5, 6-10, 11+), comme prévu dans
## docs/07 : produire 15 maillages par bâtiment serait ruineux, et l'oeil ne
## lit de toute façon que les changements francs de silhouette.

static var _cache: Dictionary = {}


static func tier_for_level(level: int) -> int:
	if level >= 11:
		return 3
	if level >= 6:
		return 2
	return 1


static func get_mesh(shape: String, size: int, level: int, color_name: String) -> ArrayMesh:
	var tier := tier_for_level(level)
	var key := "%s|%d|%d|%s" % [shape, size, tier, color_name]
	if _cache.has(key):
		return _cache[key]
	var lp := LowPoly.new()
	var col: Color = Palette.get_color(color_name)
	var s := float(size)
	match shape:
		"keep": _keep(lp, s, tier, col)
		"workshop": _workshop(lp, s, tier, col)
		"house": _house(lp, s, tier, col)
		"mine": _mine(lp, s, tier, col)
		"store": _store(lp, s, tier, col)
		"tower": _tower(lp, s, tier, col)
		"cannon": _cannon(lp, s, tier, col)
		"wall": _wall(lp, s, tier, col)
		"tree": _tree(lp, s, tier, col)
		"statue": _statue(lp, s, tier, col)
		_: _workshop(lp, s, tier, col)
	var mesh := lp.build()
	_cache[key] = mesh
	return mesh


static func get_unit_mesh(shape: String, color_name: String) -> ArrayMesh:
	var key := "unit|%s|%s" % [shape, color_name]
	if _cache.has(key):
		return _cache[key]
	var lp := LowPoly.new()
	var col: Color = Palette.get_color(color_name)
	match shape:
		"ram": _unit_ram(lp, col)
		_: _unit_soldier(lp, col)
	var mesh := lp.build()
	_cache[key] = mesh
	return mesh


static func clear_cache() -> void:
	_cache.clear()


# ------------------------------------------------------------------ formes

static func _stone() -> Color: return Palette.get_color("stone")
static func _stone_dark() -> Color: return Palette.get_color("stone_dark")
static func _wood() -> Color: return Palette.get_color("wood")
static func _wood_dark() -> Color: return Palette.get_color("wood_dark")


static func _keep(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	## Le Bastion : la pièce maîtresse du village, il doit dominer.
	var base := s * 0.86
	lp.add_frustum(Vector3.ZERO, Vector2(base, base), Vector2(base * 0.92, base * 0.92), 0.35, _stone_dark())
	var body := s * 0.62
	var h := 1.1 + 0.35 * float(tier)
	lp.add_frustum(Vector3(0, 0.35, 0), Vector2(body, body), Vector2(body * 0.88, body * 0.88), h, _stone())
	lp.add_roof(Vector3(0, 0.35 + h, 0), Vector2(body * 0.88, body * 0.88), 0.55 + 0.1 * float(tier), col)
	# Tourelles d'angle, ajoutées à mesure que le Bastion monte.
	var corner := base * 0.5 - 0.18
	var count := 2 if tier == 1 else 4
	var offsets := [Vector2(-1, -1), Vector2(1, 1), Vector2(1, -1), Vector2(-1, 1)]
	for i in range(count):
		var o: Vector2 = offsets[i]
		var p := Vector3(o.x * corner, 0.35, o.y * corner)
		lp.add_cylinder(p, 0.26, 0.24, 0.75 + 0.2 * float(tier), 6, _stone())
		lp.add_cylinder(p + Vector3(0, 0.75 + 0.2 * float(tier), 0), 0.3, 0.0, 0.4, 6, col)
	if tier >= 2:
		lp.add_box(Vector3(0, 0.35 + h + 0.9, 0), Vector3(0.06, 0.5, 0.06), _wood_dark())
		lp.add_box(Vector3(0.16, 0.35 + h + 1.25, 0), Vector3(0.3, 0.18, 0.02), Palette.get_color("gold"))


static func _workshop(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.78
	var h := 0.55 + 0.16 * float(tier)
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w * 0.95, w * 0.95), h, _wood())
	lp.add_roof(Vector3(0, h, 0), Vector2(w * 0.95, w * 0.95), 0.42 + 0.08 * float(tier), col)
	lp.add_box(Vector3(w * 0.28, h, w * 0.22), Vector3(0.2, 0.55 + 0.15 * float(tier), 0.2), _stone_dark())
	lp.add_box(Vector3(0, 0.01, w * 0.5 - 0.02), Vector3(w * 0.26, h * 0.62, 0.06), _wood_dark())
	if tier >= 2:
		lp.add_box(Vector3(-w * 0.42, 0.0, -w * 0.1), Vector3(0.18, 0.4, w * 0.5), _wood_dark())
	if tier >= 3:
		lp.add_cylinder(Vector3(w * 0.42, 0.0, w * 0.34), 0.22, 0.22, 0.28, 8, _stone())


static func _house(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.72
	var h := 0.5 + 0.14 * float(tier)
	lp.add_frustum(Vector3.ZERO, Vector2(w, w * 0.86), Vector2(w * 0.96, w * 0.82), h, Palette.get_color("white"))
	lp.add_roof(Vector3(0, h, 0), Vector2(w * 0.96, w * 0.82), 0.4 + 0.08 * float(tier), col)
	lp.add_box(Vector3(0, 0.01, w * 0.44), Vector3(w * 0.24, h * 0.66, 0.05), _wood_dark())
	# Colombages : quelques lattes suffisent à lire "maison" de loin.
	# (add_box ancre la boîte par sa BASE, comme add_frustum.)
	for i in range(3):
		var x := -w * 0.3 + float(i) * w * 0.3
		lp.add_box(Vector3(x, 0.02, -w * 0.44), Vector3(0.05, h * 0.92, 0.04), _wood_dark())
	if tier >= 2:
		lp.add_box(Vector3(w * 0.22, h + 0.25, 0), Vector3(0.16, 0.45, 0.16), _stone_dark())


static func _mine(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.8
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w * 0.6, w * 0.6), 0.42, Palette.get_color("dirt"))
	# Cadre d'entrée
	lp.add_box(Vector3(0, 0, w * 0.34), Vector3(w * 0.44, 0.5, 0.08), _wood_dark())
	lp.add_box(Vector3(-w * 0.2, 0, w * 0.3), Vector3(0.08, 0.55, 0.08), _wood_dark())
	lp.add_box(Vector3(w * 0.2, 0, w * 0.3), Vector3(0.08, 0.55, 0.08), _wood_dark())
	# Chevalement
	var h := 0.7 + 0.22 * float(tier)
	lp.add_cylinder(Vector3(0, 0.42, 0), 0.2, 0.12, h, 5, _wood_dark())
	lp.add_cylinder(Vector3(0, 0.42 + h, 0), 0.26, 0.26, 0.1, 6, col)
	for i in range(mini(tier + 1, 4)):
		var a := TAU * float(i) / 4.0
		lp.add_pyramid(Vector3(cos(a) * w * 0.36, 0.42, sin(a) * w * 0.36), Vector2(0.3, 0.3), 0.22, col)


static func _store(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.76
	var h := 0.5 + 0.12 * float(tier)
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w, w), h, col)
	lp.add_roof(Vector3(0, h, 0), Vector2(w, w), 0.3, _wood_dark(), 0.06)
	var crates := 2 + tier
	for i in range(crates):
		var fx := (float(i % 2) - 0.5) * w * 0.44
		var fz := (float(i / 2) - 0.5) * w * 0.44
		lp.add_box(Vector3(fx, h + 0.3, fz), Vector3(0.26, 0.24, 0.26), _wood())


static func _tower(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var r := s * 0.34
	var h := 0.9 + 0.3 * float(tier)
	lp.add_cylinder(Vector3.ZERO, r * 1.15, r, 0.18, 8, _stone_dark())
	lp.add_cylinder(Vector3(0, 0.18, 0), r, r * 0.92, h, 8, col)
	# Créneaux
	var top := 0.18 + h
	for i in range(8):
		var a := TAU * float(i) / 8.0
		lp.add_box(Vector3(cos(a) * r * 0.86, top, sin(a) * r * 0.86), Vector3(0.14, 0.2, 0.14), _stone())
	if tier >= 2:
		lp.add_cylinder(Vector3(0, top + 0.2, 0), r * 0.9, 0.0, 0.5, 8, Palette.get_color("roof_red"))
	if tier >= 3:
		lp.add_cylinder(Vector3(0, 0.18, 0), r * 1.25, r * 1.25, 0.12, 8, _stone())


static func _cannon(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.7
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w * 0.8, w * 0.8), 0.34, _stone_dark())
	lp.add_box(Vector3(0, 0.34, 0), Vector3(w * 0.5, 0.22, w * 0.5), _wood_dark())
	var barrel_len := 0.55 + 0.12 * float(tier)
	lp.add_cylinder(Vector3(0, 0.5, 0), 0.17, 0.13, barrel_len, 8, col)
	lp.add_box(Vector3(0, 0.44, 0), Vector3(0.34, 0.14, 0.34), Palette.get_color("iron"))
	if tier >= 3:
		lp.add_cylinder(Vector3(0, 0.5 + barrel_len, 0), 0.19, 0.19, 0.1, 8, Palette.get_color("gold"))


static func _wall(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.98
	var h := 0.45 + 0.1 * float(tier)
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w * 0.9, w * 0.9), h, col)
	for i in range(2):
		for j in range(2):
			var fx := (float(i) - 0.5) * w * 0.5
			var fz := (float(j) - 0.5) * w * 0.5
			lp.add_box(Vector3(fx, h, fz), Vector3(w * 0.34, 0.16, w * 0.34), _stone())


static func _tree(lp: LowPoly, _s: float, _tier: int, col: Color) -> void:
	lp.add_cylinder(Vector3.ZERO, 0.09, 0.07, 0.34, 5, Palette.get_color("wood_dark"))
	lp.add_pyramid(Vector3(0, 0.3, 0), Vector2(0.62, 0.62), 0.55, col)
	lp.add_pyramid(Vector3(0, 0.62, 0), Vector2(0.44, 0.44), 0.45, Palette.get_color("leaf_dark"))


static func _statue(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var w := s * 0.5
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w * 0.75, w * 0.75), 0.3, _stone_dark())
	lp.add_box(Vector3(0, 0.3, 0), Vector3(0.22, 0.55 + 0.08 * float(tier), 0.16), col)
	lp.add_box(Vector3(0, 0.85 + 0.08 * float(tier), 0), Vector3(0.17, 0.17, 0.17), col)
	lp.add_box(Vector3(0.16, 0.55, 0), Vector3(0.06, 0.45, 0.06), Palette.get_color("gold"))


# ------------------------------------------------------------------- unités

static func _unit_soldier(lp: LowPoly, col: Color) -> void:
	lp.add_frustum(Vector3(0, 0.05, 0), Vector2(0.2, 0.15), Vector2(0.17, 0.13), 0.24, col)
	lp.add_box(Vector3(0, 0.29, 0), Vector3(0.14, 0.13, 0.14), Palette.get_color("white"))
	lp.add_box(Vector3(0, 0.38, 0), Vector3(0.16, 0.06, 0.16), Palette.get_color("iron"))
	lp.add_box(Vector3(0.12, 0.1, 0), Vector3(0.04, 0.3, 0.04), Palette.get_color("wood_dark"))
	lp.add_box(Vector3(-0.02, 0.0, 0.0), Vector3(0.08, 0.06, 0.08), Palette.get_color("dark"))


static func _unit_ram(lp: LowPoly, col: Color) -> void:
	lp.add_box(Vector3(0, 0.12, 0), Vector3(0.42, 0.1, 0.28), Palette.get_color("wood_dark"))
	lp.add_cylinder(Vector3(0, 0.22, 0), 0.09, 0.09, 0.5, 6, col)
	for sx in [-0.16, 0.16]:
		for sz in [-0.13, 0.13]:
			lp.add_cylinder(Vector3(sx, 0.0, sz), 0.09, 0.09, 0.05, 6, Palette.get_color("dark"))


# ------------------------------------------------------------------- terrain

static func make_ground(w: int, h: int, seed_value: int, margin: int = 16) -> ArrayMesh:
	## Sol en damier irrégulier : la variation par tuile suffit à donner de la
	## vie sans texture ni normal map.
	##
	## La marge déborde volontairement de la zone jouable : sans elle, on voit
	## le bord du terrain et le ciel en dessous dès qu'on dézoome.
	var lp := LowPoly.new()
	var rng := DetRandom.new(seed_value)
	var grass: Color = Palette.get_color("grass")
	var grass_dark: Color = Palette.get_color("grass_dark")
	for y in range(-margin, h + margin):
		for x in range(-margin, w + margin):
			var t := float(rng.next_range(0, 100)) / 100.0
			var c := grass.lerp(grass_dark, t * 0.55)
			var a := Vector3(float(x), 0.0, float(y))
			var b := Vector3(float(x + 1), 0.0, float(y))
			var cc := Vector3(float(x + 1), 0.0, float(y + 1))
			var d := Vector3(float(x), 0.0, float(y + 1))
			lp._quad(a, b, cc, d, c)
	return lp.build()
