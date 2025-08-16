package com.tonic.remapper.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaHighlighter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class CodeArea extends RSyntaxTextArea {
    private JDialog searchDialog;
    private JTextField searchField;
    private JCheckBox caseSensitiveCheckBox;
    private JCheckBox wholeWordCheckBox;
    private JCheckBox regexCheckBox;
    private JLabel statusLabel;

    public CodeArea(int rows, int cols) {
        super(rows, cols);

        setTheme();
        setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        setCodeFoldingEnabled(true);
        setAnimateBracketMatching(true);
        setAutoIndentEnabled(true);
        setHighlighter(new RSyntaxTextAreaHighlighter());
        setEditable(false);

        // Add search key binding
        setupSearchKeyBinding();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handleRightClick(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    handleRightClick(e);
                }
            }

            private void handleRightClick(MouseEvent e) {
                if (getSelectedText() == null) {
                    int offset = viewToModel2D(e.getPoint());
                    setCaretPosition(offset);
                }
            }
        });

        JPopupMenu popupMenu = getPopupMenu();
        popupMenu.addSeparator();

        // Add search option to popup menu
        JMenuItem searchItem = new JMenuItem("Find...");
        searchItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK));
        searchItem.addActionListener(e -> showSearchDialog());
        popupMenu.add(searchItem);
    }

    private void setupSearchKeyBinding() {
        // Add Ctrl+F key binding
        KeyStroke ctrlF = KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK);
        getInputMap().put(ctrlF, "find");
        getActionMap().put("find", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showSearchDialog();
            }
        });

        // Add Ctrl+G (Find Next) key binding
        KeyStroke ctrlG = KeyStroke.getKeyStroke(KeyEvent.VK_G, KeyEvent.CTRL_DOWN_MASK);
        getInputMap().put(ctrlG, "findNext");
        getActionMap().put("findNext", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchField != null && !searchField.getText().isEmpty()) {
                    findNext();
                } else {
                    showSearchDialog();
                }
            }
        });

        // Add Ctrl+Shift+G (Find Previous) key binding
        KeyStroke ctrlShiftG = KeyStroke.getKeyStroke(KeyEvent.VK_G,
                KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK);
        getInputMap().put(ctrlShiftG, "findPrevious");
        getActionMap().put("findPrevious", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (searchField != null && !searchField.getText().isEmpty()) {
                    findPrevious();
                } else {
                    showSearchDialog();
                }
            }
        });
    }

    private void showSearchDialog() {
        if (searchDialog == null) {
            createSearchDialog();
        }

        // Pre-populate search field with selected text if any
        String selectedText = getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            searchField.setText(selectedText);
        }

        searchDialog.setLocationRelativeTo(this);
        searchDialog.setVisible(true);
        searchField.requestFocus();
        searchField.selectAll();
    }

    private void createSearchDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        searchDialog = new JDialog(parent, "Find", Dialog.ModalityType.MODELESS);
        searchDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        searchDialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search input panel
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.add(new JLabel("Find:"), BorderLayout.WEST);
        searchField = new JTextField(20);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Options panel
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        caseSensitiveCheckBox = new JCheckBox("Case sensitive");
        wholeWordCheckBox = new JCheckBox("Whole word");
        regexCheckBox = new JCheckBox("Regex");
        optionsPanel.add(caseSensitiveCheckBox);
        optionsPanel.add(wholeWordCheckBox);
        optionsPanel.add(regexCheckBox);

        // Buttons panel
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton findNextButton = new JButton("Find Next");
        JButton findPrevButton = new JButton("Find Previous");
        JButton closeButton = new JButton("Close");

        buttonsPanel.add(findNextButton);
        buttonsPanel.add(findPrevButton);
        buttonsPanel.add(closeButton);

        // Status label
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);

        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(searchPanel, BorderLayout.NORTH);
        topPanel.add(optionsPanel, BorderLayout.CENTER);
        topPanel.add(statusLabel, BorderLayout.SOUTH);

        panel.add(topPanel, BorderLayout.CENTER);
        panel.add(buttonsPanel, BorderLayout.SOUTH);

        searchDialog.add(panel);

        // Add listeners
        findNextButton.addActionListener(e -> findNext());
        findPrevButton.addActionListener(e -> findPrevious());
        closeButton.addActionListener(e -> searchDialog.setVisible(false));

        // Enter key in search field triggers find next
        searchField.addActionListener(e -> findNext());

        // Escape key closes dialog
        KeyStroke escapeStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeStroke, "ESCAPE");
        panel.getActionMap().put("ESCAPE", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchDialog.setVisible(false);
            }
        });

        searchDialog.pack();
    }

    private void findNext() {
        performSearch(true);
    }

    private void findPrevious() {
        performSearch(false);
    }

    private void performSearch(boolean forward) {
        if (searchField == null || searchField.getText().isEmpty()) {
            return;
        }

        SearchContext context = new SearchContext();
        context.setSearchFor(searchField.getText());
        context.setMatchCase(caseSensitiveCheckBox.isSelected());
        context.setWholeWord(wholeWordCheckBox.isSelected());
        context.setRegularExpression(regexCheckBox.isSelected());
        context.setSearchForward(forward);
        context.setMarkAll(true);

        SearchResult result = SearchEngine.find(this, context);

        if (!result.wasFound()) {
            statusLabel.setText("Text not found");
            // Optionally, wrap search
            if (result.getMarkedCount() == 0) {
                Toolkit.getDefaultToolkit().beep();
            } else {
                // Try wrapping around
                setCaretPosition(forward ? 0 : getText().length());
                result = SearchEngine.find(this, context);
                if (result.wasFound()) {
                    statusLabel.setText("Wrapped search");
                }
            }
        } else {
            statusLabel.setText(" ");
        }
    }

    private void setTheme() {
        try {
            Theme theme = Theme.load(getClass().getResourceAsStream(
                    "/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(this);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Convenience method to get the parent scroll pane if you need it
     * for advanced search features like highlighting all occurrences
     */
    public RTextScrollPane getScrollPane() {
        Container parent = getParent();
        while (parent != null) {
            if (parent instanceof RTextScrollPane) {
                return (RTextScrollPane) parent;
            }
            parent = parent.getParent();
        }
        return null;
    }
}