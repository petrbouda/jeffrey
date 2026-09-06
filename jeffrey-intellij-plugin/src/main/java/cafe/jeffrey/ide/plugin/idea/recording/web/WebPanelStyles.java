/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.ide.plugin.idea.recording.web;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.Color;
import java.awt.Font;

/**
 * The panel's stylesheet, built from the IDE's current theme.
 *
 * <p>Unlike the Swing sheet this one has real custom properties, so the theme appears exactly once —
 * in a {@code :root} block — and every rule below is written against it. A look-and-feel change is
 * therefore a re-render with a different {@code :root}, not a hunt for baked hex.
 *
 * <p>Scaling goes the same way. IntelliJ's HiDPI factor is not something CSS can read, so
 * {@link JBUI#scale(int)} is asked once for a reference size and the ratio becomes {@code --u}; every
 * dimension below is a multiple of it. That keeps a 150% display from rendering a panel drawn for 96
 * DPI inside a browser that knows nothing about the IDE's idea of a pixel.
 *
 * <p>{@code --u} carries {@code px} rather than being a bare ratio, and that is load-bearing:
 * {@code calc(16*1.0)} is a number, not a length, so a unitless {@code --u} makes every dimension in
 * the sheet invalid and CSS drops invalid declarations without a word. The result is not a slightly
 * wrong panel but an unstyled one.
 *
 * <p>The flame is the one colour not taken from the theme. It is Jeffrey's, it has to mean the same
 * thing in both themes, and it is the only place the panel raises its voice — so it is declared per
 * theme rather than derived, with the dark variant lifted for contrast against a dark ground.
 */
final class WebPanelStyles {

    /** Reference size the HiDPI ratio is measured against. */
    private static final int SCALE_BASIS = 100;

    private static final String FLAME_LIGHT = "#D2551E";
    private static final String FLAME_LIGHT_HOVER = "#B94716";
    private static final String FLAME_DARK = "#E8703A";
    private static final String FLAME_DARK_HOVER = "#D2551E";

    private WebPanelStyles() {
    }

    static String sheet() {
        return ":root{" + tokens() + "}" + RULES;
    }

    private static String tokens() {
        boolean dark = !JBColor.isBright();
        Font font = UIUtil.getLabelFont();
        double unit = JBUI.scale(SCALE_BASIS) / (double) SCALE_BASIS;

        return "--u:" + unit + "px;"
                + "--font:" + cssFontStack(font) + ";"
                + "--mono:" + cssMonoStack() + ";"
                + "--bg:" + hex(UIUtil.getPanelBackground()) + ";"
                + "--panel:" + hex(panelSurface(dark)) + ";"
                + "--border:" + hex(JBColor.border()) + ";"
                + "--text:" + hex(UIUtil.getLabelForeground()) + ";"
                + "--sec:" + hex(UIUtil.getContextHelpForeground()) + ";"
                + "--dim:" + hex(UIUtil.getLabelDisabledForeground()) + ";"
                + "--hover:" + hex(UIUtil.getListSelectionBackground(false)) + ";"
                + "--well:" + hex(panelSurface(dark)) + ";"
                + "--link:" + hex(JBColor.namedColor("Link.activeForeground", JBColor.BLUE)) + ";"
                + "--err:" + hex(JBColor.namedColor("Label.errorForeground", JBColor.RED)) + ";"
                + "--btn-bg:" + hex(JBColor.namedColor("Button.startBackground", UIUtil.getPanelBackground())) + ";"
                + "--btn-br:" + hex(JBColor.namedColor("Button.startBorderColor", JBColor.border())) + ";"
                + "--focus:" + hex(JBColor.namedColor("Component.focusColor", JBColor.BLUE)) + ";"
                + "--fl:" + (dark ? FLAME_DARK : FLAME_LIGHT) + ";"
                + "--fl-hover:" + (dark ? FLAME_DARK_HOVER : FLAME_LIGHT_HOVER) + ";"
                + "--fl-soft:" + (dark ? "rgba(232,112,58,.16)" : "rgba(232,112,58,.10)") + ";"
                + "--fl-line:" + (dark ? "rgba(232,112,58,.34)" : "rgba(210,85,30,.30)") + ";"
                + "--shadow:" + (dark
                        ? "0 8px 24px rgba(0,0,0,.5),0 1px 3px rgba(0,0,0,.4)"
                        : "0 6px 20px rgba(0,0,0,.14),0 1px 3px rgba(0,0,0,.08)") + ";";
    }

