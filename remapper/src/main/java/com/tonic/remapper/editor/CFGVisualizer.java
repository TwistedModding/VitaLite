package com.tonic.remapper.editor;

import com.mxgraph.canvas.mxGraphics2DCanvas;
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout;
import com.mxgraph.layout.mxCompactTreeLayout;
import com.mxgraph.shape.mxRectangleShape;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.util.mxConstants;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import com.mxgraph.view.mxStylesheet;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CFGVisualizer extends JPanel {
    private mxGraph graph;
    private Object parent;
    private final Map<String, Object> nodesMap = new HashMap<>();
    private final Map<BasicBlock, String> blockLabels = new HashMap<>();
    private double scale = 1.0;
    private MethodNode methodNode;
    private List<BasicBlock> basicBlocks;
    private boolean useHierarchicalLayout = true;

    private static class Colors {
        static final String BACKGROUND = "#2b2b2b";
        static final String BACKGROUND_EXCEPTION = "#3a2b2b";
        static final String KEYWORDS = "#cc7832";
        static final String FUNCTIONS = "#ffc66d";
        static final String VARIABLES = "#9876aa";
        static final String VALUES = "#6a8759";
        static final String OPERATORS = "#a9b7c6";
        static final String NOTATION = "#808080";
        static final String EDGE_LABEL_COLOR = "#629755";
        static final String JUMP_INSTRUCTION = "#cc7832";
        static final String LOAD_STORE = "#9876aa";
        static final String ARITHMETIC = "#ffc66d";
        static final String INVOKE = "#6897bb";
        static final String EXCEPTION_EDGE = "#ff6b68";
    }

    public static CFGVisualizer create(MethodNode methodNode) {
        CFGVisualizer panel = new CFGVisualizer(methodNode);
        panel.setVisible(true);
        return panel;
    }

    private CFGVisualizer(MethodNode methodNode) {
        this.methodNode = methodNode;
        setLayout(new BorderLayout());
        init();

        addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoomIn();
            } else {
                zoomOut();
            }
        });
    }

    private void init() {
        this.graph = new mxGraph();
        this.graph.getView().setScale(scale);
        this.graph.setCellsEditable(false);
        this.graph.setCellsMovable(false);
        this.graph.setAutoOrigin(true);
        this.graph.setCellsSelectable(false);
        this.graph.setEnabled(true);
        this.graph.setAutoSizeCells(true);
        this.graph.setHtmlLabels(true);
        this.graph.setAllowDanglingEdges(true);

        setStyles();
        this.parent = graph.getDefaultParent();
        this.basicBlocks = createBasicBlocks(methodNode);
        if (basicBlocks.size() == 1 && methodNode.instructions.size() > 10) {
            System.err.println("Warning: Only one basic block found for method with " +
                    methodNode.instructions.size() + " instructions. Trying alternative parsing...");
            this.basicBlocks = createBasicBlocksAlternative(methodNode);
        }

        graph.getModel().beginUpdate();
        try {
            nodesMap.clear();
            blockLabels.clear();
            createGraphFromBlocks();
        } finally {
            graph.getModel().endUpdate();
            applyLayout();
            graph.setCellsMovable(false);
            graph.setCellsResizable(false);
            graph.setCellsEditable(false);
            graph.setEdgeLabelsMovable(false);
            graph.setConnectableEdges(false);
        }

        removeAll();
        graph.setEventsEnabled(true);
        mxGraphComponent graphComponent = new mxGraphComponent(graph);
        graphComponent.setPanning(true);
        graphComponent.setAutoScroll(true);
        graphComponent.setBackground(Color.decode("#1e1e1e"));
        graphComponent.getViewport().setOpaque(true);
        graphComponent.getViewport().setBackground(Color.decode("#1e1e1e"));

        graphComponent.getGraphHandler().setRemoveCellsFromParent(false);
        graphComponent.getGraphHandler().setMoveEnabled(false);

        graphComponent.addMouseWheelListener(e -> {
            if (e.getPreciseWheelRotation() < 0) {
                zoomIn();
            } else {
                zoomOut();
            }
        });

        add(graphComponent, BorderLayout.CENTER);
        add(createControlPanel(), BorderLayout.NORTH);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBackground(Color.decode("#2b2b2b"));

        JButton layoutToggle = new JButton("Toggle Layout");
        layoutToggle.addActionListener(e -> {
            useHierarchicalLayout = !useHierarchicalLayout;
            applyLayout();
        });

        JButton zoomFit = new JButton("Fit");
        zoomFit.addActionListener(e -> {
            scale = 1.0;
            graph.getView().setScale(scale);
        });

        JLabel info = new JLabel("Blocks: " + basicBlocks.size());
        info.setForeground(Color.WHITE);

        panel.add(layoutToggle);
        panel.add(zoomFit);
        panel.add(info);

        return panel;
    }

    private List<BasicBlock> createBasicBlocks(MethodNode method) {
        List<BasicBlock> blocks = new ArrayList<>();
        AbstractInsnNode[] instructions = method.instructions.toArray();

        if (instructions.length == 0) {
            return blocks;
        }

        Map<AbstractInsnNode, Integer> insnToIndex = new IdentityHashMap<>();
        Map<LabelNode, Integer> labelToIndex = new IdentityHashMap<>();

        int realIndex = 0;
        for (AbstractInsnNode insn : instructions) {
            if (insn instanceof LabelNode) {
                labelToIndex.put((LabelNode) insn, realIndex);
            } else if (insn.getOpcode() >= 0) { // Real instruction
                insnToIndex.put(insn, realIndex);
                realIndex++;
            }
        }

        Set<Integer> leaders = new TreeSet<>();
        leaders.add(0);

        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
                addLabelAsLeader(tcb.start, labelToIndex, leaders);
                addLabelAsLeader(tcb.end, labelToIndex, leaders);
                addLabelAsLeader(tcb.handler, labelToIndex, leaders);
            }
        }

        realIndex = 0;
        for (AbstractInsnNode insn : instructions) {
            if (insn.getOpcode() < 0) continue;

            if (insn instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) insn;
                addLabelAsLeader(jump.label, labelToIndex, leaders);
                if (realIndex + 1 < insnToIndex.size()) {
                    leaders.add(realIndex + 1);
                }
            } else if (insn instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) insn;
                addLabelAsLeader(tableSwitch.dflt, labelToIndex, leaders);
                for (LabelNode label : tableSwitch.labels) {
                    addLabelAsLeader(label, labelToIndex, leaders);
                }
                if (realIndex + 1 < insnToIndex.size()) {
                    leaders.add(realIndex + 1);
                }
            } else if (insn instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) insn;
                addLabelAsLeader(lookupSwitch.dflt, labelToIndex, leaders);
                for (LabelNode label : lookupSwitch.labels) {
                    addLabelAsLeader(label, labelToIndex, leaders);
                }
                if (realIndex + 1 < insnToIndex.size()) {
                    leaders.add(realIndex + 1);
                }
            } else if (isTerminalInstruction(insn)) {
                if (realIndex + 1 < insnToIndex.size()) {
                    leaders.add(realIndex + 1);
                }
            }
            realIndex++;
        }

        List<Integer> leaderList = new ArrayList<>(leaders);
        Collections.sort(leaderList);

        Map<Integer, Integer> realToArrayIndex = new HashMap<>();
        realIndex = 0;
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i].getOpcode() >= 0) {
                realToArrayIndex.put(realIndex++, i);
            }
        }

        for (int i = 0; i < leaderList.size(); i++) {
            int startRealIdx = leaderList.get(i);
            int endRealIdx = (i + 1 < leaderList.size()) ? leaderList.get(i + 1) : realIndex;

            if (startRealIdx >= endRealIdx) continue;

            BasicBlock block = new BasicBlock();
            block.id = blocks.size();

            Integer startArrayIdx = realToArrayIndex.get(startRealIdx);
            int endArrayIdx = (endRealIdx < realIndex) ? realToArrayIndex.get(endRealIdx) : instructions.length;

            if (startArrayIdx == null) continue;

            for (int j = startArrayIdx; j < endArrayIdx; j++) {
                AbstractInsnNode insn = instructions[j];
                block.instructions.add(insn);

                if (insn.getOpcode() >= 0) {
                    if (block.start == null) {
                        block.start = insn;
                        block.startIndex = j;
                    }
                    block.end = insn;
                    block.endIndex = j;
                }
            }

            if (block.start != null) {
                blocks.add(block);
            }
        }

        calculateSuccessors(blocks, method, labelToIndex);
        return blocks;
    }

    private List<BasicBlock> createBasicBlocksAlternative(MethodNode method) {
        List<BasicBlock> blocks = new ArrayList<>();
        AbstractInsnNode[] instructions = method.instructions.toArray();

        if (instructions.length == 0) return blocks;

        BasicBlock currentBlock = null;
        for (int i = 0; i < instructions.length; i++) {
            AbstractInsnNode insn = instructions[i];
            if (insn.getOpcode() >= 0 || shouldStartNewBlock(insn, instructions)) {
                if (currentBlock != null && !currentBlock.instructions.isEmpty()) {
                    blocks.add(currentBlock);
                }
                currentBlock = new BasicBlock();
                currentBlock.id = blocks.size();
                currentBlock.startIndex = i;
            }

            if (currentBlock != null) {
                currentBlock.instructions.add(insn);
                if (insn.getOpcode() >= 0) {
                    if (currentBlock.start == null) {
                        currentBlock.start = insn;
                    }
                    currentBlock.end = insn;
                    currentBlock.endIndex = i;
                }

                if (isControlFlowInstruction(insn)) {
                    blocks.add(currentBlock);
                    currentBlock = null;
                }
            }
        }

        if (currentBlock != null && !currentBlock.instructions.isEmpty()) {
            blocks.add(currentBlock);
        }

        Map<LabelNode, Integer> labelToIndex = new IdentityHashMap<>();
        for (int i = 0; i < instructions.length; i++) {
            if (instructions[i] instanceof LabelNode) {
                labelToIndex.put((LabelNode) instructions[i], i);
            }
        }

        calculateSuccessors(blocks, method, labelToIndex);

        return blocks;
    }

    private boolean shouldStartNewBlock(AbstractInsnNode insn, AbstractInsnNode[] instructions) {
        for (AbstractInsnNode prev : instructions) {
            if (prev instanceof JumpInsnNode && ((JumpInsnNode) prev).label == insn) {
                return true;
            }
        }
        return false;
    }

    private boolean isControlFlowInstruction(AbstractInsnNode insn) {
        return insn instanceof JumpInsnNode ||
                insn instanceof TableSwitchInsnNode ||
                insn instanceof LookupSwitchInsnNode ||
                isTerminalInstruction(insn);
    }

    private boolean isTerminalInstruction(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return isReturnInstruction(insn) || opcode == Opcodes.ATHROW;
    }

    private void addLabelAsLeader(LabelNode label, Map<LabelNode, Integer> labelToIndex, Set<Integer> leaders) {
        Integer index = labelToIndex.get(label);
        if (index != null) {
            leaders.add(index);
        }
    }

    private void calculateSuccessors(List<BasicBlock> blocks, MethodNode method, Map<LabelNode, Integer> labelToIndex) {
        Map<Integer, BasicBlock> indexToBlock = new HashMap<>();
        for (BasicBlock block : blocks) {
            for (int i = 0; i < block.instructions.size(); i++) {
                AbstractInsnNode insn = block.instructions.get(i);
                if (insn.getOpcode() >= 0) {
                    indexToBlock.put(block.startIndex + i, block);
                    break;
                }
            }
        }

        for (BasicBlock block : blocks) {
            if (block.instructions.isEmpty() || block.end == null) continue;

            AbstractInsnNode lastInsn = block.end;
            if (lastInsn instanceof JumpInsnNode) {
                JumpInsnNode jump = (JumpInsnNode) lastInsn;

                BasicBlock target = findBlockByLabel(jump.label, blocks);
                if (target != null) {
                    block.addSuccessor(target, "jump");
                }

                if (jump.getOpcode() != Opcodes.GOTO && jump.getOpcode() != Opcodes.JSR) {
                    BasicBlock fallThrough = findNextBlock(block, blocks);
                    if (fallThrough != null) {
                        block.addSuccessor(fallThrough, "fall");
                    }
                }
            } else if (lastInsn instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode) lastInsn;

                BasicBlock defaultTarget = findBlockByLabel(tableSwitch.dflt, blocks);
                if (defaultTarget != null) {
                    block.addSuccessor(defaultTarget, "default");
                }

                int caseNum = tableSwitch.min;
                for (LabelNode label : tableSwitch.labels) {
                    BasicBlock caseTarget = findBlockByLabel(label, blocks);
                    if (caseTarget != null) {
                        block.addSuccessor(caseTarget, "case " + caseNum);
                    }
                    caseNum++;
                }
            } else if (lastInsn instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookupSwitch = (LookupSwitchInsnNode) lastInsn;

                BasicBlock defaultTarget = findBlockByLabel(lookupSwitch.dflt, blocks);
                if (defaultTarget != null) {
                    block.addSuccessor(defaultTarget, "default");
                }

                for (int i = 0; i < lookupSwitch.labels.size(); i++) {
                    BasicBlock caseTarget = findBlockByLabel(lookupSwitch.labels.get(i), blocks);
                    if (caseTarget != null && i < lookupSwitch.keys.size()) {
                        block.addSuccessor(caseTarget, "case " + lookupSwitch.keys.get(i));
                    }
                }
            } else if (!isTerminalInstruction(lastInsn)) {
                BasicBlock next = findNextBlock(block, blocks);
                if (next != null) {
                    block.addSuccessor(next, "");
                }
            }
        }

        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
                BasicBlock handlerBlock = findBlockByLabel(tcb.handler, blocks);
                if (handlerBlock == null) continue;

                Integer startIdx = labelToIndex.get(tcb.start);
                Integer endIdx = labelToIndex.get(tcb.end);

                if (startIdx != null && endIdx != null) {
                    for (BasicBlock block : blocks) {
                        if (block.startIndex >= startIdx && block.startIndex < endIdx) {
                            block.addExceptionSuccessor(handlerBlock,
                                    tcb.type != null ? tcb.type : "Exception");
                        }
                    }
                }
            }
        }
    }

    private BasicBlock findBlockByLabel(LabelNode label, List<BasicBlock> blocks) {
        for (BasicBlock block : blocks) {
            for (AbstractInsnNode insn : block.instructions) {
                if (insn == label) {
                    return block;
                }
            }
        }

        return null;
    }

    private BasicBlock findNextBlock(BasicBlock current, List<BasicBlock> blocks) {
        int currentIndex = blocks.indexOf(current);
        if (currentIndex >= 0 && currentIndex + 1 < blocks.size()) {
            return blocks.get(currentIndex + 1);
        }
        return null;
    }

    private boolean isReturnInstruction(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        return opcode == Opcodes.RETURN || opcode == Opcodes.IRETURN ||
                opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN ||
                opcode == Opcodes.DRETURN || opcode == Opcodes.ARETURN;
    }

    private void createGraphFromBlocks() {
        double y = 20;
        for (BasicBlock block : basicBlocks) {
            String label = createLabelFromBlock(block);
            String blockId = "block_" + block.id;

            String bgColor = Colors.BACKGROUND;
            if (block.isExceptionHandler) {
                bgColor = Colors.BACKGROUND_EXCEPTION;
            }

            Object graphNode = graph.insertVertex(parent, blockId, label, 20, y, 250, 100,
                    "fillColor=" + bgColor + ";fontSize=11;fontFamily=monospace;fontColor=" + Colors.OPERATORS +
                            ";strokeColor=" + Colors.NOTATION + ";strokeWidth=1.5;");
            graph.updateCellSize(graphNode);

            nodesMap.put(blockId, graphNode);
            blockLabels.put(block, blockId);

            y += 120;
        }

        for (BasicBlock block : basicBlocks) {
            String sourceId = blockLabels.get(block);
            Object sourceNode = nodesMap.get(sourceId);

            for (int i = 0; i < block.successors.size(); i++) {
                EdgeInfo edge = block.successors.get(i);
                String targetId = blockLabels.get(edge.target);
                Object targetNode = nodesMap.get(targetId);

                if (sourceNode != null && targetNode != null) {
                    String edgeLabel = edge.label;
                    String style = "fontSize=10;fontColor=" + Colors.EDGE_LABEL_COLOR + ";";
                    graph.insertEdge(parent, null, edgeLabel, sourceNode, targetNode, style);
                }
            }

            for (EdgeInfo edge : block.exceptionSuccessors) {
                String targetId = blockLabels.get(edge.target);
                Object targetNode = nodesMap.get(targetId);

                if (sourceNode != null && targetNode != null) {
                    String edgeLabel = "catch(" + edge.label + ")";
                    String style = "fontSize=10;fontColor=" + Colors.EXCEPTION_EDGE +
                            ";strokeColor=" + Colors.EXCEPTION_EDGE + ";dashed=1;";
                    graph.insertEdge(parent, null, edgeLabel, sourceNode, targetNode, style);
                }
            }
        }
    }

    private String createLabelFromBlock(BasicBlock block) {
        StringBuilder label = new StringBuilder();
        label.append("<html><div style='padding:5px;'>");

        // Block header
        label.append("<b>").append(colorize("Block " + block.id, Colors.NOTATION)).append("</b>");
        if (block.startIndex >= 0) {
            label.append(colorize(" [" + block.startIndex + "-" + block.endIndex + "]", Colors.NOTATION));
        }
        label.append("<br>");

        int displayCount = 0;
        int maxDisplay = 20;
        boolean truncated = false;

        for (AbstractInsnNode insn : block.instructions) {
            if (displayCount >= maxDisplay) {
                truncated = true;
                break;
            }

            if (insn instanceof LabelNode) {
                LabelNode labelNode = (LabelNode) insn;
                label.append(colorize("L" + getLabelId(labelNode) + ":", Colors.NOTATION)).append("<br>");
            } else if (insn instanceof LineNumberNode) {
                LineNumberNode line = (LineNumberNode) insn;
                label.append(colorize("  // line " + line.line, Colors.NOTATION)).append("<br>");
            } else if (insn instanceof FrameNode) {
                // Skip frame nodes in display
                continue;
            } else if (insn.getOpcode() >= 0) {
                String instrText = formatInstruction(insn);
                label.append("  ").append(instrText).append("<br>");
                displayCount++;
            }
        }

        if (truncated) {
            label.append(colorize("  ... (" + (block.instructions.size() - maxDisplay) + " more instructions)", Colors.NOTATION));
        }

        label.append("</div></html>");
        return label.toString();
    }

    private String getLabelId(LabelNode label) {
        return Integer.toHexString(System.identityHashCode(label)).substring(0, 4);
    }

    private String formatInstruction(AbstractInsnNode insn) {
        String opcodeName = getOpcodeName(insn.getOpcode());
        String color = getInstructionColor(insn);

        StringBuilder formatted = new StringBuilder();
        formatted.append(colorize(opcodeName, color));

        // Add operands based on instruction type
        if (insn instanceof IntInsnNode) {
            IntInsnNode intInsn = (IntInsnNode) insn;
            formatted.append(" ").append(colorize(String.valueOf(intInsn.operand), Colors.VALUES));
        } else if (insn instanceof VarInsnNode) {
            VarInsnNode varInsn = (VarInsnNode) insn;
            formatted.append(" ").append(colorize("var" + varInsn.var, Colors.VARIABLES));
        } else if (insn instanceof FieldInsnNode) {
            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
            String fieldName = simplifyClassName(fieldInsn.owner) + "." + fieldInsn.name;
            formatted.append(" ").append(colorize(fieldName, Colors.VARIABLES));
        } else if (insn instanceof MethodInsnNode) {
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            String methodName = simplifyClassName(methodInsn.owner) + "." + methodInsn.name;
            formatted.append(" ").append(colorize(methodName, Colors.FUNCTIONS));
        } else if (insn instanceof JumpInsnNode) {
            JumpInsnNode jump = (JumpInsnNode) insn;
            formatted.append(" ").append(colorize("-> L:" + getLabelId(jump.label), Colors.JUMP_INSTRUCTION));
        } else if (insn instanceof LdcInsnNode) {
            LdcInsnNode ldcInsn = (LdcInsnNode) insn;
            String value = formatConstant(ldcInsn.cst);
            formatted.append(" ").append(colorize(value, Colors.VALUES));
        } else if (insn instanceof IincInsnNode) {
            IincInsnNode iincInsn = (IincInsnNode) insn;
            formatted.append(" ").append(colorize("var" + iincInsn.var, Colors.VARIABLES))
                    .append(" ").append(colorize(String.valueOf(iincInsn.incr), Colors.VALUES));
        } else if (insn instanceof TypeInsnNode) {
            TypeInsnNode typeInsn = (TypeInsnNode) insn;
            formatted.append(" ").append(colorize(simplifyClassName(typeInsn.desc), Colors.KEYWORDS));
        } else if (insn instanceof MultiANewArrayInsnNode) {
            MultiANewArrayInsnNode arrayInsn = (MultiANewArrayInsnNode) insn;
            formatted.append(" ").append(colorize(simplifyClassName(arrayInsn.desc), Colors.KEYWORDS))
                    .append(" ").append(colorize(String.valueOf(arrayInsn.dims), Colors.VALUES));
        }

        return formatted.toString();
    }

    private String simplifyClassName(String className) {
        if (className == null) return "null";

        // Handle array types
        if (className.startsWith("[")) {
            return simplifyClassName(className.substring(1)) + "[]";
        }

        // Handle primitive types
        if (className.length() == 1) {
            switch (className.charAt(0)) {
                case 'I': return "int";
                case 'J': return "long";
                case 'F': return "float";
                case 'D': return "double";
                case 'B': return "byte";
                case 'C': return "char";
                case 'S': return "short";
                case 'Z': return "boolean";
                case 'V': return "void";
            }
        }

        // Handle object types
        if (className.startsWith("L") && className.endsWith(";")) {
            className = className.substring(1, className.length() - 1);
        }

        // Simplify package names
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            return className.substring(lastSlash + 1);
        }

        return className;
    }

    private String formatConstant(Object cst) {
        if (cst instanceof String) {
            String str = (String) cst;
            if (str.length() > 20) {
                str = str.substring(0, 20) + "...";
            }
            return "\"" + str.replace("\n", "\\n") + "\"";
        } else if (cst instanceof Type) {
            return simplifyClassName(((Type) cst).getInternalName()) + ".class";
        } else {
            return String.valueOf(cst);
        }
    }

    private String getOpcodeName(int opcode) {
        // Using reflection would be cleaner, but this is more efficient
        switch (opcode) {
            case Opcodes.NOP: return "nop";
            case Opcodes.ACONST_NULL: return "aconst_null";
            case Opcodes.ICONST_M1: return "iconst_m1";
            case Opcodes.ICONST_0: return "iconst_0";
            case Opcodes.ICONST_1: return "iconst_1";
            case Opcodes.ICONST_2: return "iconst_2";
            case Opcodes.ICONST_3: return "iconst_3";
            case Opcodes.ICONST_4: return "iconst_4";
            case Opcodes.ICONST_5: return "iconst_5";
            case Opcodes.LCONST_0: return "lconst_0";
            case Opcodes.LCONST_1: return "lconst_1";
            case Opcodes.FCONST_0: return "fconst_0";
            case Opcodes.FCONST_1: return "fconst_1";
            case Opcodes.FCONST_2: return "fconst_2";
            case Opcodes.DCONST_0: return "dconst_0";
            case Opcodes.DCONST_1: return "dconst_1";
            case Opcodes.BIPUSH: return "bipush";
            case Opcodes.SIPUSH: return "sipush";
            case Opcodes.LDC: return "ldc";
            case Opcodes.ILOAD: return "iload";
            case Opcodes.LLOAD: return "lload";
            case Opcodes.FLOAD: return "fload";
            case Opcodes.DLOAD: return "dload";
            case Opcodes.ALOAD: return "aload";
            case Opcodes.IALOAD: return "iaload";
            case Opcodes.LALOAD: return "laload";
            case Opcodes.FALOAD: return "faload";
            case Opcodes.DALOAD: return "daload";
            case Opcodes.AALOAD: return "aaload";
            case Opcodes.BALOAD: return "baload";
            case Opcodes.CALOAD: return "caload";
            case Opcodes.SALOAD: return "saload";
            case Opcodes.ISTORE: return "istore";
            case Opcodes.LSTORE: return "lstore";
            case Opcodes.FSTORE: return "fstore";
            case Opcodes.DSTORE: return "dstore";
            case Opcodes.ASTORE: return "astore";
            case Opcodes.IASTORE: return "iastore";
            case Opcodes.LASTORE: return "lastore";
            case Opcodes.FASTORE: return "fastore";
            case Opcodes.DASTORE: return "dastore";
            case Opcodes.AASTORE: return "aastore";
            case Opcodes.BASTORE: return "bastore";
            case Opcodes.CASTORE: return "castore";
            case Opcodes.SASTORE: return "sastore";
            case Opcodes.POP: return "pop";
            case Opcodes.POP2: return "pop2";
            case Opcodes.DUP: return "dup";
            case Opcodes.DUP_X1: return "dup_x1";
            case Opcodes.DUP_X2: return "dup_x2";
            case Opcodes.DUP2: return "dup2";
            case Opcodes.DUP2_X1: return "dup2_x1";
            case Opcodes.DUP2_X2: return "dup2_x2";
            case Opcodes.SWAP: return "swap";
            case Opcodes.IADD: return "iadd";
            case Opcodes.LADD: return "ladd";
            case Opcodes.FADD: return "fadd";
            case Opcodes.DADD: return "dadd";
            case Opcodes.ISUB: return "isub";
            case Opcodes.LSUB: return "lsub";
            case Opcodes.FSUB: return "fsub";
            case Opcodes.DSUB: return "dsub";
            case Opcodes.IMUL: return "imul";
            case Opcodes.LMUL: return "lmul";
            case Opcodes.FMUL: return "fmul";
            case Opcodes.DMUL: return "dmul";
            case Opcodes.IDIV: return "idiv";
            case Opcodes.LDIV: return "ldiv";
            case Opcodes.FDIV: return "fdiv";
            case Opcodes.DDIV: return "ddiv";
            case Opcodes.IREM: return "irem";
            case Opcodes.LREM: return "lrem";
            case Opcodes.FREM: return "frem";
            case Opcodes.DREM: return "drem";
            case Opcodes.INEG: return "ineg";
            case Opcodes.LNEG: return "lneg";
            case Opcodes.FNEG: return "fneg";
            case Opcodes.DNEG: return "dneg";
            case Opcodes.ISHL: return "ishl";
            case Opcodes.LSHL: return "lshl";
            case Opcodes.ISHR: return "ishr";
            case Opcodes.LSHR: return "lshr";
            case Opcodes.IUSHR: return "iushr";
            case Opcodes.LUSHR: return "lushr";
            case Opcodes.IAND: return "iand";
            case Opcodes.LAND: return "land";
            case Opcodes.IOR: return "ior";
            case Opcodes.LOR: return "lor";
            case Opcodes.IXOR: return "ixor";
            case Opcodes.LXOR: return "lxor";
            case Opcodes.IINC: return "iinc";
            case Opcodes.I2L: return "i2l";
            case Opcodes.I2F: return "i2f";
            case Opcodes.I2D: return "i2d";
            case Opcodes.L2I: return "l2i";
            case Opcodes.L2F: return "l2f";
            case Opcodes.L2D: return "l2d";
            case Opcodes.F2I: return "f2i";
            case Opcodes.F2L: return "f2l";
            case Opcodes.F2D: return "f2d";
            case Opcodes.D2I: return "d2i";
            case Opcodes.D2L: return "d2l";
            case Opcodes.D2F: return "d2f";
            case Opcodes.I2B: return "i2b";
            case Opcodes.I2C: return "i2c";
            case Opcodes.I2S: return "i2s";
            case Opcodes.LCMP: return "lcmp";
            case Opcodes.FCMPL: return "fcmpl";
            case Opcodes.FCMPG: return "fcmpg";
            case Opcodes.DCMPL: return "dcmpl";
            case Opcodes.DCMPG: return "dcmpg";
            case Opcodes.IFEQ: return "ifeq";
            case Opcodes.IFNE: return "ifne";
            case Opcodes.IFLT: return "iflt";
            case Opcodes.IFGE: return "ifge";
            case Opcodes.IFGT: return "ifgt";
            case Opcodes.IFLE: return "ifle";
            case Opcodes.IF_ICMPEQ: return "if_icmpeq";
            case Opcodes.IF_ICMPNE: return "if_icmpne";
            case Opcodes.IF_ICMPLT: return "if_icmplt";
            case Opcodes.IF_ICMPGE: return "if_icmpge";
            case Opcodes.IF_ICMPGT: return "if_icmpgt";
            case Opcodes.IF_ICMPLE: return "if_icmple";
            case Opcodes.IF_ACMPEQ: return "if_acmpeq";
            case Opcodes.IF_ACMPNE: return "if_acmpne";
            case Opcodes.GOTO: return "goto";
            case Opcodes.JSR: return "jsr";
            case Opcodes.RET: return "ret";
            case Opcodes.TABLESWITCH: return "tableswitch";
            case Opcodes.LOOKUPSWITCH: return "lookupswitch";
            case Opcodes.IRETURN: return "ireturn";
            case Opcodes.LRETURN: return "lreturn";
            case Opcodes.FRETURN: return "freturn";
            case Opcodes.DRETURN: return "dreturn";
            case Opcodes.ARETURN: return "areturn";
            case Opcodes.RETURN: return "return";
            case Opcodes.GETSTATIC: return "getstatic";
            case Opcodes.PUTSTATIC: return "putstatic";
            case Opcodes.GETFIELD: return "getfield";
            case Opcodes.PUTFIELD: return "putfield";
            case Opcodes.INVOKEVIRTUAL: return "invokevirtual";
            case Opcodes.INVOKESPECIAL: return "invokespecial";
            case Opcodes.INVOKESTATIC: return "invokestatic";
            case Opcodes.INVOKEINTERFACE: return "invokeinterface";
            case Opcodes.INVOKEDYNAMIC: return "invokedynamic";
            case Opcodes.NEW: return "new";
            case Opcodes.NEWARRAY: return "newarray";
            case Opcodes.ANEWARRAY: return "anewarray";
            case Opcodes.ARRAYLENGTH: return "arraylength";
            case Opcodes.ATHROW: return "athrow";
            case Opcodes.CHECKCAST: return "checkcast";
            case Opcodes.INSTANCEOF: return "instanceof";
            case Opcodes.MONITORENTER: return "monitorenter";
            case Opcodes.MONITOREXIT: return "monitorexit";
            case Opcodes.MULTIANEWARRAY: return "multianewarray";
            case Opcodes.IFNULL: return "ifnull";
            case Opcodes.IFNONNULL: return "ifnonnull";
            default: return "unknown_" + opcode;
        }
    }

    /**
     * Gets the color for an instruction based on its type
     */
    private String getInstructionColor(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();

        // Jump instructions
        if (opcode >= Opcodes.IFEQ && opcode <= Opcodes.JSR ||
                opcode == Opcodes.TABLESWITCH ||
                opcode == Opcodes.LOOKUPSWITCH ||
                opcode == Opcodes.IFNULL ||
                opcode == Opcodes.IFNONNULL) {
            return Colors.JUMP_INSTRUCTION;
        }

        // Load/Store instructions
        if ((opcode >= Opcodes.ILOAD && opcode <= Opcodes.SALOAD) ||
                (opcode >= Opcodes.ISTORE && opcode <= Opcodes.SASTORE)) {
            return Colors.LOAD_STORE;
        }

        // Arithmetic instructions
        if ((opcode >= Opcodes.IADD && opcode <= Opcodes.DREM) ||
                (opcode >= Opcodes.INEG && opcode <= Opcodes.DNEG) ||
                (opcode >= Opcodes.ISHL && opcode <= Opcodes.LXOR)) {
            return Colors.ARITHMETIC;
        }

        // Invoke instructions
        if (opcode >= Opcodes.INVOKEVIRTUAL && opcode <= Opcodes.INVOKEDYNAMIC) {
            return Colors.INVOKE;
        }

        // Field access
        if (opcode >= Opcodes.GETSTATIC && opcode <= Opcodes.PUTFIELD) {
            return Colors.VARIABLES;
        }

        // Constants
        if (opcode >= Opcodes.ACONST_NULL && opcode <= Opcodes.LDC) {
            return Colors.VALUES;
        }

        return Colors.KEYWORDS;
    }

    private void setStyles() {
        Map<String, Object> edgeStyle = this.graph.getStylesheet().getDefaultEdgeStyle();
        edgeStyle.put(mxConstants.STYLE_ROUNDED, true);
        edgeStyle.put(mxConstants.STYLE_EDGE, mxConstants.EDGESTYLE_ORTHOGONAL);
        edgeStyle.put(mxConstants.STYLE_ENDARROW, mxConstants.ARROW_CLASSIC);
        edgeStyle.put(mxConstants.STYLE_TARGET_PERIMETER_SPACING, 2d);
        edgeStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5d);
        edgeStyle.put(mxConstants.STYLE_STROKECOLOR, Colors.OPERATORS);
        edgeStyle.put(mxConstants.STYLE_FONTCOLOR, Colors.EDGE_LABEL_COLOR);

        Map<String, Object> vertexStyle = this.graph.getStylesheet().getDefaultVertexStyle();
        vertexStyle.put(mxConstants.STYLE_AUTOSIZE, 1);
        vertexStyle.put(mxConstants.STYLE_SPACING, "8");
        vertexStyle.put(mxConstants.STYLE_ORTHOGONAL, "true");
        vertexStyle.put(mxConstants.STYLE_ROUNDED, true);
        vertexStyle.put(mxConstants.STYLE_ARCSIZE, 8);
        vertexStyle.put(mxConstants.STYLE_ALIGN, mxConstants.ALIGN_LEFT);
        vertexStyle.put(mxConstants.STYLE_VERTICAL_ALIGN, mxConstants.ALIGN_TOP);
        vertexStyle.put(mxConstants.STYLE_FONTCOLOR, Colors.OPERATORS);
        vertexStyle.put(mxConstants.STYLE_STROKECOLOR, Colors.NOTATION);
        vertexStyle.put(mxConstants.STYLE_STROKEWIDTH, 1.5d);

        mxGraphics2DCanvas.putShape(mxConstants.SHAPE_RECTANGLE, new mxRectangleShape() {
            @Override
            protected int getArcSize(mxCellState state, double w, double h) {
                return 10;
            }
        });

        mxStylesheet stylesheet = new mxStylesheet();
        stylesheet.setDefaultEdgeStyle(edgeStyle);
        stylesheet.setDefaultVertexStyle(vertexStyle);
        this.graph.setStylesheet(stylesheet);
    }

    private void applyLayout() {
        if (useHierarchicalLayout) {
            applyHierarchicalLayout();
        } else {
            applyTreeLayout();
        }
    }

    private void applyTreeLayout() {
        mxCompactTreeLayout layout = new mxCompactTreeLayout(graph, false);
        layout.setHorizontal(false);
        layout.setEdgeRouting(true);
        layout.setLevelDistance(50);
        layout.setNodeDistance(30);
        layout.setMoveTree(true);
        layout.setResizeParent(true);

        graph.getModel().beginUpdate();
        try {
            layout.execute(parent);
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private void applyHierarchicalLayout() {
        mxHierarchicalLayout layout = new mxHierarchicalLayout(graph);
        layout.setInterRankCellSpacing(50);
        layout.setIntraCellSpacing(30);
        layout.setOrientation(SwingConstants.NORTH);
        layout.setDisableEdgeStyle(false);

        graph.getModel().beginUpdate();
        try {
            layout.execute(parent);
        } finally {
            graph.getModel().endUpdate();
        }
    }

    private String colorize(String str, String color) {
        return "<font color=\"" + color + "\">" + escapeHtml(str) + "</font>";
    }

    private String escapeHtml(String str) {
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void zoomIn() {
        scale *= 1.2;
        if (scale > 5.0) {
            scale = 5.0;
        }
        graph.getView().setScale(scale);
        revalidate();
        repaint();
    }

    private void zoomOut() {
        scale /= 1.2;
        if (scale < 0.1) {
            scale = 0.1;
        }
        graph.getView().setScale(scale);
        revalidate();
        repaint();
    }

    public void updateMethod(MethodNode newMethodNode) {
        this.methodNode = newMethodNode;
        SwingUtilities.invokeLater(() -> {
            init();
            revalidate();
            repaint();
        });
    }

    private static class BasicBlock {
        int id;
        AbstractInsnNode start;
        AbstractInsnNode end;
        int startIndex = -1;
        int endIndex = -1;
        List<AbstractInsnNode> instructions = new ArrayList<>();
        List<EdgeInfo> successors = new ArrayList<>();
        List<EdgeInfo> exceptionSuccessors = new ArrayList<>();
        boolean isExceptionHandler = false;

        void addSuccessor(BasicBlock target, String label) {
            successors.add(new EdgeInfo(target, label));
        }

        void addExceptionSuccessor(BasicBlock target, String exceptionType) {
            exceptionSuccessors.add(new EdgeInfo(target, exceptionType));
            target.isExceptionHandler = true;
        }

        @Override
        public String toString() {
            return "Block[id=" + id + ", start=" + startIndex + ", end=" + endIndex +
                    ", instructions=" + instructions.size() + "]";
        }
    }
    private static class EdgeInfo {
        final BasicBlock target;
        final String label;

        EdgeInfo(BasicBlock target, String label) {
            this.target = target;
            this.label = label;
        }
    }
}