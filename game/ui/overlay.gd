class_name Overlay
extends Control
## Base des panneaux modaux : fond assombri, feuille qui monte depuis le bas,
## barre de titre avec fermeture. Tous les panneaux du jeu en héritent pour
## garder un comportement identique (et un seul endroit à corriger).

signal closed

const SHEET_RATIO := 0.72

var body: VBoxContainer
var _sheet: PanelContainer
var _title_label: Label


func _init(title_text: String = "") -> void:
	UIKit.fill_screen(self)
	mouse_filter = Control.MOUSE_FILTER_STOP

	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.55)
	UIKit.fill_screen(dim)
	dim.mouse_filter = Control.MOUSE_FILTER_STOP
	dim.gui_input.connect(func(e: InputEvent) -> void:
		if e is InputEventScreenTouch and not (e as InputEventScreenTouch).pressed:
			close())
	add_child(dim)

	_sheet = UIKit.panel(Palette.UI_PANEL, 26, 22)
	_sheet.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	_sheet.anchor_top = 1.0 - SHEET_RATIO
	_sheet.offset_left = 0
	_sheet.offset_right = 0
	_sheet.offset_top = 0
	_sheet.offset_bottom = 0
	add_child(_sheet)

	var col := UIKit.vbox(16)
	_sheet.add_child(col)

	var header := UIKit.hbox(12)
	_title_label = UIKit.title(title_text)
	_title_label.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(_title_label)
	header.add_child(UIKit.button("✕", close, false, 72))
	col.add_child(header)
	col.add_child(UIKit.separator())

	body = UIKit.vbox(14)
	body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(body)


func set_title(text: String) -> void:
	_title_label.text = text


func set_height_ratio(ratio: float) -> void:
	_sheet.anchor_top = 1.0 - clampf(ratio, 0.2, 0.95)


func open() -> void:
	visible = true
	_sheet.modulate.a = 0.0
	var start := _sheet.position.y + 60.0
	_sheet.position.y = start
	var tw := create_tween()
	tw.set_parallel(true)
	tw.tween_property(_sheet, "modulate:a", 1.0, 0.16)
	tw.tween_property(_sheet, "position:y", start - 60.0, 0.2).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)


func close() -> void:
	visible = false
	emit_signal("closed")
	queue_free()


func add_row(control: Control) -> void:
	body.add_child(control)


func add_scrollable(content: Control) -> void:
	body.add_child(UIKit.scroll(content))
