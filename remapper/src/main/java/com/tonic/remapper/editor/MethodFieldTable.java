package com.tonic.remapper.editor;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MethodFieldTable extends AbstractTableModel {
    private final Kind kind;
    private ClassMapping current;
    private String filter = "";
    private List<Integer> filteredRows = new ArrayList<>();
    private boolean showOnlyMapped = false;

    public MethodFieldTable(Kind k) { this.kind = k; }

    public void setClass(ClassMapping cm) {
        current = cm;
        updateFilteredRows();
        fireTableDataChanged();
    }

    public void setShowOnlyMapped(boolean b) {           // NEW
        showOnlyMapped = b;
        updateFilteredRows();
        fireTableDataChanged();
    }

    public void setFilter(String f) {
        if (f == null) f = "";
        filter = f.trim().toLowerCase(Locale.ROOT);
        updateFilteredRows();
        fireTableDataChanged();
    }

    private void updateFilteredRows() {
        filteredRows.clear();
        if (current == null) return;

        /* decide upfront whether a record passes the "mapped" test */
        java.util.function.Predicate<String> notBlank =
                s -> s != null && !s.isBlank();

        if (kind == Kind.METHOD) {
            for (int i = 0; i < current.methods.size(); i++) {
                MethodRecord mr = current.methods.get(i);

                if (showOnlyMapped && !notBlank.test(mr.newName)) continue; // NEW

                String obf    = (mr.node.name + mr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = mr.newName != null ? mr.newName.toLowerCase(Locale.ROOT) : "";

                if (filter.isEmpty() || obf.contains(filter) || mapped.contains(filter))
                    filteredRows.add(i);
            }
        } else {
            for (int i = 0; i < current.fields.size(); i++) {
                FieldRecord fr = current.fields.get(i);

                if (showOnlyMapped && !notBlank.test(fr.newName)) continue; // NEW

                String obf    = (fr.node.name + " " + fr.node.desc).toLowerCase(Locale.ROOT);
                String mapped = fr.newName != null ? fr.newName.toLowerCase(Locale.ROOT) : "";

                if (filter.isEmpty() || obf.contains(filter) || mapped.contains(filter))
                    filteredRows.add(i);
            }
        }
    }

    @Override
    public int getRowCount() { return current == null ? 0 : filteredRows.size(); }

    @Override
    public int getColumnCount() {
        return kind == Kind.METHOD ? 3  /* Obf / Mapped / Garbage */
                : 4; /* Obf / Mapped / Setter / Getter */
    }

    @Override
    public String getColumnName(int c) {
        if (kind == Kind.METHOD) {
            switch (c) {
                case 1:
                    return "Mapped";
                case 2:
                    return "Garbage";
                default:
                    return "Obf";
            }
        } else {
            switch (c) {
                case 1:
                    return "Mapped";
                case 2:
                    return "Setter";
                case 3:
                    return "Getter";
                default:
                    return "Obf";
            }
        }
    }

    @Override
    public Object getValueAt(int row,int col) {
        if (current == null) return null;
        int idx = filteredRows.get(row);

        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(idx);
            switch (col) {
                case 0:
                    return mr.node.name + mr.node.desc;
                case 1:
                    return mr.newName;
                case 2:
                    return mr.garbage;
                default:
                    return null;
            }
        } else {
            FieldRecord fr = current.fields.get(idx);
            switch (col) {
                case 0:
                    return fr.node.name + " " + fr.node.desc;
                case 1:
                    return fr.newName;
                case 2:
                    return fr.setter;
                case 3:
                    return fr.getter;
                default:
                    return null;
            }
        }
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return col != 0;
    }

    @SuppressWarnings("unchecked")
    public void setValueAt(Object v,int row,int col) {
        if (current == null || col == 0) return;
        int idx = filteredRows.get(row);

        if (kind == Kind.METHOD) {
            MethodRecord mr = current.methods.get(idx);
            if (col == 1) {                               // mapped name
                mr.newName = (v == null ? null : v.toString());
                String key = mr.node.name + mr.node.desc;
                if (mr.newName == null || mr.newName.isBlank())
                    current.methodMap.remove(key);
                else
                    current.methodMap.put(key, mr.newName);
            } else {                                      // garbage value
                mr.garbage = parseNumber(v);
            }
        } else {
            FieldRecord fr = current.fields.get(idx);
            switch (col) {
                case 1:
                    fr.newName = (v == null ? null : v.toString());
                    String key = fr.node.name + " " + fr.node.desc;
                    if (fr.newName == null || fr.newName.isBlank())
                        current.fieldMap.remove(key);
                    else
                        current.fieldMap.put(key, fr.newName);
                    break;
                case 2:
                    fr.setter = parseNumber(v);
                    break;
                case 3:
                    fr.getter = parseNumber(v);
            }
        }
        fireTableRowsUpdated(row,row);
    }

    private static Number parseNumber(Object v) {
        if (v == null) return null;
        if (v instanceof Number) {
            return (Number) v;
        }
        try { return Long.decode(v.toString()); } catch (NumberFormatException nfe) { return null; }
    }

    public MethodRecord getMethodRecordAt(int viewRow) {
        if (kind != Kind.METHOD || current == null) return null;
        int modelRow = filteredRows.get(viewRow);        // convert via our own mapping
        return current.methods.get(modelRow);
    }
}