class_name Quests
extends RefCounted
## Fil de quêtes : la colonne vertébrale de la lisibilité du jeu.
##
## Le joueur ne doit jamais se demander « et maintenant ? ». Le Conseiller
## répond à court terme (quelle action, là, tout de suite) ; les quêtes
## répondent à moyen terme (quel objectif, sur les 10 prochaines minutes) et
## font tourner le joueur entre les différents systèmes plutôt que de le
## laisser s'enfermer dans un seul.
##
## Elles se débloquent dans l'ordre : une seule est « active » à la fois, ce qui
## évite le mur de 20 objectifs qui ne veut rien dire.

const LIST := [
	{"id": "build3", "name": "Trois bâtiments", "metric": "buildings", "target": 3,
	 "desc": "Pose trois bâtiments dans ta vallée.", "gold": 150, "essence": 0},
	{"id": "collect5", "name": "Première moisson", "metric": "collected", "target": 5,
	 "desc": "Touche un bâtiment qui affiche un badge pour ramasser sa production.",
	 "gold": 200, "essence": 0},
	{"id": "raid1", "name": "Premier assaut", "metric": "raids_won", "target": 1,
	 "desc": "Entraîne des troupes et remporte un raid.", "gold": 400, "essence": 1},
	{"id": "upgrade5", "name": "Chantier permanent", "metric": "upgrades", "target": 5,
	 "desc": "Améliore cinq fois un bâtiment (le bouton Améliorer le fait d'un coup).",
	 "gold": 500, "essence": 0},
	{"id": "bastion2", "name": "Bastion II", "metric": "bastion", "target": 2,
	 "desc": "Fais monter ton Bastion d'un niveau.", "gold": 600, "essence": 1},
	{"id": "shop1", "name": "Premier investissement", "metric": "shop_levels", "target": 1,
	 "desc": "Achète une amélioration permanente à la boutique.", "gold": 700, "essence": 1},
	{"id": "stars5", "name": "Cinq étoiles", "metric": "stars", "target": 5,
	 "desc": "Accumule cinq étoiles en campagne.", "gold": 900, "essence": 2},
	{"id": "army20", "name": "Une vraie armée", "metric": "army", "target": 20,
	 "desc": "Remplis ton camp jusqu'à 20 places.", "gold": 1100, "essence": 1},
	{"id": "perfect", "name": "Sans bavure", "metric": "best_stars", "target": 3,
	 "desc": "Rase un village entier : trois étoiles sur un seul raid.",
	 "gold": 1500, "essence": 3},
	{"id": "walls10", "name": "Derrière les murs", "metric": "walls", "target": 10,
	 "desc": "Pose dix segments de mur.", "gold": 1800, "essence": 2},
	{"id": "bastion5", "name": "Bastion V", "metric": "bastion", "target": 5,
	 "desc": "Réussis l'Épreuve et atteins le Bastion 5.", "gold": 2500, "essence": 4},
	{"id": "yield5", "name": "Rendement", "metric": "yield_level", "target": 5,
	 "desc": "Monte l'amélioration Rendement au niveau 5.", "gold": 3000, "essence": 3},
	{"id": "levels10", "name": "Dix villages", "metric": "levels", "target": 10,
	 "desc": "Termine dix villages de campagne.", "gold": 4000, "essence": 4},
	{"id": "defenses10", "name": "Place forte", "metric": "defenses", "target": 10,
	 "desc": "Pose dix bâtiments de défense.", "gold": 5000, "essence": 4},
	{"id": "auto1", "name": "Sans les mains", "metric": "auto_level", "target": 1,
	 "desc": "Débloque la récolte automatique à la boutique.", "gold": 6000, "essence": 5},
	{"id": "stars30", "name": "Trente étoiles", "metric": "stars", "target": 30,
	 "desc": "Accumule trente étoiles en campagne.", "gold": 8000, "essence": 6},
	{"id": "bastion10", "name": "Bastion X", "metric": "bastion", "target": 10,
	 "desc": "Atteins le Bastion 10 — la renaissance devient possible.",
	 "gold": 12000, "essence": 8},
	{"id": "earned500k", "name": "Fortune", "metric": "earned", "target": 500000,
	 "desc": "Gagne un demi-million de ressources en tout.", "gold": 16000, "essence": 8},
	{"id": "prestige1", "name": "Renaissance", "metric": "prestiges", "target": 1,
	 "desc": "Renais une première fois pour un bonus définitif.",
	 "gold": 20000, "essence": 12},
	{"id": "bastion15", "name": "Bastion XV", "metric": "bastion", "target": 15,
	 "desc": "Atteins le Bastion 15.", "gold": 30000, "essence": 14},
	{"id": "levels30", "name": "Trente villages", "metric": "levels", "target": 30,
	 "desc": "Termine trente villages de campagne.", "gold": 45000, "essence": 18},
	{"id": "earned5m", "name": "Trésor de guerre", "metric": "earned", "target": 5000000,
	 "desc": "Gagne cinq millions de ressources en tout.", "gold": 70000, "essence": 25},
	{"id": "bastion20", "name": "Bastion XX", "metric": "bastion", "target": 20,
	 "desc": "Atteins le Bastion 20.", "gold": 100000, "essence": 30},
	{"id": "prestige3", "name": "Trois vies", "metric": "prestiges", "target": 3,
	 "desc": "Renais trois fois.", "gold": 150000, "essence": 40},
]


