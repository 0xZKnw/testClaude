class_name Economy
extends RefCounted
## Production, stockage, coûts.
##
## Point de design important (docs/03) : la croissance des coûts (1.34) est
## supérieure à celle de la production (1.22). Le passif décroche donc
## volontairement au fil des niveaux, ce qui force le retour au raid. Sans ça,
## un jeu sans timers devient un idle game qui se joue tout seul.

const RESERVE_MAX_HOURS := 24     # Réserve du Régent : au-delà, plus rien ne s'accumule
const OFFLINE_DECAY_AFTER_H := 6  # rendement dégressif au-delà de 6 h hors-ligne


static func storage_caps(state: PlayerState, tables: DataTables) -> Dictionary:
	var caps := {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 999999999}
	for b: Dictionary in state.village.buildings:
		var d: Dictionary = tables.buildings.get(b["type"], {})
		if d.is_empty():
			continue
		var store_res: Array = d["store_res"]
		if store_res.is_empty():
			continue
		var amount := tables.building_storage(b["type"], int(b["level"]))
		for res: String in store_res:
			caps[res] = int(caps.get(res, 0)) + amount
	return caps


static func production_per_minute(state: PlayerState, tables: DataTables) -> Dictionary:
	var prod := {"wood": 0, "stone": 0, "iron": 0, "gold": 0, "essence": 0}
	for b: Dictionary in state.village.buildings:
		var d: Dictionary = tables.buildings.get(b["type"], {})
		if d.is_empty() or String(d["prod_res"]).is_empty():
			continue
		var res: String = d["prod_res"]
		var rate := tables.building_production(b["type"], int(b["level"]))
		prod[res] = int(prod.get(res, 0)) + int(rate * _adjacency_bonus(state, b) / 100)
	return prod


static func _adjacency_bonus(state: PlayerState, b: Dictionary) -> int:
	## Bonus d'adjacence, en pourcentage (100 = neutre). Voir docs/04 §3.
	## Version réduite : les règles complètes arrivent avec l'overlay d'adjacence.
	var bonus := 100
	var t: String = b["type"]
	var neighbours := _neighbours_of(state, b)
	var counts := {}
	for n: Dictionary in neighbours:
		counts[n["type"]] = int(counts.get(n["type"], 0)) + 1

	match t:
		"sawmill":
			bonus += 5 * int(counts.get("tree", 0))          # scierie près des arbres
		"quarry":
			bonus += 8 * int(counts.get("quarry", 0))
		"house":
			bonus += 10 * int(counts.get("mill", 0))
			bonus -= 15 * int(counts.get("ironmine", 0))     # personne n'aime le bruit
		"mill":
			bonus += 6 * int(counts.get("sawmill", 0))
		"ironmine":
			bonus += 5 * int(counts.get("quarry", 0))
	if counts.has("statue"):
		bonus += 5 * int(counts["statue"])
	return clampi(bonus, 50, 220)


static func _neighbours_of(state: PlayerState, b: Dictionary) -> Array[Dictionary]:
	## Bâtiments occupant une case adjacente au périmètre de `b`.
	var out: Array[Dictionary] = []
	var seen := {}
	var x0: int = b["x"]
	var y0: int = b["y"]
	var s: int = b["size"]
	for i in range(-1, s + 1):
		var probes := [
			Vector2i(x0 + i, y0 - 1), Vector2i(x0 + i, y0 + s),
			Vector2i(x0 - 1, y0 + i), Vector2i(x0 + s, y0 + i),
		]
		for p: Vector2i in probes:
			var other := state.village.building_at(p.x, p.y)
			if other.is_empty() or other["uid"] == b["uid"] or seen.has(other["uid"]):
				continue
			seen[other["uid"]] = true
			out.append(other)
	return out


static func can_afford(state: PlayerState, cost: Dictionary) -> bool:
	for res: String in cost.keys():
		if int(state.resources.get(res, 0)) < int(cost[res]):
			return false
	return true


static func missing_for(state: PlayerState, cost: Dictionary) -> Dictionary:
	var miss := {}
	for res: String in cost.keys():
		var lack := int(cost[res]) - int(state.resources.get(res, 0))
		if lack > 0:
			miss[res] = lack
	return miss


static func pay(state: PlayerState, cost: Dictionary) -> bool:
	if not can_afford(state, cost):
		return false
	for res: String in cost.keys():
		state.resources[res] = int(state.resources.get(res, 0)) - int(cost[res])
	return true


static func grant(state: PlayerState, tables: DataTables, gains: Dictionary) -> Dictionary:
	## Ajoute des ressources en respectant les plafonds. Retourne ce qui a
	## réellement été crédité (pour l'affichage honnête du butin).
	var caps := storage_caps(state, tables)
	var actual := {}
	for res: String in gains.keys():
		var amount := int(gains[res])
		if amount <= 0:
			continue
		var cap := int(caps.get(res, 0))
		var cur := int(state.resources.get(res, 0))
		var added: int = amount if res == "essence" else mini(amount, maxi(0, cap - cur))
		if added > 0:
			state.resources[res] = cur + added
			actual[res] = added
	return actual


static func tick(state: PlayerState, tables: DataTables, delta_seconds: float) -> Dictionary:
	## Production continue. `delta_seconds` peut être un pas de jeu (0.25 s) ou
	## une durée hors-ligne (jusqu'à 24 h). Retourne ce qui a été crédité.
	if delta_seconds <= 0.0:
		return {}
	var seconds := minf(delta_seconds, float(RESERVE_MAX_HOURS * 3600))
	var efficiency := 1.0
	var decay_start := float(OFFLINE_DECAY_AFTER_H * 3600)
	if seconds > decay_start:
		# Au-delà de 6 h : rendement dégressif, mais jamais nul.
		var extra := seconds - decay_start
		seconds = decay_start + extra * 0.35
	var prod := production_per_minute(state, tables)
	var gains := {}
	for res: String in prod.keys():
		var per_sec := float(prod[res]) / 60.0 * efficiency
		var raw := per_sec * seconds + float(state.accum.get(res, 0.0))
		var whole := int(floor(raw))
		state.accum[res] = raw - float(whole)
		if whole > 0:
			gains[res] = whole
	return grant(state, tables, gains)


static func upgrade_cost(tables: DataTables, type_id: String, current_level: int) -> Dictionary:
	return tables.building_cost(type_id, current_level + 1)


static func sell_refund(tables: DataTables, type_id: String, level: int) -> Dictionary:
	## 40 % du cumul investi. Déplacer est gratuit ; vendre coûte un peu.
	var refund := {}
	for lvl in range(1, level + 1):
		var c := tables.building_cost(type_id, lvl)
		for res: String in c.keys():
			refund[res] = int(refund.get(res, 0)) + int(c[res])
	for res: String in refund.keys():
		refund[res] = int(refund[res] * 40 / 100)
	return refund
