class_name Progression
extends RefCounted
## Le Bastion : 40 paliers, et les trois verrous qui remplacent les timers.
##
## 1. verrou économique  -> le coût (croissance 1.34, multiplicateur 2.2)
## 2. verrou de compétence -> les étoiles de campagne + les Épreuves
## 3. verrou structurel  -> rien ne se débloque avant son palier
##
## Aucune de ces conditions n'est une attente. Un joueur qui joue mieux monte
## strictement plus vite, c'est tout le pari du jeu (docs/03 §3).

const MAX_BASTION := 40
const ARMY_HOUSING_BASE := 20
const ARMY_HOUSING_PER_LEVEL := 6


static func max_bastion() -> int:
	return MAX_BASTION


static func bastion_cost(level: int) -> Dictionary:
	## Coût pour passer AU niveau `level` (donc level >= 2).
	if level < 2 or level > MAX_BASTION:
		return {}
	var n := level - 1
	var c := {
		"wood": DataTables.grow(600, DataTables.GROW_COST, n),
		"stone": DataTables.grow(200, DataTables.GROW_COST, n),
		"gold": DataTables.grow(400, DataTables.GROW_COST, n),
	}
	if level >= 5:
		c["iron"] = DataTables.grow(300, DataTables.GROW_COST, level - 4)
	if level >= 10:
		c["essence"] = DataTables.grow(25, 1250, level - 9)
	return c


static func required_stars(level: int) -> int:
	## Étoiles de campagne cumulées exigées. C'est le verrou de compétence :
	## on ne peut pas monter sans avoir réellement gagné des combats.
	return maxi(0, (level - 2) * 4)


static func requires_trial(level: int) -> bool:
	return level % 5 == 0


static func trial_index(level: int) -> int:
	## Index d'Épreuve (1-based) associé au palier de Bastion.
	return level / 5


static func army_housing(bastion_level: int) -> int:
	return ARMY_HOUSING_BASE + (bastion_level - 1) * ARMY_HOUSING_PER_LEVEL


static func blockers(state: PlayerState, tables: DataTables) -> Array[String]:
	## Liste lisible de ce qui manque pour monter d'un palier. Vide = c'est bon.
	var out: Array[String] = []
	var current := state.bastion_level()
	var target := current + 1
	if target > MAX_BASTION:
		out.append("Bastion au niveau maximum.")
		return out

	var cost := bastion_cost(target)
	var missing := Economy.missing_for(state, cost)
	for res: String in missing.keys():
		out.append("Il manque %d %s" % [missing[res], DataTables.RESOURCE_NAMES.get(res, res)])

	var need_stars := required_stars(target)
	if state.total_stars() < need_stars:
		out.append("Il faut %d étoiles de campagne (tu en as %d)" % [need_stars, state.total_stars()])

	if requires_trial(target) and not state.trials_done.has(target):
		out.append("L'Épreuve du Bastion %d n'est pas réussie" % target)

	# Verrou structurel : le village doit avoir grandi, pas seulement le trésor.
	var need_buildings := mini(4 + target, 26)
	var built := state.village.buildings.size() - state.village.count_of("tree")
	if built < need_buildings:
		out.append("Il faut %d bâtiments dans le village (tu en as %d)" % [need_buildings, built])

	# `tables` sert aux règles à venir (prérequis de niveau par catégorie).
	if tables == null:
		out.append("Données de jeu indisponibles")
	return out


static func can_upgrade_bastion(state: PlayerState, tables: DataTables) -> bool:
	return blockers(state, tables).is_empty()


static func upgrade_bastion(state: PlayerState, tables: DataTables) -> bool:
	if not can_upgrade_bastion(state, tables):
		return false
	var target := state.bastion_level() + 1
	if not Economy.pay(state, bastion_cost(target)):
		return false
	state.set_bastion_level(target)
	state.stats["upgrades"] = int(state.stats.get("upgrades", 0)) + 1
	return true


static func newly_unlocked(tables: DataTables, level: int) -> Dictionary:
	## Ce que le palier `level` ouvre — sert à l'écran de célébration.
	var b: Array[String] = []
	for id: String in tables.building_order:
		if int(tables.buildings[id]["unlock"]) == level and id != "bastion":
			b.append(tables.building_name(id))
	var u: Array[String] = []
	for id: String in tables.unit_order:
		if int(tables.units[id]["unlock"]) == level:
			u.append(tables.unit_name(id))
	return {"buildings": b, "units": u}


static func progress_summary(state: PlayerState) -> String:
	return "Bastion %d/%d · %d étoiles · %d niveaux" % [
		state.bastion_level(), MAX_BASTION, state.total_stars(), state.levels_cleared()
	]