static func metric(state: PlayerState, tables: DataTables, key: String) -> int:
	match key:
		"buildings":
			return state.village.buildings.size() - state.village.count_of("tree")
		"collected": return int(state.stats.get("collected", 0))
		"raids_won": return int(state.stats.get("raids_won", 0))
		"upgrades": return int(state.stats.get("upgrades", 0))
		"bastion": return state.bastion_level()
		"stars": return state.total_stars()
		"levels": return state.levels_cleared()
		"army": return state.army_size(tables)
		"earned": return int(state.stats.get("total_earned", 0))
		"prestiges": return int(state.stats.get("prestiges", 0))
		"walls": return state.village.count_of("wall")
		"yield_level": return Tycoon.level_of(state, "yield")
		"auto_level": return Tycoon.level_of(state, "auto")
		"shop_levels":
			var n := 0
			for v: Variant in state.upgrades.values():
				n += int(v)
			return n
		"best_stars":
			var best := 0
			for v: Variant in state.campaign_stars.values():
				best = maxi(best, int(v))
			return best
		"defenses":
			var n := 0
			for b: Dictionary in state.village.buildings:
				var d: Dictionary = tables.buildings.get(b["type"], {})
				if not d.is_empty() and String(d["category"]) == "defense":
					n += 1
			return n
	return 0


static func index_of(id: String) -> int:
	for i in range(LIST.size()):
		if String(LIST[i]["id"]) == id:
			return i
	return -1


static func active_index(state: PlayerState) -> int:
	## La première quête non réclamée. -1 si tout est terminé.
	for i in range(LIST.size()):
		if not state.quests_claimed.has(String(LIST[i]["id"])):
			return i
	return -1


static func active(state: PlayerState) -> Dictionary:
	var i := active_index(state)
	return {} if i < 0 else LIST[i]


static func progress(state: PlayerState, tables: DataTables, quest: Dictionary) -> Dictionary:
	if quest.is_empty():
		return {"current": 0, "target": 1, "ratio": 1.0, "done": true}
	var target := int(quest["target"])
	var current := mini(metric(state, tables, String(quest["metric"])), target)
	return {
		"current": current, "target": target,
		"ratio": float(current) / float(maxi(1, target)),
		"done": current >= target,
	}


static func can_claim(state: PlayerState, tables: DataTables) -> bool:
	var q := active(state)
	if q.is_empty():
		return false
	return bool(progress(state, tables, q)["done"])


static func claim(state: PlayerState, tables: DataTables) -> Dictionary:
	## Récompense la quête active si elle est terminée. Retourne le gain.
	if not can_claim(state, tables):
		return {}
	var q := active(state)
	state.quests_claimed[String(q["id"])] = 1
	var reward := {}
	if int(q["gold"]) > 0:
		reward["gold"] = int(q["gold"])
	if int(q["essence"]) > 0:
		reward["essence"] = int(q["essence"])
	# L'or de quête ignore le plafond de stockage : une récompense qui
	# s'évapore parce que l'entrepôt est plein est une punition absurde.
	for res: String in reward.keys():
		state.resources[res] = int(state.resources.get(res, 0)) + int(reward[res])
		state.stats["total_earned"] = int(state.stats.get("total_earned", 0)) + int(reward[res])
	return reward


static func completed_count(state: PlayerState) -> int:
	return state.quests_claimed.size()
