class_name HUD
extends Control
## Bandeau de ressources en haut, barre d'actions en bas, toasts au milieu.
##
## La barre d'actions vit dans le tiers bas : sur une session de plusieurs
## heures, un bouton en haut de l'écran est une douleur physique.

signal build_pressed
signal foreman_pressed
signal army_pressed
signal attack_pressed
signal bastion_pressed
signal hint_pressed(action: String)

var _res_labels: Dictionary = {}
var _res_boxes: Dictionary = {}
var _bastion_label: Label
var _toast_box: VBoxContainer
var _foreman_button: Button
var _hint_button: Button
var _hint_action: String = ""


func _init() -> void:
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_build_top()
	_build_toasts()
	_build_hint()
	_build_bottom()


func _ready() -> void:
	Game.resources_changed.connect(refresh)
	Game.village_changed.connect(refresh)
	Game.army_changed.connect(refresh)
	Game.notice.connect(toast)
	Game.bastion_upgraded.connect(func(_l: int) -> void: refresh())
	refresh()


# ---------------------------------------------------------------- bandeau haut

func _build_top() -> void:
	var top := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.88), 0, 14)
	top.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top.offset_bottom = 132
	top.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(top)

	var col := UIKit.vbox(6)
	top.add_child(col)

	var row := UIKit.hbox(10)
	col.add_child(row)
	for res: String in ["wood", "stone", "iron", "gold", "essence"]:
		var box := UIKit.hbox(6)
		box.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		box.add_child(UIKit.icon_swatch(Palette.resource_color(res), 24))
		var l := UIKit.label("0", UIKit.FONT_SM)
		box.add_child(l)
		_res_labels[res] = l
		_res_boxes[res] = box
		row.add_child(box)

	var sub := UIKit.hbox(10)
	col.add_child(sub)
	_bastion_label = UIKit.label("Bastion 1", UIKit.FONT_SM, Palette.UI_ACCENT)
	_bastion_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sub.add_child(_bastion_label)


func _build_toasts() -> void:
	_toast_box = UIKit.vbox(8)
	_toast_box.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_toast_box.anchor_left = 0.08
	_toast_box.anchor_right = 0.92
	_toast_box.offset_top = 160
	_toast_box.offset_left = 0
	_toast_box.offset_right = 0
	_toast_box.alignment = BoxContainer.ALIGNMENT_BEGIN
	_toast_box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_toast_box)


# -------------------------------------------------------------- barre de conseil

func _build_hint() -> void:
	## « Quoi faire ? » — la garantie qu'on n'est jamais devant un écran mort.
	## Elle est tapable : le conseil mène directement à l'écran concerné.
	_hint_button = UIKit.button("", func() -> void:
		if not _hint_action.is_empty():
			emit_signal("hint_pressed", _hint_action), false, 74)
	_hint_button.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	_hint_button.offset_top = -250
	_hint_button.offset_bottom = -176
	_hint_button.offset_left = 16
	_hint_button.offset_right = -16
	_hint_button.add_theme_font_size_override("font_size", UIKit.FONT_SM)
	_hint_button.clip_text = true
	add_child(_hint_button)


# ---------------------------------------------------------------- barre basse

func _build_bottom() -> void:
	var bar := UIKit.panel(Color(Palette.UI_BG.r, Palette.UI_BG.g, Palette.UI_BG.b, 0.94), 0, 14)
	bar.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	bar.offset_top = -168
	bar.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(bar)

	var row := UIKit.hbox(10)
	bar.add_child(row)

	var defs := [
		{"text": "Bâtir", "sig": "build_pressed", "accent": false},
		{"text": "Contremaître", "sig": "foreman_pressed", "accent": false},
		{"text": "Armée", "sig": "army_pressed", "accent": false},
		{"text": "Attaquer", "sig": "attack_pressed", "accent": true},
		{"text": "Bastion", "sig": "bastion_pressed", "accent": false},
	]
	for d: Dictionary in defs:
		var sig_name := String(d["sig"])
		var b := UIKit.button(String(d["text"]),
				func() -> void: emit_signal(sig_name), bool(d["accent"]), 120)
		b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		b.add_theme_font_size_override("font_size", UIKit.FONT_SM)
		b.clip_text = true
		row.add_child(b)
		if sig_name == "foreman_pressed":
			_foreman_button = b


# ------------------------------------------------------------------ refresh

func refresh() -> void:
	if Game.state == null:
		return
	var caps := Economy.storage_caps(Game.state, Game.tables)
	for res: String in _res_labels.keys():
		var amount := int(Game.state.resources.get(res, 0))
		var l: Label = _res_labels[res]
		if res == "essence":
			l.text = Game.format_number(amount)
			l.add_theme_color_override("font_color", Palette.UI_TEXT)
		else:
			var cap := int(caps.get(res, 0))
			l.text = Game.format_number(amount)
			# Entrepôt plein : on le signale, c'est un appel à dépenser ou à raider.
			var full := cap > 0 and amount >= cap
			l.add_theme_color_override("font_color",
					Palette.UI_ACCENT if full else Palette.UI_TEXT)

	var s := Game.state
	_bastion_label.text = "Bastion %d  ·  ★ %d  ·  Armée %d/%d" % [
		s.bastion_level(), s.total_stars(), Game.army_used(), Game.army_cap()
	]

	if _foreman_button != null:
		var n := Game.foreman_plan().size()
		_foreman_button.text = "Contremaître" if n == 0 else "Contremaître (%d)" % n
		_foreman_button.add_theme_color_override("font_color",
				Palette.UI_ACCENT if n > 0 else Palette.UI_TEXT)

	if _hint_button != null:
		var hint := Advisor.best(s, Game.tables)
		_hint_action = String(hint["action"])
		_hint_button.text = "→  %s" % String(hint["text"])
		_hint_button.disabled = _hint_action.is_empty()


func toast(text: String) -> void:
	var p := UIKit.panel(Palette.UI_PANEL_LIGHT, 12, 14)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var l := UIKit.label(text, UIKit.FONT_SM)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	p.add_child(l)
	_toast_box.add_child(p)
	if _toast_box.get_child_count() > 4:
		_toast_box.get_child(0).queue_free()
	var tw := create_tween()
	tw.tween_interval(2.2)
	tw.tween_property(p, "modulate:a", 0.0, 0.4)
	tw.tween_callback(p.queue_free)
