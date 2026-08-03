class_name SaveSystem
extends RefCounted
## Sauvegarde locale versionnée, avec triple écriture et checksum.
##
## Casser les sauvegardes des joueurs en production est le pire incident
## possible sur un jeu de gestion : on ne touche jamais au format sans écrire
## la migration correspondante dans `_migrate()`.

const CURRENT_VERSION := 1
const MAIN_PATH := "user://valdris.save"
const BACKUP_PATH := "user://valdris.save.bak"
const EMERGENCY_PATH := "user://valdris.save.old"


static func save(state: PlayerState) -> bool:
	state.last_seen_unix = int(Time.get_unix_time_from_system())
	state.version = CURRENT_VERSION
	var payload := state.to_dict()
	var body := JSON.stringify(payload)
	var envelope := {
		"version": CURRENT_VERSION,
		"checksum": _checksum(body),
		"body": body,
	}
	var text := JSON.stringify(envelope)

	# Rotation : main -> backup -> emergency, avant d'écrire le nouveau.
	if FileAccess.file_exists(BACKUP_PATH):
		_copy(BACKUP_PATH, EMERGENCY_PATH)
	if FileAccess.file_exists(MAIN_PATH):
		_copy(MAIN_PATH, BACKUP_PATH)
	if not _write(MAIN_PATH, text):
		return false
	# Cas de la toute première sauvegarde : sans ça, une corruption avant la
	# deuxième écriture laisserait le joueur sans aucun filet.
	if not FileAccess.file_exists(BACKUP_PATH):
		_copy(MAIN_PATH, BACKUP_PATH)
	return true


static func load_state() -> PlayerState:
	## Essaie les trois emplacements dans l'ordre. Retourne null si aucun n'est
	## exploitable — l'appelant crée alors une nouvelle partie.
	for path: String in [MAIN_PATH, BACKUP_PATH, EMERGENCY_PATH]:
		var state := _try_load(path)
		if state != null:
			if path != MAIN_PATH:
				push_warning("Valdris: sauvegarde principale illisible, restauration depuis %s" % path)
			return state
	return null


static func has_save() -> bool:
	return FileAccess.file_exists(MAIN_PATH) or FileAccess.file_exists(BACKUP_PATH)


static func wipe() -> void:
	for path: String in [MAIN_PATH, BACKUP_PATH, EMERGENCY_PATH]:
		if FileAccess.file_exists(path):
			DirAccess.remove_absolute(ProjectSettings.globalize_path(path))


static func _try_load(path: String) -> PlayerState:
	if not FileAccess.file_exists(path):
		return null
	var f := FileAccess.open(path, FileAccess.READ)
	if f == null:
		return null
	var text := f.get_as_text()
	f.close()

	var envelope: Variant = JSON.parse_string(text)
	if typeof(envelope) != TYPE_DICTIONARY:
		return null
	var env: Dictionary = envelope
	var body := String(env.get("body", ""))
	if body.is_empty():
		return null
	if int(env.get("checksum", -1)) != _checksum(body):
		push_warning("Valdris: checksum invalide pour %s" % path)
		return null

	var payload: Variant = JSON.parse_string(body)
	if typeof(payload) != TYPE_DICTIONARY:
		return null
	var data: Dictionary = _migrate(payload, int(env.get("version", 1)))
	return PlayerState.from_dict(data)


static func _migrate(data: Dictionary, from_version: int) -> Dictionary:
	## Chaîne de migrations. Chaque étape fait passer d'une version à la suivante.
	var v := from_version
	while v < CURRENT_VERSION:
		match v:
			# Exemple pour la prochaine évolution du format :
			# 1:
			#     data["heroes"] = {}
			_:
				pass
		v += 1
	data["version"] = CURRENT_VERSION
	return data


static func _checksum(s: String) -> int:
	## FNV-1a 32 bits — rapide, stable, suffisant pour détecter une corruption.
	var h := 0x811C9DC5
	for b: int in s.to_utf8_buffer():
		h = (h ^ b) & 0xFFFFFFFF
		h = (h * 0x01000193) & 0xFFFFFFFF
	return h


static func _write(path: String, text: String) -> bool:
	var f := FileAccess.open(path, FileAccess.WRITE)
	if f == null:
		push_error("Valdris: écriture impossible: %s" % path)
		return false
	f.store_string(text)
	f.close()
	return true


static func _copy(from: String, to: String) -> void:
	var f := FileAccess.open(from, FileAccess.READ)
	if f == null:
		return
	var text := f.get_as_text()
	f.close()
	_write(to, text)
