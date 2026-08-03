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


static func get_mesh(shape: String, size: int, level: int, color_name: String,
		id: String = "") -> ArrayMesh:
	var tier := tier_for_level(level)
	var key := "%s|%d|%d|%s|%s" % [shape, size, tier, color_name, id]
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
		"tent": _tent(lp, s, tier, col)
		"windmill": _windmill(lp, s, tier, col)
		"market": _market(lp, s, tier, col)
		"temple": _temple(lp, s, tier, col)
		"fountain": _fountain(lp, s, tier, col)
		_: _workshop(lp, s, tier, col)
	# Signe distinctif propre au bâtiment : sept bâtiments partagent la
	# silhouette « atelier », et sans cela on ne distingue une Caserne d'une
	# Scierie qu'à la couleur du toit — illisible pour un daltonien, et pénible
	# pour tout le monde.
	_topper(lp, id, s, tier)
	var mesh := lp.build()
	_cache[key] = mesh
	return mesh


static func _topper(lp: LowPoly, id: String, s: float, tier: int) -> void:
	var w := s * 0.78
	var h := 0.55 + 0.16 * float(tier)
	match id:
		"sawmill":
			# Pile de rondins + lame de scie
			for i in range(3):
				lp.add_cylinder(Vector3(-w * 0.34 + float(i) * 0.16, 0.0, -w * 0.46),
						0.09, 0.09, 0.42, 6, _wood_dark())
			lp.add_cylinder(Vector3(w * 0.1, h + 0.42, -w * 0.3), 0.26, 0.26, 0.05, 10,
					Palette.get_color("iron"))
		"barracks":
			# Râtelier d'armes + bannière
			for i in range(3):
				lp.add_box(Vector3(-w * 0.3 + float(i) * 0.2, 0.0, w * 0.42),
						Vector3(0.05, 0.62, 0.05), Palette.get_color("iron"))
			lp.add_box(Vector3(-w * 0.45, 0.0, -w * 0.4), Vector3(0.06, h + 0.7, 0.06), _wood_dark())
			lp.add_box(Vector3(-w * 0.45 + 0.16, h + 0.35, -w * 0.4),
					Vector3(0.3, 0.34, 0.03), Palette.get_color("roof_red"))
		"archer_camp":
			# Cibles de tir
			for i in range(2):
				lp.add_cylinder(Vector3(w * 0.36, 0.0, -w * 0.3 + float(i) * 0.5),
						0.03, 0.03, 0.5, 5, _wood_dark())
				lp.add_cylinder(Vector3(w * 0.36, 0.5, -w * 0.3 + float(i) * 0.5),
						0.16, 0.16, 0.05, 10, Palette.get_color("white"))
		"forge", "siege_works":
			# Cheminée trapue et enclume : ça fume, ça martèle
			lp.add_cylinder(Vector3(-w * 0.3, h, w * 0.3), 0.19, 0.16, 0.7 + 0.1 * float(tier), 8,
					Palette.get_color("stone_dark"))
			lp.add_box(Vector3(w * 0.3, 0.0, w * 0.36), Vector3(0.3, 0.2, 0.18),
					Palette.get_color("iron"))
			if tier >= 2:
				lp.add_cylinder(Vector3(-w * 0.3, h + 0.72, w * 0.3), 0.22, 0.1, 0.14, 8,
						Palette.get_color("roof_orange"))
		"elite_camp":
			# Trois bannières : le camp d'élite doit se voir de loin
			for i in range(3):
				var x := -w * 0.3 + float(i) * w * 0.3
				lp.add_box(Vector3(x, h, 0.0), Vector3(0.05, 0.8, 0.05), _wood_dark())
				lp.add_box(Vector3(x + 0.14, h + 0.45, 0.0), Vector3(0.26, 0.3, 0.03),
						Palette.get_color("roof_purple"))
		"mill":
			lp.add_cylinder(Vector3(w * 0.36, 0.0, -w * 0.36), 0.24, 0.24, 0.34, 10,
					Palette.get_color("stone"))
			lp.add_cylinder(Vector3(w * 0.36, 0.34, -w * 0.36), 0.1, 0.1, 0.26, 6, _wood_dark())
		"library":
			lp.add_box(Vector3(0.0, 0.0, w * 0.42), Vector3(0.5, 0.16, 0.26),
					Palette.get_color("roof_blue"))


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
	## Le Bastion : c'est LE repère visuel du village. Il doit se lire comme un
	## château fort, pas comme une grande maison — soubassement crénelé, tours
	## d'angle qui dépassent, herse.
	var base := s * 0.9
	var rampart := 0.52 + 0.06 * float(tier)
	lp.add_frustum(Vector3.ZERO, Vector2(base, base),
			Vector2(base * 0.94, base * 0.94), rampart, _stone_dark())

	# Créneaux du rempart
	var steps := 7
	for i in range(steps):
		var t := (float(i) / float(steps - 1)) - 0.5
		for sgn in [-1.0, 1.0]:
			lp.add_box(Vector3(t * base * 0.9, rampart, sgn * base * 0.45),
					Vector3(base * 0.09, 0.16, base * 0.08), _stone())
			lp.add_box(Vector3(sgn * base * 0.45, rampart, t * base * 0.9),
					Vector3(base * 0.08, 0.16, base * 0.09), _stone())

	# Donjon central
	var body := s * 0.5
	var h := 1.25 + 0.4 * float(tier)
	lp.add_frustum(Vector3(0, rampart, 0), Vector2(body, body),
			Vector2(body * 0.9, body * 0.9), h, _stone())
	lp.add_roof(Vector3(0, rampart + h, 0), Vector2(body * 0.9, body * 0.9),
			0.62 + 0.12 * float(tier), col)

	# Tours d'angle : deux au départ, quatre dès le palier 2.
	var corner := base * 0.45
	var count := 2 if tier == 1 else 4
	var offsets := [Vector2(-1, -1), Vector2(1, 1), Vector2(1, -1), Vector2(-1, 1)]
	var tower_h := 1.0 + 0.3 * float(tier)
	for i in range(count):
		var o: Vector2 = offsets[i]
		var p := Vector3(o.x * corner, 0.0, o.y * corner)
		lp.add_cylinder(p, 0.3, 0.27, tower_h, 7, _stone())
		for k in range(6):
			var a := TAU * float(k) / 6.0
			lp.add_box(Vector3(p.x + cos(a) * 0.24, tower_h, p.z + sin(a) * 0.24),
					Vector3(0.11, 0.14, 0.11), _stone_dark())
		lp.add_cylinder(Vector3(p.x, tower_h + 0.14, p.z), 0.33, 0.0, 0.46, 7, col)

	# Herse : la porte se voit, donc le village a une entrée.
	lp.add_box(Vector3(0, 0.0, base * 0.46), Vector3(base * 0.26, rampart * 0.82, 0.06),
			_wood_dark())
	if tier >= 2:
		lp.add_box(Vector3(0, rampart + h + 0.95, 0), Vector3(0.06, 0.55, 0.06), _wood_dark())
		lp.add_box(Vector3(0.17, rampart + h + 1.3, 0), Vector3(0.32, 0.2, 0.02),
				Palette.get_color("gold"))


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


