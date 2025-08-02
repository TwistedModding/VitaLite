package com.tonic.remapper;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tonic.remapper.dto.JClass;
import com.tonic.remapper.dto.JField;
import com.tonic.remapper.dto.JMethod;
import com.tonic.remapper.methods.MethodKey;
import com.tonic.remapper.methods.UsedMethodScanner;
import com.tonic.remapper.ui.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Mapping editor for obfuscated JARs.
 * Loads classes, filters to used methods, lets the user assign readable names to classes/methods/fields,
 * and serializes the result into the existing DTO format (JClass/JField/JMethod) using Gson.
 */
public class MappingEditor extends JFrame {
    private final Map<String, ClassMapping> classMappings = new LinkedHashMap<>();
    private final JTree classTree = new JTree();
    private final MethodFieldTable methodTable = new MethodFieldTable(Kind.METHOD);
    private final MethodFieldTable fieldTable = new MethodFieldTable(Kind.FIELD);

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            new MappingEditor().setVisible(true);
        });
    }

    public MappingEditor() {
        super("Remap Name Editor");
        setIconImage(loadImageResource(MappingEditor.class, "icon.png"));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        buildMenuBar();

        classTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("<No JAR loaded>")));
        classTree.addTreeSelectionListener(this::onClassSelected);
        classTree.addMouseListener(new ClassTreePopup());

        JTabbedPane tabs = new JTabbedPane();
        JTable methodTableView = new JTable(methodTable);
        JTable fieldTableView = new JTable(fieldTable);
        methodTableView.setFillsViewportHeight(true);
        fieldTableView.setFillsViewportHeight(true);

        // search bar to filter methods/fields
        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JLabel searchLabel = new JLabel("Search:");
        JTextField searchField = new JTextField(30);
        JButton clearBtn = new JButton("Clear");
        clearBtn.setToolTipText("Clear search");
        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(clearBtn);

        JScrollPane methodScroll = new JScrollPane(methodTableView);
        JScrollPane fieldScroll = new JScrollPane(fieldTableView);
        tabs.addTab("Methods", methodScroll);
        tabs.addTab("Fields", fieldScroll);

        rightPanel.add(searchPanel, BorderLayout.NORTH);
        rightPanel.add(tabs, BorderLayout.CENTER);

        // wire search field to both tables
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                String text = searchField.getText();
                methodTable.setFilter(text);
                fieldTable.setFilter(text);
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            methodTable.setFilter("");
            fieldTable.setFilter("");
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(new JScrollPane(classTree));
        splitPane.setRightComponent(rightPanel);
        splitPane.setDividerLocation(300);
        getContentPane().add(splitPane, BorderLayout.CENTER);
    }

    private void buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new AbstractAction("Open JAR...") {
            @Override
            public void actionPerformed(ActionEvent e) { openJar(); }
        });
        file.add(new AbstractAction("Load Mapping...") {
            @Override
            public void actionPerformed(ActionEvent e) { loadMapping(); }
        });
        file.add(new AbstractAction("Save Mapping...") {
            @Override
            public void actionPerformed(ActionEvent e) { saveMapping(); }
        });
        file.addSeparator();
        file.add(new AbstractAction("Quit") {
            @Override
            public void actionPerformed(ActionEvent e) { dispose(); }
        });
        bar.add(file);
        setJMenuBar(bar);
    }

    private void openJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("C:/test/remap/"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path jarPath = chooser.getSelectedFile().toPath();
        try {
            loadJar(jarPath);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadJar(Path jarPath) throws Exception {
        classMappings.clear();
        List<ClassNode> classNodes = new ArrayList<>();

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> ents = jar.entries();
            while (ents.hasMoreElements()) {
                JarEntry entry = ents.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ClassNode cn = new ClassNode();
                    cr.accept(cn, 0);
                    classNodes.add(cn);
                }
            }
        }

        Set<MethodKey> used = UsedMethodScanner.findUsedMethods(classNodes);

        for (ClassNode cn : classNodes) {
            ClassMapping cm = new ClassMapping(cn, used);
            classMappings.put(cn.name, cm);
        }
        rebuildTree();
    }

    private void rebuildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("classes");
        List<ClassMapping> sorted = new ArrayList<>(classMappings.values());
        sorted.sort(Comparator.comparing(cm -> cm.originalName));
        for (ClassMapping cm : sorted) {
            root.add(new DefaultMutableTreeNode(cm));
        }
        classTree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < classTree.getRowCount(); i++) {
            classTree.expandRow(i);
        }
    }

    private void onClassSelected(TreeSelectionEvent ev) {
        TreePath path = ev.getPath();
        if (path == null) return;
        Object last = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (!(last instanceof ClassMapping)) return;
        ClassMapping cm = (ClassMapping) last;
        methodTable.setClass(cm);
        fieldTable.setClass(cm);
    }

    private void saveMapping() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("C:/test/remap/"));
        chooser.setSelectedFile(new File("mapping.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File outFile = chooser.getSelectedFile();

        List<JClass> dtoClasses = buildDtoClasses();
        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
            gson.toJson(dtoClasses, w);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<JClass> buildDtoClasses() {
        List<JClass> out = new ArrayList<>();
        for (ClassMapping cm : classMappings.values()) {
            JClass jc = new JClass();
            jc.setObfuscatedName(cm.originalName);
            jc.setName(cm.newName);

            // methods
            for (MethodRecord mr : cm.methods) {
                JMethod jm = new JMethod();
                jm.setObfuscatedName(mr.node.name);
                jm.setDescriptor(mr.node.desc);
                jm.setOwnerObfuscatedName(cm.originalName);
                jm.setOwner(jc.getName());
                jm.setName(mr.newName);
                jm.setStatic((mr.node.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0);
                jc.getMethods().add(jm);
            }

            // fields
            for (FieldRecord fr : cm.fields) {
                JField jf = new JField();
                jf.setObfuscatedName(fr.node.name);
                jf.setDescriptor(fr.node.desc);
                jf.setOwnerObfuscatedName(cm.originalName);
                jf.setOwner(jc.getName());
                jf.setName(fr.newName != null && !fr.newName.isBlank() ? fr.newName : "");
                jf.setStatic((fr.node.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0);
                jc.getFields().add(jf);
            }

            out.add(jc);
        }
        return out;
    }

    private void loadMapping() {
        if (classMappings.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Load a JAR first before loading a mapping.", "No JAR", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("C:/test/remap/"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File inFile = chooser.getSelectedFile();

        String previousSelectedClass = null;
        TreePath sel = classTree.getSelectionPath();
        if (sel != null) {
            Object last = ((DefaultMutableTreeNode) sel.getLastPathComponent()).getUserObject();
            if (last instanceof ClassMapping) {
                ClassMapping cm = (ClassMapping) last;
                previousSelectedClass = cm.originalName;
            }
        }

        Gson gson = new GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .setPrettyPrinting()
                .create();

        int unmatchedClasses = 0;
        try (Reader r = new InputStreamReader(new FileInputStream(inFile), StandardCharsets.UTF_8)) {
            JClass[] dtoClasses = gson.fromJson(r, JClass[].class);
            if (dtoClasses == null) {
                JOptionPane.showMessageDialog(this, "Mapping file appears empty or malformed.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            for (JClass jc : dtoClasses) {
                ClassMapping cm = classMappings.get(jc.getObfuscatedName());
                if (cm == null) {
                    unmatchedClasses++;
                    continue;
                }

                // class name
                cm.newName = jc.getName();

                // methods
                for (JMethod jm : jc.getMethods()) {
                    String key = jm.getObfuscatedName() + jm.getDescriptor();
                    for (MethodRecord mr : cm.methods) {
                        if (mr.node.name.equals(jm.getObfuscatedName()) && mr.node.desc.equals(jm.getDescriptor())) {
                            mr.newName = jm.getName();
                            if (jm.getName() != null && !jm.getName().isBlank()) {
                                cm.methodMap.put(key, jm.getName());
                            } else {
                                cm.methodMap.remove(key);
                            }
                            break;
                        }
                    }
                }

                // fields
                for (JField jf : jc.getFields()) {
                    String key = jf.getObfuscatedName() + " " + jf.getDescriptor();
                    for (FieldRecord fr : cm.fields) {
                        if (fr.node.name.equals(jf.getObfuscatedName()) && fr.node.desc.equals(jf.getDescriptor())) {
                            fr.newName = jf.getName();
                            if (jf.getName() != null && !jf.getName().isBlank()) {
                                cm.fieldMap.put(key, jf.getName());
                            } else {
                                cm.fieldMap.remove(key);
                            }
                            break;
                        }
                    }
                }
            }

            rebuildTree();

            // reselect previous class if possible
            if (previousSelectedClass != null) {
                DefaultMutableTreeNode node = findNodeForClassMapping(previousSelectedClass);
                if (node != null) {
                    TreePath path = new TreePath(node.getPath());
                    classTree.setSelectionPath(path);
                    classTree.scrollPathToVisible(path);
                }
            }
            // refresh tables for current selection
            if (classTree.getSelectionPath() != null) {
                onClassSelected(new javax.swing.event.TreeSelectionEvent(
                        classTree, classTree.getSelectionPath(), false, null, classTree.getSelectionPath()
                ));
            }

            if (unmatchedClasses > 0) {
                JOptionPane.showMessageDialog(this,
                        unmatchedClasses + " class(es) in the mapping file did not exist in the currently loaded JAR and were skipped.",
                        "Partial Load", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        } catch (com.google.gson.JsonSyntaxException jse) {
            JOptionPane.showMessageDialog(this, "Failed to parse mapping JSON: " + jse.getMessage(), "Parse Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DefaultMutableTreeNode findNodeForClassMapping(String originalName) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) classTree.getModel().getRoot();
        if (root == null) return null;
        Enumeration<?> children = root.children();
        while (children.hasMoreElements()) {
            Object maybe = children.nextElement();
            if (maybe instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) maybe;
                Object user = node.getUserObject();
                if (user instanceof ClassMapping && ((ClassMapping)user).originalName.equals(originalName)) {
                    return node;
                }
            }
        }
        return null;
    }

    private class ClassTreePopup extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) { maybeShowPopup(e); }
        @Override
        public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

        private void maybeShowPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            TreePath path = classTree.getPathForLocation(e.getX(), e.getY());
            if (path == null) return;
            Object node = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
            if (!(node instanceof ClassMapping)) return;
            ClassMapping cm = (ClassMapping) node;
            JPopupMenu popup = new JPopupMenu();
            popup.add(new AbstractAction("Rename Class") {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    String current = cm.newName == null ? "" : cm.newName;
                    String inp = JOptionPane.showInputDialog(MappingEditor.this, "New name for class:", current);
                    if (inp != null) {
                        cm.newName = inp.trim();
                        ((DefaultTreeModel) classTree.getModel()).nodeChanged((DefaultMutableTreeNode) path.getLastPathComponent());
                    }
                }
            });
            popup.show(classTree, e.getX(), e.getY());
        }
    }

    public static BufferedImage loadImageResource(final Class<?> c, final String path)
    {
        try (InputStream in = c.getResourceAsStream(path))
        {
            assert in != null;
            synchronized (ImageIO.class)
            {
                return ImageIO.read(in);
            }
        }
        catch (Exception ignored)
        {
        }
        return null;
    }
}