    /**
     * A surface one step off the panel background, for figure tiles and icon wells.
     *
     * <p>Derived rather than named because no platform key means "slightly raised" in both themes:
     * light themes want a touch darker, dark themes a touch lighter, and the same key would give one
     * of them the wrong direction.
     */
    private static Color panelSurface(boolean dark) {
        Color base = UIUtil.getPanelBackground();
        return dark ? ColorUtil.brighter(base, 1) : ColorUtil.darker(base, 1);
    }

    private static String cssFontStack(Font font) {
        return "'" + font.getFamily() + "',system-ui,-apple-system,'Segoe UI',sans-serif";
    }

    private static String cssMonoStack() {
        return "'JetBrains Mono',ui-monospace,'SF Mono',Menlo,Consolas,monospace";
    }

    private static String hex(Color color) {
        return "#" + ColorUtil.toHex(color);
    }

    /**
     * Everything that is not the theme. A text block rather than concatenation, because this is a
     * stylesheet and should be readable as one.
     */
    private static final String RULES = """
            *{box-sizing:border-box}
            html,body{margin:0;padding:0}
            body{
              background:var(--bg); color:var(--text); font-family:var(--font);
              font-size:calc(13*var(--u)); line-height:1.5;
              -webkit-user-select:none; user-select:none; overflow-x:hidden;
            }
            ::-webkit-scrollbar{width:calc(10*var(--u));height:calc(10*var(--u))}
            ::-webkit-scrollbar-thumb{background:var(--border);border-radius:calc(5*var(--u))}
            ::-webkit-scrollbar-track{background:transparent}
            :focus-visible{outline:calc(2*var(--u)) solid var(--focus); outline-offset:calc(1*var(--u))}

            .accent{height:calc(3*var(--u));background:linear-gradient(90deg,#E8703A,#D2451E 55%,#8E2F14)}
            .accent.mute{background:var(--border)}

            .hdr{display:flex;align-items:center;gap:calc(12*var(--u));padding:calc(20*var(--u)) calc(24*var(--u)) 0}
            .hdr .well{
              width:calc(36*var(--u));height:calc(36*var(--u));border-radius:calc(9*var(--u));flex:none;
              background:var(--fl-soft);color:var(--fl);display:flex;align-items:center;justify-content:center;
            }
            .hdr .well.mute{background:var(--well);color:var(--dim)}
            .hdr .title{font-size:calc(14.5*var(--u));font-weight:600;letter-spacing:-.01em}
            .hdr .sub{font-size:calc(12*var(--u));color:var(--sec);margin-top:calc(3*var(--u))}
            .hdr .actions{margin-left:auto;display:flex;gap:calc(8*var(--u));align-items:flex-start}

            .fname{font-family:var(--mono);font-size:calc(14*var(--u));letter-spacing:-.01em;
                   -webkit-user-select:text;user-select:text;word-break:break-all}
            .tnum{font-variant-numeric:tabular-nums}

            .btn{
              font-family:var(--font);font-size:calc(13*var(--u));font-weight:500;line-height:1;
              height:calc(28*var(--u));padding:0 calc(14*var(--u));border-radius:calc(5*var(--u));
              border:calc(1*var(--u)) solid var(--btn-br);background:var(--btn-bg);color:var(--text);
              cursor:pointer;white-space:nowrap;display:inline-flex;align-items:center;gap:calc(7*var(--u));
            }
            .btn:hover{background:var(--hover)}
            .btn.flame{background:var(--fl);border-color:var(--fl);color:#fff}
            .btn.flame:hover{background:var(--fl-hover)}
            .btn[disabled]{color:var(--dim);border-color:var(--border);cursor:default;background:var(--btn-bg)}
            .btn[disabled]:hover{background:var(--btn-bg)}

            .split{display:inline-flex}
            .split .main{border-radius:calc(5*var(--u)) 0 0 calc(5*var(--u));border-right-width:0}
            .split .arrow{border-radius:0 calc(5*var(--u)) calc(5*var(--u)) 0;padding:0 calc(8*var(--u))}
            .split .arrow[aria-expanded="true"]{background:var(--hover)}

            .anchor{position:relative;display:inline-flex}
            .pop{
              position:absolute;top:calc(100% + 5*var(--u));right:0;min-width:calc(252*var(--u));z-index:20;
              background:var(--bg);border:calc(1*var(--u)) solid var(--border);border-radius:calc(7*var(--u));
              box-shadow:var(--shadow);padding:calc(5*var(--u));text-align:left;
            }
            .pop[hidden]{display:none}
            .pop .grp{font-size:calc(10.5*var(--u));font-weight:600;letter-spacing:.07em;text-transform:uppercase;
                      color:var(--dim);padding:calc(7*var(--u)) calc(9*var(--u)) calc(4*var(--u))}
            .pop .divider{height:calc(1*var(--u));background:var(--border);margin:calc(5*var(--u)) 0}
            .mi{display:flex;align-items:center;gap:calc(9*var(--u));padding:calc(6*var(--u)) calc(9*var(--u));
                border-radius:calc(5*var(--u));font-size:calc(13*var(--u));white-space:nowrap;
                width:100%;border:0;background:transparent;color:var(--text);font-family:var(--font);
                cursor:pointer;text-align:left}
            .mi:hover{background:var(--hover)}
            .mi .n{font-weight:500}
            .mi .tail{margin-left:auto;font-size:calc(11*var(--u));color:var(--dim);font-family:var(--mono)}
            .mi.dis{color:var(--dim);cursor:default}
            .mi.dis .n{font-weight:400}
            .mi.dis:hover{background:transparent}
            .pop .foot{padding:calc(8*var(--u)) calc(9*var(--u)) calc(4*var(--u));
                       border-top:calc(1*var(--u)) solid var(--border);margin-top:calc(5*var(--u));
                       font-size:calc(11.5*var(--u));color:var(--sec);white-space:normal;
                       max-width:calc(252*var(--u))}

            .mark{
              width:calc(19*var(--u));height:calc(19*var(--u));border-radius:calc(5*var(--u));flex:none;
              display:flex;align-items:center;justify-content:center;font-family:var(--mono);
              font-size:calc(9.5*var(--u));font-weight:500;background:var(--fl-soft);color:var(--fl);
            }
            .mark.grey{background:var(--well);color:var(--dim)}

            .figs{display:grid;grid-template-columns:repeat(4,1fr);gap:calc(10*var(--u));
                  padding:calc(20*var(--u)) calc(24*var(--u)) 0}
            .fig{background:var(--panel);border:calc(1*var(--u)) solid var(--border);
                 border-radius:calc(8*var(--u));padding:calc(12*var(--u)) calc(14*var(--u))}
            .fig .v{font-size:calc(21*var(--u));font-weight:500;letter-spacing:-.02em}
            .fig .k{font-size:calc(11.5*var(--u));color:var(--sec);margin-top:calc(2*var(--u))}
            .fig.bad .v{color:var(--err)}
            .meter{height:calc(4*var(--u));border-radius:calc(2*var(--u));background:var(--border);
                   margin-top:calc(9*var(--u));overflow:hidden}
            .meter i{display:block;height:100%;border-radius:calc(2*var(--u));background:var(--fl)}

            .body{padding:calc(24*var(--u)) calc(24*var(--u)) calc(26*var(--u))}
            .rule{height:calc(1*var(--u));background:var(--border);margin:calc(24*var(--u)) 0}
            .sect{font-size:calc(11*var(--u));font-weight:600;letter-spacing:.07em;
                  text-transform:uppercase;color:var(--sec)}
            .aa-head{display:flex;align-items:baseline;gap:calc(9*var(--u));margin-bottom:calc(12*var(--u))}
            .aa-head .cnt{font-size:calc(11.5*var(--u));color:var(--dim)}

            .find{display:flex;gap:calc(11*var(--u));align-items:flex-start;
                  padding:calc(8*var(--u)) calc(10*var(--u));margin:0 calc(-10*var(--u));
                  border-radius:calc(6*var(--u))}
            .find:hover{background:var(--hover)}
            .find .ico{color:var(--fl);margin-top:calc(3*var(--u))}
            .find .rn{font-size:calc(13.5*var(--u));font-weight:600;letter-spacing:-.005em}
            .find .sm{font-size:calc(12.5*var(--u));color:var(--sec);margin-top:calc(1*var(--u));line-height:1.5}

            .link{font-size:calc(13*var(--u));color:var(--link);cursor:pointer;background:none;border:0;
                  padding:0;font-family:var(--font);text-align:left}
            .link:hover{text-decoration:underline}
            .aa-more{margin-top:calc(12*var(--u));display:inline-block}
            .aa-none{font-size:calc(13*var(--u));color:var(--sec)}

            .views{display:grid;grid-template-columns:repeat(3,1fr);gap:calc(10*var(--u));
                   margin-top:calc(12*var(--u))}
            .card{display:flex;gap:calc(11*var(--u));border:calc(1*var(--u)) solid var(--border);
                  border-radius:calc(8*var(--u));padding:calc(12*var(--u)) calc(13*var(--u));
                  background:var(--bg);align-items:flex-start;cursor:pointer;text-align:left;
                  font-family:var(--font);color:var(--text);width:100%}
            .card:hover{background:var(--hover);border-color:var(--fl-line)}
            .card .iw{width:calc(28*var(--u));height:calc(28*var(--u));border-radius:calc(7*var(--u));
                      flex:none;background:var(--fl-soft);color:var(--fl);display:flex;
                      align-items:center;justify-content:center}
            .card .l{font-size:calc(13.5*var(--u));font-weight:500}
            .card .b{font-size:calc(12*var(--u));color:var(--sec);margin-top:calc(2*var(--u))}
            .card.off{border-style:dashed;cursor:default}
            .card.off .l,.card.off .b{color:var(--dim)}
            .card.off .iw{background:var(--well);color:var(--dim)}
            .card.off:hover{border-color:var(--border);background:var(--bg)}

            .note{font-size:calc(13*var(--u));color:var(--sec);margin:0 0 calc(18*var(--u));max-width:72ch}
            .note.last{margin-bottom:0}
            .facts{display:grid;grid-template-columns:auto 1fr;gap:calc(7*var(--u)) calc(20*var(--u));
                   font-size:calc(13*var(--u));max-width:calc(760*var(--u));margin:0}
            .facts dt{color:var(--sec)}
            .facts dd{margin:0;font-family:var(--mono);font-size:calc(12.5*var(--u));word-break:break-all;
                      -webkit-user-select:text;user-select:text}

            .prog{height:calc(4*var(--u));border-radius:calc(2*var(--u));background:var(--border);
                  overflow:hidden;max-width:calc(280*var(--u));margin-bottom:calc(14*var(--u))}
            .prog i{display:block;height:100%;width:38%;border-radius:calc(2*var(--u));background:var(--fl);
                    animation:slide 1.5s ease-in-out infinite}
            @keyframes slide{0%{transform:translateX(-100%)}100%{transform:translateX(365%)}}
            @media (prefers-reduced-motion:reduce){.prog i{animation:none;width:100%;opacity:.5}}

            .err{display:flex;gap:calc(11*var(--u));align-items:flex-start;
                 padding:calc(12*var(--u)) calc(14*var(--u));border:calc(1*var(--u)) solid var(--err);
                 border-radius:calc(8*var(--u));margin-bottom:calc(16*var(--u));max-width:calc(820*var(--u))}
            .err .ico{color:var(--err);margin-top:calc(2*var(--u))}
            .err .t{font-size:calc(13.5*var(--u));font-weight:600}
            .err .m{font-size:calc(12.5*var(--u));color:var(--sec);margin-top:calc(2*var(--u));
                    font-family:var(--mono);line-height:1.5;-webkit-user-select:text;user-select:text;
                    white-space:pre-wrap;word-break:break-word}

            .ico{width:calc(16*var(--u));height:calc(16*var(--u));flex:none;display:block}
            .ico-lg{width:calc(20*var(--u));height:calc(20*var(--u))}
            .chev{width:calc(11*var(--u));height:calc(11*var(--u));flex:none;display:block}
            """;
}