static func _tent(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	## Campement : une bâche tendue, quelques rondins. Silhouette basse, qui se
	## distingue immédiatement des bâtiments en dur.
	var w := s * 0.8
	lp.add_frustum(Vector3.ZERO, Vector2(w, w * 0.9), Vector2(w * 0.9, w * 0.8), 0.12,
			Palette.get_color("dirt"))
	lp.add_roof(Vector3(0, 0.12, 0), Vector2(w * 0.86, w * 0.76), 0.62 + 0.1 * float(tier), col, 0.05)
	for i in range(2 + tier):
		var a := TAU * float(i) / float(2 + tier)
		lp.add_cylinder(Vector3(cos(a) * w * 0.46, 0.0, sin(a) * w * 0.42), 0.07, 0.06, 0.3, 5,
				_wood_dark())
	lp.add_box(Vector3(w * 0.3, 0.12, w * 0.36), Vector3(0.24, 0.16, 0.24), _wood())


static func _windmill(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var r := s * 0.3
	var h := 0.9 + 0.22 * float(tier)
	lp.add_cylinder(Vector3.ZERO, r * 1.2, r * 0.86, h, 8, Palette.get_color("white"))
	lp.add_cylinder(Vector3(0, h, 0), r * 1.0, 0.0, 0.45, 8, col)
	# Les pales : le détail qui fait lire « moulin » en une fraction de seconde.
	var hub := Vector3(0, h * 0.78, r * 1.05)
	for i in range(4):
		var a := TAU * float(i) / 4.0
		lp.add_box(Vector3(hub.x + cos(a) * 0.05, hub.y + sin(a) * 0.05, hub.z),
				Vector3(0.09 + absf(cos(a)) * 0.7, 0.09 + absf(sin(a)) * 0.7, 0.06), _wood_dark())
	lp.add_cylinder(hub, 0.1, 0.1, 0.12, 6, Palette.get_color("iron"))


static func _market(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	## Halle ouverte : des piliers, un toit, des étals de couleur.
	var w := s * 0.82
	lp.add_frustum(Vector3.ZERO, Vector2(w, w), Vector2(w, w), 0.14, Palette.get_color("path"))
	var post_h := 0.62 + 0.08 * float(tier)
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			lp.add_box(Vector3(sx * w * 0.4, 0.14, sz * w * 0.4),
					Vector3(0.13, post_h, 0.13), _wood_dark())
	lp.add_roof(Vector3(0, 0.14 + post_h, 0), Vector2(w * 0.98, w * 0.98),
			0.4 + 0.06 * float(tier), col, 0.16)
	var stall_colors := ["roof_red", "roof_blue", "roof_green", "roof_teal"]
	for i in range(mini(2 + tier, 4)):
		var fx := (float(i % 2) - 0.5) * w * 0.5
		var fz := (float(i / 2) - 0.5) * w * 0.5
		lp.add_box(Vector3(fx, 0.14, fz), Vector3(w * 0.3, 0.22, w * 0.26),
				Palette.get_color(String(stall_colors[i])))


static func _temple(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var base := s * 0.86
	for i in range(3):
		var k := 1.0 - float(i) * 0.16
		lp.add_frustum(Vector3(0, float(i) * 0.16, 0), Vector2(base * k, base * k),
				Vector2(base * (k - 0.05), base * (k - 0.05)), 0.16, _stone())
	var body := base * 0.5
	var h := 0.7 + 0.2 * float(tier)
	lp.add_frustum(Vector3(0, 0.48, 0), Vector2(body, body), Vector2(body * 0.9, body * 0.9),
			h, Palette.get_color("white"))
	lp.add_pyramid(Vector3(0, 0.48 + h, 0), Vector2(body * 1.1, body * 1.1),
			0.55 + 0.12 * float(tier), col)
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			lp.add_cylinder(Vector3(sx * base * 0.34, 0.48, sz * base * 0.34),
					0.09, 0.08, h * 0.85, 6, Palette.get_color("white"))
	if tier >= 2:
		lp.add_cylinder(Vector3(0, 0.48 + h + 0.6, 0), 0.14, 0.0, 0.34, 6,
				Palette.get_color("gold"))


static func _fountain(lp: LowPoly, s: float, tier: int, col: Color) -> void:
	var r := s * 0.42
	lp.add_cylinder(Vector3.ZERO, r, r * 0.96, 0.18, 10, _stone())
	lp.add_cylinder(Vector3(0, 0.16, 0), r * 0.84, r * 0.84, 0.04, 10, col)
	lp.add_cylinder(Vector3(0, 0.2, 0), 0.12, 0.09, 0.34 + 0.06 * float(tier), 8, _stone())
	lp.add_cylinder(Vector3(0, 0.54 + 0.06 * float(tier), 0), 0.22, 0.05, 0.08, 8, _stone())


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

const GRASS_MARGIN := 11
const SAND_RING := 4
const PLATEAU_DROP := 0.14   # marche qui délimite la zone constructible
const SHORE_DROP := 0.34
const CLIFF_BOTTOM := -1.8
const WATER_LEVEL := -0.55


static func make_ground(w: int, h: int, seed_value: int) -> ArrayMesh:
	## Une île, pas une nappe infinie.
	##
	## Trois intentions :
	##  * une marche visible délimite la zone constructible — le joueur voit où
	##    il peut poser sans avoir besoin d'une grille affichée en permanence ;
	##  * plage puis falaise : le terrain se termine proprement au lieu de
	##    s'arrêter dans le vide ;
	##  * variation de teinte par tuile, pour l'aspect low poly peint à la main.
	var lp := LowPoly.new()
	lp.ao_strength = 0.0   # le sol est plat : une AO verticale l'assombrirait sans raison
	var rng := DetRandom.new(seed_value)

	var grass: Color = Palette.get_color("grass")
	var grass_dark: Color = Palette.get_color("grass_dark")
	var grass_deep: Color = Palette.get_color("grass_deep")
	var sand: Color = Palette.get_color("sand")
	var sand_dark: Color = Palette.get_color("sand_dark")
	var rock: Color = Palette.get_color("rock")
	var rock_dark: Color = Palette.get_color("rock_dark")
	var dirt: Color = Palette.get_color("dirt")

	var outer := GRASS_MARGIN + SAND_RING
	for y in range(-outer, h + outer):
		for x in range(-outer, w + outer):
			var d := maxi(maxi(-x, x - w + 1), maxi(-y, y - h + 1))
			var t := _patch_noise(x, y)
			var level := 0.0
			var col := grass

			if d <= 0:
				col = grass.lerp(grass_dark, t * 0.4)
			elif d <= GRASS_MARGIN:
				level = -PLATEAU_DROP
				var far := float(d) / float(GRASS_MARGIN)
				col = grass_dark.lerp(grass_deep, t * 0.5 + far * 0.25)
			else:
				var s := float(d - GRASS_MARGIN) / float(SAND_RING)
				level = -PLATEAU_DROP - SHORE_DROP * s
				col = sand.lerp(sand_dark, t * 0.5)

			lp._quad(
				Vector3(float(x), level, float(y)),
				Vector3(float(x + 1), level, float(y)),
				Vector3(float(x + 1), level, float(y + 1)),
				Vector3(float(x), level, float(y + 1)), col)

	# Marche du plateau : la bordure de la zone constructible.
	_skirt(lp, 0, 0, w, h, 0.0, -PLATEAU_DROP, dirt)
	# Falaise : de la plage jusque sous l'eau.
	var lo := -outer
	var hi_w := w + outer
	var hi_h := h + outer
	_skirt(lp, lo, lo, hi_w - lo, hi_h - lo,
			-PLATEAU_DROP - SHORE_DROP, CLIFF_BOTTOM, rock)
	_cliff_rocks(lp, lo, lo, hi_w, hi_h, rng, rock, rock_dark)
	return lp.build()


static func _patch_noise(x: int, y: int) -> float:
	## Bruit par plaques de 4x4 cases, adouci par une petite variation locale.
	## Le résultat se lit comme des zones d'herbe plutôt que comme un damier.
	var px := x >> 2
	var py := y >> 2
	var hp := (px * 73856093) ^ (py * 19349663)
	hp = (hp ^ (hp >> 13)) * 1274126177
	var coarse := float(absi(hp) % 1000) / 1000.0
	var hl := ((x * 83492791) ^ (y * 2971215073)) & 0x7FFFFFFF
	var fine := float(hl % 1000) / 1000.0
	return clampf(coarse * 0.78 + fine * 0.22, 0.0, 1.0)


static func _skirt(lp: LowPoly, x0: int, y0: int, w: int, h: int,
		top: float, bottom: float, col: Color) -> void:
	## Jupe verticale sur le pourtour d'un rectangle.
	var side := col.lerp(Color.BLACK, 0.16)
	for i in range(w):
		var x := float(x0 + i)
		lp._quad(Vector3(x, top, float(y0)), Vector3(x + 1.0, top, float(y0)),
				Vector3(x + 1.0, bottom, float(y0)), Vector3(x, bottom, float(y0)), col)
		var yb := float(y0 + h)
		lp._quad(Vector3(x + 1.0, top, yb), Vector3(x, top, yb),
				Vector3(x, bottom, yb), Vector3(x + 1.0, bottom, yb), col)
	for j in range(h):
		var y := float(y0 + j)
		var xr := float(x0 + w)
		lp._quad(Vector3(xr, top, y), Vector3(xr, top, y + 1.0),
				Vector3(xr, bottom, y + 1.0), Vector3(xr, bottom, y), side)
		lp._quad(Vector3(float(x0), top, y + 1.0), Vector3(float(x0), top, y),
				Vector3(float(x0), bottom, y), Vector3(float(x0), bottom, y + 1.0), side)


static func _cliff_rocks(lp: LowPoly, x0: int, y0: int, x1: int, y1: int,
		rng: DetRandom, rock: Color, rock_dark: Color) -> void:
	## Quelques rochers émergeant autour de l'île : casse la ligne droite de la
	## falaise, qui sinon se lit comme une boîte.
	for i in range(26):
		var edge := rng.next_range(0, 4)
		var px := 0.0
		var pz := 0.0
		match edge:
			0: px = float(rng.next_range(x0, x1)); pz = float(y0) + float(rng.next_range(0, 3))
			1: px = float(rng.next_range(x0, x1)); pz = float(y1) - float(rng.next_range(0, 3))
			2: px = float(x0) + float(rng.next_range(0, 3)); pz = float(rng.next_range(y0, y1))
			_: px = float(x1) - float(rng.next_range(0, 3)); pz = float(rng.next_range(y0, y1))
		var r := 0.5 + float(rng.next_range(0, 90)) / 100.0
		var hgt := 0.5 + float(rng.next_range(0, 110)) / 100.0
		lp.add_cylinder(Vector3(px, WATER_LEVEL - 0.3, pz), r, r * 0.55, hgt,
				rng.next_range(5, 8), rock if rng.chance(60) else rock_dark)


static func make_water(w: int, h: int) -> ArrayMesh:
	var lp := LowPoly.new()
	lp.ao_strength = 0.0
	var m := 90.0
	var col: Color = Palette.get_color("water")
	lp._quad(
		Vector3(-m, WATER_LEVEL, -m),
		Vector3(float(w) + m, WATER_LEVEL, -m),
		Vector3(float(w) + m, WATER_LEVEL, float(h) + m),
		Vector3(-m, WATER_LEVEL, float(h) + m), col)
	return lp.build()
