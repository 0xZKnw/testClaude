class_name QuestBar
extends Control
## Bandeau de quête active : objectif, jauge, récompense.
##
## Il est toujours visible, jamais modal, et devient un bouton dès que la quête
## est terminée. C'est la réponse permanente à « je fais quoi ? » à l'échelle
## de la dizaine de minutes.

signal claim_pressed
signal details_pressed

const BAR_H := 78.0

var _title: Label
var _progress: ProgressBar
var _count: Label
var _button: Button
var _panel: PanelContainer


func _init() -> void:
	set_anchors_preset(Control.PRESET_TOP_WIDE)
	offset_top = 158
	offset_bottom = 158 + BAR_H
	offset_left = 12
	offset_right = -12
	mouse_filter = Control.MOUSE_FILTER_IGNORE

	_panel = UIKit.panel(Palette.UI_PANEL, 14, 10)
	UIKit.fill_screen(_panel)
	_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_panel)

	var row := UIKit.hbox(10)
	_panel.add_child(row)

	var col := UIKit.vbox(4)
	col.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	col.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	_title = UIKit.label("", UIKit.FONT_SM)
	_title.clip_text = true
	col.add_child(_title)

	var gauge := UIKit.hbox(8)
	_progress = ProgressBar.new()
	_progress.show_percentage = false
	_progress.custom_minimum_size = Vector2(0, 12)
	_progress.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_progress.add_theme_stylebox_override("background", UIKit.stylebox(Palette.UI_BG, 6, 0))
	_progress.add_theme_stylebox_override("fill", UIKit.stylebox(Palette.UI_ACCENT, 6, 0))
	gauge.add_child(_progress)
	_count = UIKit.label("", 20, Palette.UI_TEXT_DIM)
	gauge.add_child(_count)
	col.add_child(gauge)
	row.add_child(col)

	_button = UIKit.button("Détails", func() -> void:
		if _button.text.begins_with("Récupérer"):
			emit_signal("claim_pressed")
		else:
			emit_signal("details_pressed"), false, 62)
	_button.custom_minimum_size.x = 210
	_button.add_theme_font_size_override("font_size", 22)
	row.add_child(_button)


func _ready() -> void:
	Game.resources_changed.connect(refresh)
	Game.village_changed.connect(refresh)
	Game.army_changed.connect(refresh)
	Game.quest_changed.connect(refresh)
	Game.prestiged.connect(func(_p: int) -> void: refresh())
	refresh()


func refresh() -> void:
	if Game.state == null:
		return
	var q := Quests.active(Game.state)
	if q.is_empty():
		_title.text = "Toutes les quêtes sont terminées."
		_progress.value = 100
		_count.text = ""
		_button.text = "Détails"
		return

	var p := Quests.progress(Game.state, Game.tables, q)
	_title.text = "%s — %s" % [String(q["name"]), String(q["desc"])]
	_progress.value = float(p["ratio"]) * 100.0
	_count.text = "%s/%s" % [Game.format_number(int(p["current"])),
			Game.format_number(int(p["target"]))]

	if bool(p["done"]):
		_button.text = "Récupérer"
		_button.add_theme_stylebox_override("normal", UIKit.stylebox(Palette.UI_OK, 12, 10))
		_button.add_theme_color_override("font_color", Palette.UI_BG)
		_progress.add_theme_stylebox_override("fill", UIKit.stylebox(Palette.UI_OK, 6, 0))
	else:
		_button.text = "Détails"
		_button.add_theme_stylebox_override("normal",
				UIKit.stylebox(Palette.UI_PANEL_LIGHT, 12, 10))
		_button.add_theme_color_override("font_color", Palette.UI_TEXT)
		_progress.add_theme_stylebox_override("fill", UIKit.stylebox(Palette.UI_ACCENT, 6, 0))
