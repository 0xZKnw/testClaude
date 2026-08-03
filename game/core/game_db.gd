extends Node
## Autoload `DB` : les tables de données, chargées une fois au démarrage.

var tables: DataTables


func _ready() -> void:
	tables = DataTables.new()
	if not tables.load_all():
		push_error("Valdris: impossible de charger les données de jeu (res://data).")


func b(id: String) -> Dictionary:
	return tables.buildings.get(id, {})


func u(id: String) -> Dictionary:
	return tables.units.get(id, {})
