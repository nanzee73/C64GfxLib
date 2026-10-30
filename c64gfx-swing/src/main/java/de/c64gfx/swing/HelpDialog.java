package de.c64gfx.swing;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * User guide window: shows an HTML file of the application in the current language.
 * <p>
 * The files are resources named {@code <prefix>_<language>.html}, e.g. {@code /de/flipperj/help/help_de.html};
 * if the current language has none, the English one ({@code _en}) is shown. Links to {@code #name} jump to
 * {@code <a name="name">} inside the page, links to web pages open the browser. Swing renders HTML 3.2 with simple CSS,
 * so the pages should use plain headings, paragraphs, lists and tables.
 */
public final class HelpDialog extends JDialog {
    private static HelpDialog open;

    private final JEditorPane pane = new JEditorPane();

    private HelpDialog(Window owner, String title, String html) {
        super(owner, title, ModalityType.MODELESS);
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = new StyleSheet();
        css.addStyleSheet(kit.getStyleSheet());
        Font f = UIManager.getFont("Label.font");
        int size = f != null ? f.getSize() : 13;
        String family = f != null ? f.getFamily() : "SansSerif";
        css.addRule("body { font-family: " + family + "; font-size: " + size + "pt; margin: 12px 18px; }");
        css.addRule("h1 { font-size: " + (size + 8) + "pt; margin-top: 4px; }");
        css.addRule("h2 { font-size: " + (size + 4) + "pt; margin-top: 18px; border-bottom: 1px solid #999999; }");
        css.addRule("h3 { font-size: " + (size + 1) + "pt; margin-top: 12px; }");
        css.addRule("td, th { padding: 3px 6px; vertical-align: top; }");
        css.addRule("th { text-align: left; background: #e4e4ee; }");
        css.addRule("code { font-family: Monospaced; }");
        css.addRule("pre { font-family: Monospaced; background: #f2f2f2; padding: 6px; }");
        css.addRule(".tip { background: #eef4ff; padding: 6px; }");
        kit.setStyleSheet(css);
        pane.setEditorKit(kit);
        pane.setEditable(false);
        pane.setText(html);
        pane.setCaretPosition(0);
        pane.addHyperlinkListener(e -> {
            if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String d = e.getDescription();
            if (d != null && d.startsWith("#")) pane.scrollToReference(d.substring(1));
            else if (e.getURL() != null && Desktop.isDesktopSupported()) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {
                    // no browser available
                }
            }
        });

        JScrollPane scroll = new JScrollPane(pane);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JButton top = new JButton("▲");
        top.addActionListener(e -> {
            pane.setCaretPosition(0);
            scroll.getVerticalScrollBar().setValue(0);
        });
        JButton close = I18n.text(new JButton(), "close");
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(top);
        buttons.add(close);

        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(900, screen.width - 80), Math.min(760, screen.height - 80));
        setLocationRelativeTo(owner);
    }

    /**
     * Shows the user guide (or brings the open one to the front, reloaded in the current language).
     *
     * @param prefix resource path without language and extension, e.g. "/de/flipperj/help/help"
     */
    public static void show(Component parent, String title, String prefix) {
        String html;
        try {
            html = load(prefix, I18n.locale().getLanguage());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), title, JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (open != null) open.dispose();
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        if (parent instanceof Window w) owner = w;
        open = new HelpDialog(owner, title, html);
        open.setVisible(true);
    }

    /** The HTML text for a language, falling back to English. */
    public static String load(String prefix, String language) throws IOException {
        for (String lang : new String[]{language, "en"}) {
            String path = prefix + "_" + lang + ".html";
            InputStream res = HelpDialog.class.getResourceAsStream(path);
            if (res == null) {                       // the app's resources may sit in another class loader
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl != null) res = cl.getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
            }
            try (InputStream in = res) {
                if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Help not found: " + prefix + "_en.html");
    }
}
