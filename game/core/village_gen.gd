class_name VillageGen
extends RefCounted
## Génération procédurale des villages ennemis.
##
## Un village = (seed, difficulté). Deux conséquences directes :
##  * 72 niveaux de campagne coûtent ~0 octet et 0 heure de level design ;
##  * un adversaire est reproductible exactement, donc rejouable et validable.
## Les niveaux écrits à la main viendront plus tard, via l'éditeur interne —
## le format de sortie (un Village) est déjà le bon.

const GRID := 34


static func generate(seed_value: int, difficulty: int, tables: DataTables) -> Village:
	var rng := DetRandom.new(seed_value)
	var v := Village.new(GRID, GRID)
	var c := GRID / 2
	var lvl := _level_for(difficulty)

	v.place("bastion", lvl, c - 2, c - 2, 4)

	var ring := _ring_radius(difficulty)
	if difficulty >= 3 and ring > 3:
		_place_wall_ring(v, c, ring, lvl, rng)

	# Défenses : à l'intérieur de l'enceinte, autour du Bastion.
	var defenses := _defense_plan(difficulty, tables)
	for entry: Dictionary in defenses:
		_place_random(v, rng, String(entry["id"]), int(entry["size"]), lvl, c, 2, maxi(3, ring - 1))

	# Production et stockage : ce qui porte le butin.
	var econ_count := 3 + difficulty / 3
	var econ_pool := ["sawmill", "quarry", "house", "warehouse", "mill"]
	if difficulty >= 8:
		econ_pool.append("ironmine")
	for i in range(econ_count):
		var id := String(econ_pool[int(rng.next_u32() % econ_pool.size())])
		var size: int = int(tables.buildings[id]["size"])
		var inside := rng.chance(65)
		var lo := 2 if inside else maxi(3, ring + 1)
		var hi := maxi(3, ring - 1) if inside else (GRID / 2 - 2)
		_place_random(v, rng, id, size, lvl, c, lo, hi)

	# Un peu de décor pour que ça ne ressemble pas à un tableur.
	for i in range(14):
		_place_random(v, rng, "tree", 1, 1, c, ring + 1, GRID / 2 - 1)

	return v


static func _level_for(difficulty: int) -> int:
	return clampi(1 + difficulty / 4, 1, 13)


static func _ring_radius(difficulty: int) -> int:
	return clampi(4 + difficulty / 5, 4, 12)


static func _defense_plan(difficulty: int, tables: DataTables) -> Array[Dictionary]:
	var out: Array[Dictionary] = []
	var towers := clampi(1 + difficulty / 2, 1, 12)
	var cannons := clampi((difficulty - 3) / 3, 0, 8)
	var mortars := clampi((difficulty - 8) / 5, 0, 4)
	var teslas := clampi((difficulty - 14) / 7, 0, 3)
	for i in range(towers):
		out.append({"id": "archer_tower", "size": int(tables.buildings["archer_tower"]["size"])})
	for i in range(cannons):
		out.append({"id": "cannon", "size": int(tables.buildings["cannon"]["size"])})
	for i in range(mortars):
		out.append({"id": "mortar", "size": int(tables.buildings["mortar"]["size"])})
	for i in range(teslas):
		out.append({"id": "tesla", "size": int(tables.buildings["tesla"]["size"])})
	return out


static func _place_wall_ring(v: Village, c: int, r: int, lvl: int, rng: DetRandom) -> void:
	## Enceinte carrée. Quelques brèches aléatoires : un mur parfait est moins
	## intéressant à attaquer qu'un mur avec des points faibles à repérer.
	var gap_chance := 6
	for d in range(-r, r + 1):
		var cells := [
			Vector2i(c + d, c - r), Vector2i(c + d, c + r),
			Vector2i(c - r, c + d), Vector2i(c + r, c + d),
		]
		for p: Vector2i in cells:
			if rng.chance(gap_chance):
				continue
			if v.can_place(p.x, p.y, 1):
				v.place("wall", lvl, p.x, p.y, 1)


static func _place_random(v: Village, rng: DetRandom, id: String, size: int,
		lvl: int, c: int, min_r: int, max_r: int) -> bool:
	if max_r <= min_r:
		max_r = min_r + 1
	for attempt in range(60):
		var dx := rng.next_range(-max_r, max_r + 1)
		var dy := rng.next_range(-max_r, max_r + 1)
		if maxi(absi(dx), absi(dy)) < min_r:
			continue
		var x := c + dx
		var y := c + dy
		if v.can_place(x, y, size):
			v.place(id, lvl, x, y, size)
			return true
	return false


static func loot_pool(difficulty: int) -> Dictionary:
	## Butin disponible dans un village de campagne (taux de pillage 55 %
	## déjà intégré — voir docs/03 §2).
	var base := 260
	var amount := DataTables.grow(base, 1290, difficulty)
	return {
		"wood": amount,
		"stone": (amount * 85) / 100,
		"iron": (amount * 45) / 100 if difficulty >= 5 else 0,
		"gold": (amount * 70) / 100,
		"essence": difficulty / 4 if difficulty >= 8 else 0,
	}


static func power_estimate(village: Village, tables: DataTables) -> int:
	## Puissance approximative d'un village, pour l'affichage et le matchmaking.
	var cp := 0
	for b: Dictionary in village.buildings:
		var d: Dictionary = tables.buildings.get(b["type"], {})
		if d.is_empty() or String(d["category"]) == "deco":
			continue
		cp += tables.building_hp(b["type"], int(b["level"])) / 10
		cp += tables.building_dps(b["type"], int(b["level"])) * 4
	return cp
