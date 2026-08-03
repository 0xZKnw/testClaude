class_name Campaign
extends RefCounted
## Les 72 villages de campagne et les 8 Épreuves du Bastion.

const CHAPTERS := [
	{"name": "La Vallée", "levels": 6, "bastion": 1},
	{"name": "Les Bois", "levels": 8, "bastion": 5},
	{"name": "La Rivière", "levels": 8, "bastion": 10},
	{"name": "Les Mines", "levels": 9, "bastion": 15},
	{"name": "Les Cimes", "levels": 9, "bastion": 20},
	{"name": "La Faille", "levels": 10, "bastion": 25},
	{"name": "La Citadelle", "levels": 10, "bastion": 30},
	{"name": "Le Trône", "levels": 12, "bastion": 35},
]

const SITE_NAMES := [
	"Camp de bandits", "Hameau brûlé", "Poste avancé", "Moulin abandonné",
	"Gué de pierre", "Clairière noire", "Carrière hantée", "Vieux pont",
	"Ferme fortifiée", "Tour de guet", "Halte des pillards", "Colline creuse",
	"Marais aux corbeaux", "Passe étroite", "Enclos de fer", "Bois-le-Roi",
	"Combe grise", "Cairn brisé", "Forge éteinte", "Chemin des loups",
	"Val profond", "Roc fendu", "Sentinelle", "Refuge du Sud",
]

const TRIAL_NAMES := [
	"Le Siège", "Le Défilé", "À l'aveugle", "Le Miroir",
	"Sans renfort", "La Longue Nuit", "Un seul héros", "Le Régent",
]

const TRIAL_RULES := [
	"Seulement 10 places d'armée.", "Seulement 14 places d'armée.",
	"Seulement 18 places d'armée.", "Seulement 22 places d'armée.",
	"Seulement 26 places d'armée.", "Seulement 30 places d'armée.",
	"Seulement 34 places d'armée.", "Seulement 38 places d'armée.",
]


static func level_count() -> int:
	var n := 0
	for c: Dictionary in CHAPTERS:
		n += int(c["levels"])
	return n


static func chapter_of(index: int) -> int:
	var acc := 0
	for i in range(CHAPTERS.size()):
		acc += int(CHAPTERS[i]["levels"])
		if index < acc:
			return i
	return CHAPTERS.size() - 1


static func chapter_first_index(chapter: int) -> int:
	var acc := 0
	for i in range(mini(chapter, CHAPTERS.size())):
		acc += int(CHAPTERS[i]["levels"])
	return acc


static func level_info(index: int) -> Dictionary:
	var total := level_count()
	var idx := clampi(index, 0, total - 1)
	var chapter := chapter_of(idx)
	var difficulty := 1 + idx    # 1..72, la difficulté suit l'ordre des niveaux
	return {
		"index": idx,
		"name": String(SITE_NAMES[idx % SITE_NAMES.size()]),
		"chapter": chapter,
		"chapter_name": String(CHAPTERS[chapter]["name"]),
		"difficulty": difficulty,
		"seed": 100000 + idx * 7919,
		"bastion_req": int(CHAPTERS[chapter]["bastion"]),
		"loot": VillageGen.loot_pool(difficulty),
		"is_trial": false,
	}


static func generate_level(index: int, tables: DataTables) -> Village:
	var info := level_info(index)
	return VillageGen.generate(int(info["seed"]), int(info["difficulty"]), tables)


static func is_unlocked(state: PlayerState, index: int) -> bool:
	## Un niveau s'ouvre si le précédent a au moins une étoile ET si le Bastion
	## a le niveau de chapitre requis.
	var info := level_info(index)
	if state.bastion_level() < int(info["bastion_req"]):
		return false
	if index == 0:
		return true
	return state.stars_for(index - 1) > 0


# ------------------------------------------------------------------ Épreuves

static func trial_count() -> int:
	return TRIAL_NAMES.size()


static func trial_info(trial_index: int) -> Dictionary:
	## trial_index est 1-based (Épreuve 1 -> Bastion 5, Épreuve 2 -> Bastion 10...).
	var t := clampi(trial_index, 1, trial_count())
	var bastion := t * 5
	return {
		"trial_index": t,
		"name": String(TRIAL_NAMES[t - 1]),
		"rule": String(TRIAL_RULES[t - 1]),
		"bastion_level": bastion,
		"difficulty": clampi(bastion + 3, 1, 72),
		"seed": 500000 + t * 13337,
		"army_cap": 6 + t * 4,
		"loot": {},
		"is_trial": true,
	}


static func generate_trial(trial_index: int, tables: DataTables) -> Village:
	var info := trial_info(trial_index)
	return VillageGen.generate(int(info["seed"]), int(info["difficulty"]), tables)


static func pending_trial(state: PlayerState) -> int:
	## Épreuve à réussir pour le prochain palier, ou 0 s'il n'y en a pas.
	var target := state.bastion_level() + 1
	if target > Progression.MAX_BASTION:
		return 0
	if not Progression.requires_trial(target):
		return 0
	if state.trials_done.has(target):
		return 0
	return Progression.trial_index(target)
