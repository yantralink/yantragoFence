import 'package:flutter/material.dart';

/// Centralized YantraGO corner radii.
///
/// Use these tokens instead of magic numbers. Values are logical pixels.
/// Source: docs/YANTRAGO_Mobile_UI.md section 3.4.
class AppRadii {
  AppRadii._();

  /// Main content card radius.
  static const double card = 16;

  /// Compact metric tile radius.
  static const double metric = 14;

  /// Button and input radius.
  static const double button = 12;

  /// Small chip/badge radius.
  static const double chip = 8;

  static const BorderRadius cardRadius = BorderRadius.all(Radius.circular(card));
  static const BorderRadius metricRadius = BorderRadius.all(Radius.circular(metric));
  static const BorderRadius buttonRadius = BorderRadius.all(Radius.circular(button));
  static const BorderRadius chipRadius = BorderRadius.all(Radius.circular(chip));
}
