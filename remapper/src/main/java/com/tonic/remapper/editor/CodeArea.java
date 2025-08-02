package com.tonic.remapper.editor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaHighlighter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class CodeArea extends RSyntaxTextArea
{
    public CodeArea(int rows, int cols) {
        super(rows, cols);

        setTheme();
        setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        setCodeFoldingEnabled(true);
        setCodeFoldingEnabled(true);
        setAnimateBracketMatching(true);
        setAutoIndentEnabled(true);
        setHighlighter(new RSyntaxTextAreaHighlighter());
        setEditable(false);

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

        setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        setCodeFoldingEnabled(true);
        setEditable(false);
    }

    private void setTheme()
    {
        try
        {
            Theme theme = Theme.load(getClass().getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/dark.xml"));
            theme.apply(this);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }
}