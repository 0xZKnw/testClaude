class_name RaidHUD
extends Control
## Interface pendant un raid : chrono, destruction, deck d'unités.
##
## Le chrono ne démarre qu'au premier déploiement (docs/05 §1) : observer les
## défenses adverses est gratuit, seule l'exécution est chronométrée.

signal unit_selected(unit_id: String)
signal finish_pressed
signal quit_pressed

var selected_unit: String = ""

var _timer_label: Label
var _percent_label: Label
var _stars_box: HBoxContainer
var _deck: HBoxContainer
var _deck_buttons: Dictionary = {}
var _title: String = ""
var _view: RaidView


func _init(title_text: String, view: RaidView) -> void:
	_title = title_text
	_view = view
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_build_top()
	_build_bottom()


func _build_top() -> void:
	var top := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.88), 0, 14)
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_bottom = 150
	top.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(top)

	var col := UIKit.vbox(6)
	top.add_child(col)

	var row := UIKit.hbox(12)
	col.add_child(row)
	row.add_child(UIKit.button("Quitter", func() -> void: emit_signal("quit_pressed"), false, 64))
	var t := UIKit.label(_title, UIKit.FONT_MD)
	t.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	t.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	row.add_child(t)
	_timer_label = UIKit.label("3:00", UIKit.FONT_MD, Palette.UI_ACCENT)
	row.add_child(_timer_label)

	var row2 := UIKit.hbox(12)
	col.add_child(row2)
	_percent_label = UIKit.label("0 %", UIKit.FONT_LG)
	row2.add_child(_percent_label)
	_stars_box = UIKit.stars_row(0, UIKit.FONT_MD)
	row2.add_child(_stars_box)
	row2.add_child(UIKit.spacer())
	row2.add_child(UIKit.button("Terminer", func() -> void: emit_signal("finish_pressed"), false, 64))


func _build_bottom() -> void:
	var bar := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.94), 0, 12)
	bar.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	bar.offset_top = -190
	bar.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(bar)

	var col := UIKit.vbox(8)
	bar.add_child(col)
	col.add_child(UIKit.label("Choisis une troupe, puis touche le terrain pour la déployer.",
			UIKit.FONT_SM, Palette.UI_TEXT_DIM))

	_deck = UIKit.hbox(10)
	col.add_child(_deck)
	_rebuild_deck()


func _rebuild_deck() -> void:
	for c: Node in _deck.get_children():
		c.queue_free()
	_deck_buttons.clear()
	for id: String in _view.remaining_army.keys():
		var d: Dictionary = Game.tables.units.get(id, {})
		if d.is_empty():
			continue
		var b := UIKit.button("%s\n×%d" % [String(d["name"]), int(_view.remaining_army[id])],
				func() -> void: _select(id), false, 128)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.add_theme_font_size_override("font_size", UIKit.FONT_SM)
		b.clip_text = true
		_deck.add_child(b)
		_deck_buttons[id] = b
	# Sélection par défaut : la première troupe disponible, pour que le joueur
	# puisse déployer immédiatement sans étape supplémentaire.
	if selected_unit.is_empty() or int(_view.remaining_army.get(selected_unit, 0)) <= 0:
		for id: String in _view.remaining_army.keys():
			if int(_view.remaining_army[id]) > 0:
				_select(id)
				break
	else:
		_highlight()


func _select(id: String) -> void:
	selected_unit = id
	emit_signal("unit_selected", id)
	_highlight()


func _highlight() -> void:
	for id: String in _deck_buttons.keys():
		var b: Button = _deck_buttons[id]
		var active := id == selected_unit
		var col := Palette.UI_ACCENT if active else Palette.UI_PANEL_LIGHT
		b.add_theme_stylebox_override("normal", UIKit.stylebox(col, 12, 10))
		b.add_theme_color_override("font_color", Palette.UI_BG if active else Palette.UI_TEXT)


func refresh_counts() -> void:
	for id: String in _deck_buttons.keys():
		var d: Dictionary = Game.tables.units.get(id, {})
		var left := int(_view.remaining_army.get(id, 0))
		var b: Button = _deck_buttons[id]
		b.text = "%s\n×%d" % [String(d["name"]), left]
		b.disabled = left <= 0
	if int(_view.remaining_army.get(selected_unit, 0)) <= 0:
		for id: String in _view.remaining_army.keys():
			if int(_view.remaining_army[id]) > 0:
				_select(id)
				return
		_highlight()


func _process(_delta: float) -> void:
	if _view == null or _view.sim == null:
		return
	var left := _view.time_left()
	_timer_label.text = "%d:%02d" % [int(left) / 60, int(left) % 60]
	_timer_label.add_theme_color_override("font_color",
			Palette.UI_DANGER if left < 20.0 else Palette.UI_ACCENT)
	var pct := _view.percent()
	_percent_label.text = "%d %%" % pct
	var stars := _view.stars()
	for i in range(_stars_box.get_child_count()):
		var l := _stars_box.get_child(i) as Label
		var filled := i < stars
		l.text = "★" if filled else "☆"
		l.add_theme_color_override("font_color",
				Palette.UI_ACCENT if filled else Palette.UI_TEXT_DIM)
