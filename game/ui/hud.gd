class_name HUD
extends Control
## Interface du village.
##
## Principes de lisibilité appliqués ici :
##  * chaque ressource affiche sa lettre, sa couleur ET son plafond — un chiffre
##    seul ne dit pas si l'on est bloqué ;
##  * les deux actions les plus fréquentes (récolter, améliorer) sont de gros
##    boutons flottants au pouce, pas des entrées de menu ;
##  * une seule ligne de conseil dit quoi faire ensuite ;
##  * la barre du bas ne contient que des destinations, jamais des actions.

signal build_pressed
signal foreman_pressed
signal army_pressed
signal attack_pressed
signal bastion_pressed
signal shop_pressed
signal collect_pressed
signal hint_pressed(action: String)

var _res_labels: Dictionary = {}
var _res_caps: Dictionary = {}
var _status_label: Label
var _toast_box: VBoxContainer
var _hint_button: Button
var _hint_action: String = ""
var _collect_button: Button
var _foreman_button: Button


func _init() -> void:
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_build_top()
	_build_toasts()
	_build_actions()
	_build_hint()
	_build_bottom()


func _ready() -> void:
	Game.resources_changed.connect(refresh)
	Game.village_changed.connect(refresh)
	Game.army_changed.connect(refresh)
	Game.bastion_upgraded.connect(func(_l: int) -> void: refresh())
	Game.prestiged.connect(func(_p: int) -> void: refresh())
	Game.pending_changed.connect(_refresh_collect)
	Game.notice.connect(toast)
	refresh()


# ---------------------------------------------------------------- bandeau haut

func _build_top() -> void:
	var top := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.9), 0, 12)
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_bottom = 150
	top.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(top)

	var col := UIKit.vbox(8)
	top.add_child(col)

	var row := UIKit.hbox(8)
	col.add_child(row)
	for res: String in ["wood", "stone", "iron", "gold", "essence"]:
		row.add_child(_resource_chip(res))

	_status_label = UIKit.label("", UIKit.FONT_SM, Palette.UI_ACCENT)
	_status_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(_status_label)


func _resource_chip(res: String) -> Control:
	var box := UIKit.vbox(1)
	box.size_flags_horizontal = Control.SIZE_EXPAND_FILL

	var line := UIKit.hbox(5)
	line.alignment = BoxContainer.ALIGNMENT_CENTER
	var badge := PanelContainer.new()
	badge.add_theme_stylebox_override("panel",
			UIKit.stylebox(Palette.resource_color(res), 5, 3))
	var letter := UIKit.label(Palette.resource_icon(res), 19, Color(0.06, 0.07, 0.1))
	badge.add_child(letter)
	line.add_child(badge)

	var amount := UIKit.label("0", UIKit.FONT_SM)
	line.add_child(amount)
	box.add_child(line)

	var cap := UIKit.label("", 17, Palette.UI_TEXT_DIM)
	cap.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	box.add_child(cap)

	_res_labels[res] = amount
	_res_caps[res] = cap
	return box


func _build_toasts() -> void:
	_toast_box = UIKit.vbox(6)
	_toast_box.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_toast_box.offset_top = 250
	_toast_box.offset_left = 60
	_toast_box.offset_right = -60
	_toast_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_toast_box)


# ------------------------------------------------------- actions flottantes

func _build_actions() -> void:
	## Les deux gestes les plus répétés du jeu, à portée de pouce et en gros.
	var col := UIKit.vbox(10)
	col.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	col.offset_right = -18
	col.offset_bottom = -272
	col.offset_left = -430
	col.offset_top = -460
	col.alignment = BoxContainer.ALIGNMENT_END
	add_child(col)

	_collect_button = UIKit.button("Récolter", func() -> void: emit_signal("collect_pressed"),
			true, 120)
	_collect_button.add_theme_font_size_override("font_size", UIKit.FONT_MD)
	_collect_button.visible = false
	col.add_child(_collect_button)

	_foreman_button = UIKit.button("Améliorer", func() -> void: emit_signal("foreman_pressed"),
			false, 104)
	_foreman_button.add_theme_font_size_override("font_size", UIKit.FONT_SM)
	_foreman_button.visible = false
	col.add_child(_foreman_button)


