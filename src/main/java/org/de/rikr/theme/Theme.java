package org.de.rikr.theme;

import java.awt.*;

public class Theme {
    // Default
    public static Color BACKGROUND_COLOR = new Color(43, 45, 48);
    public static Color CONTENT_BACKGROUND_COLOR = new Color(30, 31, 34);

    public static Color NAVBAR_BUTTON_SELECTED_COLOR = new Color(74, 75, 78);
    public static Color SEARCH_BUTTON_SELECTED_COLOR = new Color(50, 90, 170);

    public static Color CONTENT_SELECTED_HIGHLIGHT_COLOR = new Color(33, 66, 131);
    public static Color SEARCH_HIGHLIGHT_COLOR = new Color(92, 74, 30);

    public static Color CONTENT_TEXT_COLOR = new Color(170, 170, 170);
    public static Color CONTENT_BORDER_COLOR = new Color(50, 50, 50);
    // Secondary text: at least 4.5:1 on the panel and content backgrounds (WCAG 2.2 SC 1.4.3).
    public static Color CONTENT_HIGHLIGHT_TEXT_COLOR = new Color(150, 154, 161);

    public static Color SYNTAX_HIGHLIGHT_DEFAULT_COLOR = new Color(188, 190, 196);
    public static Color SYNTAX_HIGHLIGHT_KEYWORD_COLOR = new Color(207, 142, 109);
    public static Color SYNTAX_HIGHLIGHT_COMMENT_COLOR = new Color(139, 143, 150);
    public static Color SYNTAX_HIGHLIGHT_NUMBER_COLOR = new Color(42, 172, 184);
    public static Color SYNTAX_HIGHLIGHT_STRING_COLOR = new Color(106, 171, 115);
    public static Color SYNTAX_HIGHLIGHT_ANNOTATION_COLOR = new Color(198, 120, 221);

    // Dark mode
    public static Color DARK_BACKGROUND_COLOR = new Color(43, 45, 48);
    public static Color DARK_CONTENT_BACKGROUND_COLOR = new Color(30, 31, 34);

    public static Color DARK_NAVBAR_BUTTON_SELECTED_COLOR = new Color(74, 75, 78);
    public static Color DARK_SEARCH_BUTTON_SELECTED_COLOR = new Color(50, 90, 170);

    public static Color DARK_CONTENT_SELECTED_HIGHLIGHT_COLOR = new Color(33, 66, 131);
    // Amber, as in the light theme: green would read as "matches" where mapping marks differences.
    public static Color DARK_SEARCH_HIGHLIGHT_COLOR = new Color(92, 74, 30);

    public static Color DARK_CONTENT_TEXT_COLOR = new Color(170, 170, 170);
    public static Color DARK_CONTENT_BORDER_COLOR = new Color(50, 50, 50);
    public static Color DARK_CONTENT_HIGHLIGHT_TEXT_COLOR = new Color(150, 154, 161);

    public static Color DARK_SYNTAX_HIGHLIGHT_DEFAULT_COLOR = new Color(188, 190, 196);
    public static Color DARK_SYNTAX_HIGHLIGHT_KEYWORD_COLOR = new Color(207, 142, 109);
    public static Color DARK_SYNTAX_HIGHLIGHT_COMMENT_COLOR = new Color(139, 143, 150);
    public static Color DARK_SYNTAX_HIGHLIGHT_NUMBER_COLOR = new Color(42, 172, 184);
    public static Color DARK_SYNTAX_HIGHLIGHT_STRING_COLOR = new Color(106, 171, 115);
    public static Color DARK_SYNTAX_HIGHLIGHT_ANNOTATION_COLOR = new Color(198, 120, 221);

    // Light mode
    public static Color LIGHT_BACKGROUND_COLOR = new Color(247, 248, 250);
    public static Color LIGHT_CONTENT_BACKGROUND_COLOR = new Color(255, 255, 255);

    public static Color LIGHT_NAVBAR_BUTTON_SELECTED_COLOR = new Color(78, 114, 234);
    public static Color LIGHT_SEARCH_BUTTON_SELECTED_COLOR = new Color(50, 90, 170);

    public static Color LIGHT_CONTENT_SELECTED_HIGHLIGHT_COLOR = new Color(176, 209, 252);
    public static Color LIGHT_SEARCH_HIGHLIGHT_COLOR = new Color(244, 214, 135);

    public static Color LIGHT_CONTENT_TEXT_COLOR = new Color(0, 0, 0);
    public static Color LIGHT_CONTENT_BORDER_COLOR = new Color(230, 230, 230);
    public static Color LIGHT_CONTENT_HIGHLIGHT_TEXT_COLOR = new Color(100, 104, 110);

    public static Color LIGHT_SYNTAX_HIGHLIGHT_DEFAULT_COLOR = new Color(0, 0, 0);
    public static Color LIGHT_SYNTAX_HIGHLIGHT_KEYWORD_COLOR = new Color(52, 75, 182);
    public static Color LIGHT_SYNTAX_HIGHLIGHT_COMMENT_COLOR = new Color(112, 112, 112);
    public static Color LIGHT_SYNTAX_HIGHLIGHT_NUMBER_COLOR = new Color(55, 79, 228);
    // Strings and annotations: at least 4.5:1 on the white content background (WCAG 2.2 SC 1.4.3).
    public static Color LIGHT_SYNTAX_HIGHLIGHT_STRING_COLOR = new Color(0x2E, 0x7D, 0x32);
    public static Color LIGHT_SYNTAX_HIGHLIGHT_ANNOTATION_COLOR = new Color(0x7A, 0x6A, 0x00);

}
