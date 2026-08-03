class_name BuildPanel
extends Overlay
## Catalogue de construction.
##
## Tout ce qui est verrouillé reste VISIBLE et grisé, avec la raison écrite :
## voir ce qu'on ne peut pas encore avoir est un moteur de progression, le
## cacher n'en est pas un.

signal chosen(type_id: String)


func _init() -> void:
	super("Bâtir")
	set_height_ratio(0.78)
	_populate()


func _populate() -> void:
	var list := UIKit.vbox(10)
	var tables := Game.tables
	var bastion := Game.state.bastion_level()

	for id: String in tables.building_order:
		if id == "bastion":
			continue
		var d: Dictionary = tables.buildings[id]
		var unlock := int(d["unlock"])
		# On montre le palier courant + les deux suivants : au-delà, c'est du bruit.
		if unlock > bastion + 3:
			continue
		list.add_child(_row(id, d, unlock <= bastion))
	add_scrollable(list)


func _row(id: String, d: Dictionary, unlocked: bool) -> Control:
	var tables := Game.tables
	var card := UIKit.panel(Palette.UI_PANEL_LIGHT, 14, 14)
	var row := UIKit.hbox(14)
	card.add_child(row)

	row.add_child(UIKit.icon_swatch(Palette.get_color(String(d["color"])), 52))

	var info := UIKit.vbox(4)
	info.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var count := Game.state.village.count_of(id)
	var limit := Game.build_limit(id)
	var head := "%s  ·  %d×%d" % [String(d["name"]), int(d["size"]), int(d["size"])]
	info.add_child(UIKit.label(head, UIKit.FONT_MD,
			Palette.UI_TEXT if unlocked else Palette.UI_TEXT_DIM))

	if unlocked:
		info.add_child(UIKit.label("%d / %d posés  ·  %s" % [count, limit, _effect_text(id, d)],
				UIKit.FONT_SM, Palette.UI_TEXT_DIM))
		var cost := tables.building_cost(id, 1)
		info.add_child(UIKit.cost_line(cost, func(res: String, amount: int) -> bool:
			return int(Game.state.resources.get(res, 0)) >= amount))
	else:
		info.add_child(UIKit.label("Bastion %d requis" % int(d["unlock"]),
				UIKit.FONT_SM, Palette.UI_TEXT_DIM))
	row.add_child(info)

	var check := Game.can_build(id)
	var can := unlocked and bool(check["ok"])
	var b := UIKit.button("Poser" if can else "—",
			func() -> void:
				emit_signal("chosen", id)
				close(),
			can, 88)
	b.disabled = not can
	b.custom_minimum_size.x = 180
	if unlocked and not can:
		b.text = String(check["reason"]).left(18)
		b.add_theme_font_size_override("font_size", UIKit.FONT_SM)
	row.add_child(b)
	return card


func _effect_text(id: String, d: Dictionary) -> String:
	var tables := Game.tables
	if not String(d["prod_res"]).is_empty():
		return "+%d %s/min" % [tables.building_production(id, 1),
				DataTables.RESOURCE_NAMES.get(String(d["prod_res"]), "")]
	if not (d["store_res"] as Array).is_empty():
		return "+%d de stockage" % tables.building_storage(id, 1)
	if int(d["base_dps"]) > 0:
		return "%d dégâts/s · portée %.1f" % [tables.building_dps(id, 1), float(d["range"])]
	match String(d["category"]):
		"military": return "débloque des troupes"
		"special": return "bâtiment spécial"
		"wall": return "ralentit les attaquants"
		"deco": return "décor"
	return ""