func _build_hint() -> void:
	_hint_button = UIKit.button("", func() -> void:
		if not _hint_action.is_empty():
			emit_signal("hint_pressed", _hint_action), false, 76)
	_hint_button.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	_hint_button.offset_top = -258
	_hint_button.offset_bottom = -182
	_hint_button.offset_left = 16
	_hint_button.offset_right = -16
	_hint_button.add_theme_font_size_override("font_size", UIKit.FONT_SM)
	_hint_button.clip_text = true
	add_child(_hint_button)


func _build_bottom() -> void:
	var bar := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.96), 0, 12)
	bar.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	bar.offset_top = -174
	bar.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(bar)

	var row := UIKit.hbox(8)
	bar.add_child(row)

	var defs := [
		{"text": "Bâtir", "sig": "build_pressed", "accent": false},
		{"text": "Armée", "sig": "army_pressed", "accent": false},
		{"text": "Attaquer", "sig": "attack_pressed", "accent": true},
		{"text": "Bastion", "sig": "bastion_pressed", "accent": false},
		{"text": "Boutique", "sig": "shop_pressed", "accent": false},
	]
	for d: Dictionary in defs:
		var sig_name := String(d["sig"])
		var b := UIKit.button(String(d["text"]),
				func() -> void: emit_signal(sig_name), bool(d["accent"]), 126)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.add_theme_font_size_override("font_size", UIKit.FONT_SM)
		b.clip_text = true
		row.add_child(b)


# ------------------------------------------------------------------ refresh

func refresh() -> void:
	if Game.state == null:
		return
	var s := Game.state
	var caps := Economy.storage_caps(s, Game.tables)

	for res: String in _res_labels.keys():
		var amount := int(s.resources.get(res, 0))
		var l: Label = _res_labels[res]
		var c: Label = _res_caps[res]
		l.text = Game.format_number(amount)
		if res == "essence":
			c.text = ""
			l.add_theme_color_override("font_color", Palette.UI_TEXT)
		else:
			var cap := int(caps.get(res, 0))
			c.text = "/ %s" % Game.format_number(cap)
			var full := cap > 0 and amount >= cap
			# Plein = rouge : c'est une information de gameplay, pas un détail.
			l.add_theme_color_override("font_color",
					Palette.UI_DANGER if full else Palette.UI_TEXT)
			c.add_theme_color_override("font_color",
					Palette.UI_DANGER if full else Palette.UI_TEXT_DIM)

	var mult := Tycoon.production_multiplier(s)
	_status_label.text = "Bastion %d   ·   %d ★   ·   Armée %d/%d   ·   Production ×%.2f" % [
		s.bastion_level(), s.total_stars(), Game.army_used(), Game.army_cap(), mult
	]

	var n := Game.foreman_plan().size()
	_foreman_button.visible = n > 0
	_foreman_button.text = "Améliorer  %d" % n

	var hint := Advisor.best(s, Game.tables)
	_hint_action = String(hint["action"])
	_hint_button.text = "→  %s" % String(hint["text"])
	_hint_button.disabled = _hint_action.is_empty()

	_refresh_collect()


func _refresh_collect() -> void:
	if Game.state == null or _collect_button == null:
		return
	var waiting := Economy.pending_summary(Game.state)
	var total := 0
	for v: Variant in waiting.values():
		total += int(v)
	_collect_button.visible = total > 0
	if total > 0:
		_collect_button.text = "Récolter  %s" % Game.format_number(total)


func toast(text: String) -> void:
	var p := UIKit.panel(Palette.UI_PANEL_LIGHT, 12, 12)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var l := UIKit.label(text, UIKit.FONT_SM)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	p.add_child(l)
	_toast_box.add_child(p)
	if _toast_box.get_child_count() > 3:
		_toast_box.get_child(0).queue_free()
	var tw := create_tween()
	tw.tween_interval(2.4)
	tw.tween_property(p, "modulate:a", 0.0, 0.4)
	tw.tween_callback(p.queue_free)
